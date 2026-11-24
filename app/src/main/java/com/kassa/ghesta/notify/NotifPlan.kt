package com.kassa.ghesta.notify

import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.relLabel
import java.time.LocalDate

/** Notification ids. Each due gets a stable id so it can be updated or cancelled on its own. */
object NotifIds {
    const val SUMMARY = 1001
    const val ITEM_BASE = 2000
    const val SPAN = 90_000
    const val DONE_BASE = 100_000

    private fun h(key: String) = (key.hashCode() and 0x7fffffff) % SPAN

    fun item(key: String) = ITEM_BASE + h(key)
    fun done(key: String) = DONE_BASE + h(key)
    fun isDueNotification(id: Int) = id == SUMMARY || id in ITEM_BASE until ITEM_BASE + SPAN
}

/** Identifies one payable thing: the item id and the installment id (or "debt" for a lump sum). */
fun dueKeyOf(d: Due) = "${d.item.id}|${d.key}"

fun splitDueKey(key: String): Pair<String, String> = key.substringBefore('|') to key.substringAfter('|', "")

/** What one notification says and which buttons it gets. */
class NotifSpec(
    val key: String,
    val itemId: String,
    val dueKey: String,
    val title: String,
    val text: String,
    val overdue: Boolean,
    /** Label of the "it is paid" button, or null when this kind of due has none (a debt can be paid in parts). */
    val payLabel: String?,
) {
    val id: Int get() = NotifIds.item(key)
}

class NotifPlan(
    /** One notification per due (at most the first few). Empty when details are hidden. */
    val items: List<NotifSpec>,
    val summaryTitle: String,
    val summaryText: String,
    val summaryLines: List<String>,
    /** True when a grouped summary notification is needed (several dues, or details hidden). */
    val needSummary: Boolean,
    /** True when the summary is the only notification and shows counts only. */
    val generic: Boolean,
    /** Keys of every due covered by the plan, including those beyond the shown items (for "snooze all"). */
    val keys: List<String>,
)

fun payLabelFor(d: Due): String? = when (d.item.kind) {
    Kind.INST -> "پرداخت شد"
    Kind.CHEQUE -> if (d.item.dir == Dir.OWE) "پاس شد" else "وصول شد"
    Kind.DEBT, Kind.EXPENSE -> null
}

/**
 * Pure planning of the reminder notifications, so it can be tested without Android.
 * With [hideDetails] (the app lock is on) nothing but counts is revealed and there are no per-item buttons.
 */
fun planNotifications(
    dues: List<Due>,
    today: LocalDate,
    unit: String,
    hideDetails: Boolean,
    maxItems: Int = 6,
): NotifPlan {
    val overdueN = dues.count { it.date.isBefore(today) }
    val todayN = dues.count { it.date == today }
    val soonN = dues.size - overdueN - todayN
    val title = when {
        dues.isEmpty() -> "یادآوری‌ها فعال است"
        overdueN > 0 -> "${overdueN.fa()} مورد عقب‌افتاده" + if (todayN > 0) " و ${todayN.fa()} مورد امروز" else ""
        todayN > 0 -> "${todayN.fa()} پرداخت امروز دارید"
        else -> "${soonN.fa()} پرداخت نزدیک دارید"
    }
    val keys = dues.map { dueKeyOf(it) }

    if (hideDetails) {
        return NotifPlan(
            items = emptyList(), summaryTitle = title, summaryText = "برای دیدن جزئیات، برنامه را باز کنید",
            summaryLines = emptyList(), needSummary = dues.isNotEmpty(), generic = true, keys = keys,
        )
    }

    fun line(d: Due) = "${d.item.title} — ${d.label} — ${fmtNum(d.amount)} — ${relLabel(daysBetween(today, d.date))}"
    val items = dues.take(maxItems).map { d ->
        NotifSpec(
            key = dueKeyOf(d), itemId = d.item.id, dueKey = d.key,
            title = d.item.title,
            text = "${d.label} — ${fmtNum(d.amount)} $unit — ${relLabel(daysBetween(today, d.date))}",
            overdue = d.date.isBefore(today),
            payLabel = payLabelFor(d),
        )
    }
    val lines = dues.take(7).map { line(it) } +
        (if (dues.size > 7) listOf("و ${(dues.size - 7).fa()} مورد دیگر…") else emptyList())
    return NotifPlan(
        items = items, summaryTitle = title, summaryText = "مجموع: ${fmtNum(dues.sumOf { it.amount })} $unit",
        summaryLines = lines, needSummary = dues.size > 1, generic = false, keys = keys,
    )
}
