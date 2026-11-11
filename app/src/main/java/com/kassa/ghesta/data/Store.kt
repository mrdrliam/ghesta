package com.kassa.ghesta.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kassa.ghesta.notify.Reminders
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** One item as received from the server. */
class RemoteChange(val id: String, val updatedAt: Long, val deleted: Boolean, val data: JSONObject?)

/** What we are about to push, plus the edit stamps so we can tell later whether the item changed meanwhile. */
class SyncBatch(val changes: JSONArray, val sent: Map<String, Long>)

/**
 * Single source of truth. Compose-observable state, persisted as one small JSON file.
 * (Hundreds of installments are a few KB, so a file beats pulling in a database.)
 *
 * Sync bookkeeping lives here too: every local change marks the item "pending", deletions leave a
 * tombstone, and [syncCursor] remembers how far we have read from the server.
 */
object Store {
    var items by mutableStateOf<List<Item>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set
    var today by mutableStateOf(LocalDate.now())
        private set

    /** Bumped when the app returns to the foreground so permission-dependent UI refreshes. */
    var resumeTick by mutableIntStateOf(0)
        private set

    /** Number of local changes not yet confirmed by the server. */
    var pendingCount by mutableIntStateOf(0)
        private set

    var syncCursor: Long = 0
        private set
    private val pending = LinkedHashSet<String>()
    private val tombstones = HashMap<String, Long>()

    private var file: File? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, "ghesta.json")
        load()
    }

    fun onResume() {
        today = LocalDate.now()
        resumeTick++
    }

    fun item(id: String?): Item? = if (id == null) null else items.firstOrNull { it.id == id }

    private fun newId() = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- actions

    fun addInstallments(
        title: String, party: String, note: String,
        amount: Long, count: Int, stepMonths: Int,
        first: LocalDate, paidCount: Int, lastAmount: Long = amount,
    ): String {
        val dates = Jalali.schedule(first, count, stepMonths)
        val list = dates.mapIndexed { i, d ->
            Installment(newId(), d, if (i == count - 1) lastAmount else amount, if (i < paidCount) d else null)
        }
        val item = Item(newId(), Kind.INST, title.trim(), party.trim(), note.trim(), today, installments = list)
        mutate { it + item }
        return item.id
    }

    fun addDebt(
        title: String, party: String, note: String,
        dir: Dir, total: Long, paidSoFar: Long, due: LocalDate?,
    ): String {
        val payments = if (paidSoFar > 0) listOf(Payment(newId(), today, paidSoFar.coerceAtMost(total))) else emptyList()
        val item = Item(newId(), Kind.DEBT, title.trim(), party.trim(), note.trim(), today, dir = dir, total = total, due = due, payments = payments)
        mutate { it + item }
        return item.id
    }

    fun updateItem(id: String, transform: (Item) -> Item) = mutate { list -> list.map { if (it.id == id) transform(it) else it } }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun setPaid(itemId: String, instId: String, paid: Boolean, date: LocalDate = today) = updateItem(itemId) { item ->
        item.copy(installments = item.installments.map { if (it.id == instId) it.copy(paidAt = if (paid) date else null) else it })
    }

    fun updateInstallment(itemId: String, instId: String, due: LocalDate, amount: Long, paidAt: LocalDate?) = updateItem(itemId) { item ->
        item.copy(installments = item.installments.map { if (it.id == instId) it.copy(due = due, amount = amount, paidAt = paidAt) else it }.sortedBy { it.due })
    }

    fun deleteInstallment(itemId: String, instId: String) = updateItem(itemId) { item ->
        item.copy(installments = item.installments.filterNot { it.id == instId })
    }

    /** Changes the amount of every unpaid installment. */
    fun setUnpaidAmount(itemId: String, amount: Long) = updateItem(itemId) { item ->
        item.copy(installments = item.installments.map { if (it.paidAt == null) it.copy(amount = amount) else it })
    }

    fun addPayment(itemId: String, amount: Long, date: LocalDate): String {
        val pid = newId()
        updateItem(itemId) { it.copy(payments = it.payments + Payment(pid, date, amount)) }
        return pid
    }

    fun removePayment(itemId: String, paymentId: String) = updateItem(itemId) { item ->
        item.copy(payments = item.payments.filterNot { it.id == paymentId })
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        settings = transform(settings)
        save()
        appContext?.let { Reminders.schedule(it) }
    }

    /** Deletes everything (and, if signed in, it is deleted from the account on the next sync). */
    fun clearAll() = mutate { emptyList() }

    // ---------------------------------------------------------------- change tracking

    /** Applies [transform], stamps every changed item, records tombstones for removed ones, saves, requests a sync. */
    private fun mutate(transform: (List<Item>) -> List<Item>) {
        val old = items.associateBy { it.id }
        val now = System.currentTimeMillis()
        val next = transform(items)
        val nextIds = next.map { it.id }.toSet()
        val stamped = next.map { n ->
            val o = old[n.id]
            if (o == null || o != n) {
                pending.add(n.id)
                tombstones.remove(n.id)
                n.copy(updatedAt = maxOf(now, (o?.updatedAt ?: 0L) + 1))
            } else n
        }
        for (o in old.values) {
            if (o.id !in nextIds) {
                tombstones[o.id] = maxOf(now, o.updatedAt + 1)
                pending.add(o.id)
            }
        }
        items = stamped
        pendingCount = pending.size
        save()
        Sync.requestSync()
    }

    // ---------------------------------------------------------------- sync support

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun buildBatch(limit: Int = 500): SyncBatch {
        val arr = JSONArray()
        val sent = HashMap<String, Long>()
        val byId = items.associateBy { it.id }
        val dead = ArrayList<String>()
        for (id in pending) {
            if (arr.length() >= limit) break
            val item = byId[id]
            val tomb = tombstones[id]
            when {
                item != null -> {
                    arr.put(JSONObject().put("id", id).put("updated_at", item.updatedAt).put("deleted", false).put("data", itemToJson(item)))
                    sent[id] = item.updatedAt
                }
                tomb != null -> {
                    arr.put(JSONObject().put("id", id).put("updated_at", tomb).put("deleted", true))
                    sent[id] = tomb
                }
                else -> dead.add(id)
            }
        }
        pending.removeAll(dead.toSet())
        return SyncBatch(arr, sent)
    }

    /** Merges the server's answer. Newer-wins per item; a local edit made during the request stays pending. */
    fun applySync(batch: SyncBatch, accepted: Set<String>, remote: List<RemoteChange>, cursor: Long) {
        val map = LinkedHashMap<String, Item>()
        items.forEach { map[it.id] = it }
        for (c in remote) {
            val local = map[c.id]
            val localStamp = local?.updatedAt ?: tombstones[c.id]
            if (c.id in pending && localStamp != null && localStamp > c.updatedAt) continue // our edit is newer; it will be pushed
            if (c.deleted) {
                map.remove(c.id)
            } else {
                val parsed = try {
                    itemFromJson(c.data ?: continue).copy(updatedAt = c.updatedAt)
                } catch (_: Exception) {
                    continue
                }
                map[c.id] = parsed
            }
            pending.remove(c.id)
            tombstones.remove(c.id)
        }
        for (id in accepted) {
            val stamp = map[id]?.updatedAt ?: tombstones[id]
            if (stamp == batch.sent[id]) {
                pending.remove(id)
                tombstones.remove(id)
            }
        }
        items = map.values.toList()
        syncCursor = cursor
        pendingCount = pending.size
        save()
    }

    /** After signing in with data already on the phone: upload all of it (merge by newest edit). */
    fun markAllPending() {
        tombstones.clear()
        items.forEach { pending.add(it.id) }
        pendingCount = pending.size
        save()
    }

    /** After signing in and choosing to keep only the account's data. */
    fun discardLocalData() {
        items = emptyList()
        pending.clear()
        tombstones.clear()
        syncCursor = 0
        pendingCount = 0
        save()
    }

    /** On sign-out: the data stays on the phone, but it no longer belongs to any account. */
    fun resetSyncState() {
        pending.clear()
        tombstones.clear()
        syncCursor = 0
        pendingCount = 0
        save()
    }

    // ---------------------------------------------------------------- persistence

    private fun save() {
        val f = file ?: return
        try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(toJson(withSyncState = true))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            val (parsed, s) = parseBackup(root)
            items = parsed
            if (s != null) settings = s
            root.optJSONObject("sync")?.let { sy ->
                syncCursor = sy.optLong("cursor", 0)
                sy.optJSONArray("pending")?.let { a -> for (i in 0 until a.length()) pending.add(a.getString(i)) }
                sy.optJSONObject("tombstones")?.let { t -> t.keys().forEach { k -> tombstones[k] = t.getLong(k) } }
            }
            pendingCount = pending.size
        } catch (_: Exception) {
        }
    }

    private fun toJson(withSyncState: Boolean): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("settings", JSONObject().apply {
            put("theme", settings.theme)
            put("notify", settings.notify)
            put("remindHour", settings.remindHour)
            put("remindDays", JSONArray(settings.remindDays.sorted()))
        })
        root.put("items", JSONArray().also { arr -> items.forEach { arr.put(itemToJson(it)) } })
        if (withSyncState) {
            root.put("sync", JSONObject().apply {
                put("cursor", syncCursor)
                put("pending", JSONArray(pending.toList()))
                put("tombstones", JSONObject().also { t -> tombstones.forEach { (k, v) -> t.put(k, v) } })
            })
        }
        return root.toString(1)
    }

    /** Backup file contents: items and settings only, no sync bookkeeping. */
    fun exportJson(): String = toJson(withSyncState = false)

    /** Replaces all data with the given backup. Returns false (and changes nothing) if it is not valid. */
    fun importJson(text: String): Boolean = try {
        val (parsed, s) = parseBackup(JSONObject(text))
        if (s != null) {
            settings = s
            appContext?.let { Reminders.schedule(it) }
        }
        mutate { parsed } // goes through change tracking, so a signed-in account is updated too
        true
    } catch (_: Exception) {
        false
    }

    private fun parseBackup(root: JSONObject): Pair<List<Item>, Settings?> {
        val arr = root.getJSONArray("items")
        val parsed = (0 until arr.length()).map { itemFromJson(arr.getJSONObject(it)) }
        val s = root.optJSONObject("settings")
        val base = Settings()
        val newSettings = if (s == null) null else Settings(
            theme = s.optInt("theme", base.theme),
            notify = s.optBoolean("notify", base.notify),
            remindHour = s.optInt("remindHour", base.remindHour).coerceIn(0, 23),
            remindDays = s.optJSONArray("remindDays")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: base.remindDays,
        )
        return parsed to newSettings
    }

    fun itemToJson(item: Item): JSONObject = JSONObject().apply {
        item.run {
            put("id", id); put("kind", kind.name); put("title", title); put("party", party); put("note", note)
            put("created", created.toString())
            put("dir", dir.name); put("total", total)
            put("due", due?.toString() ?: JSONObject.NULL)
            put("updatedAt", updatedAt)
            put("installments", JSONArray().also { a ->
                installments.forEach {
                    a.put(JSONObject().put("id", it.id).put("due", it.due.toString()).put("amount", it.amount)
                        .put("paidAt", it.paidAt?.toString() ?: JSONObject.NULL))
                }
            })
            put("payments", JSONArray().also { a ->
                payments.forEach { a.put(JSONObject().put("id", it.id).put("date", it.date.toString()).put("amount", it.amount)) }
            })
        }
    }

    fun itemFromJson(o: JSONObject): Item {
        val inst = o.optJSONArray("installments") ?: JSONArray()
        val pay = o.optJSONArray("payments") ?: JSONArray()
        return Item(
            id = o.getString("id"),
            kind = Kind.valueOf(o.getString("kind")),
            title = o.getString("title"),
            party = o.optString("party", ""),
            note = o.optString("note", ""),
            created = LocalDate.parse(o.getString("created")),
            installments = (0 until inst.length()).map {
                val x = inst.getJSONObject(it)
                Installment(
                    x.getString("id"), LocalDate.parse(x.getString("due")), x.getLong("amount"),
                    if (x.isNull("paidAt")) null else LocalDate.parse(x.getString("paidAt")),
                )
            },
            dir = Dir.valueOf(o.optString("dir", "OWE")),
            total = o.optLong("total", 0),
            due = if (o.isNull("due")) null else LocalDate.parse(o.getString("due")),
            payments = (0 until pay.length()).map {
                val x = pay.getJSONObject(it)
                Payment(x.getString("id"), LocalDate.parse(x.getString("date")), x.getLong("amount"))
            },
            updatedAt = o.optLong("updatedAt", 0),
        )
    }
}
