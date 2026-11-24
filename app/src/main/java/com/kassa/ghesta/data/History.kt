package com.kassa.ghesta.data

import java.time.LocalDate

/**
 * Payment history: everything the user has actually paid out, merged from installments, debt payments,
 * cheques they issued and one-off expenses. Money received (a cheque or debt owed TO the user) is not included.
 * All of this is pure so it can be tested without Android.
 */
class HistoryEntry(
    val itemId: String,
    val kind: Kind,
    val date: LocalDate,
    val amount: Long,
    /** What the row says, e.g. "برق‌کار" or "وام خودرو". */
    val title: String,
    /** The person or bank paid, if known. */
    val payee: String,
    /** The user's own tag (expenses only). */
    val tag: String,
    /** "قسط ۳ از ۱۲" and the like for non-expense rows. */
    val detail: String,
    /** Where the money went, when that is known for this payment. */
    val destination: PayTarget?,
) {
    /** The bucket used in totals and in the breakdown bar. */
    val bucket: String get() = tag.ifBlank { kindLabel(kind) }
}

fun kindLabel(kind: Kind): String = when (kind) {
    Kind.INST -> "اقساط"
    Kind.DEBT -> "بدهی‌ها"
    Kind.CHEQUE -> "چک‌ها"
    Kind.EXPENSE -> "پرداخت‌ها"
}

class HistoryMonth(val y: Int, val m: Int, val entries: List<HistoryEntry>) {
    val total: Long get() = entries.sumOf { it.amount }

    /** bucket -> amount, largest first. */
    val breakdown: List<Pair<String, Long>>
        get() = entries.groupBy { it.bucket }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
}

object History {
    fun build(items: List<Item>): List<HistoryEntry> {
        val out = ArrayList<HistoryEntry>()
        for (it in items) {
            when (it.kind) {
                Kind.INST -> it.installments.forEachIndexed { i, x ->
                    val paid = x.paidAt ?: return@forEachIndexed
                    out += HistoryEntry(
                        it.id, Kind.INST, paid, x.amount, it.title, it.party, "",
                        "قسط ${(i + 1).fa()} از ${it.installments.size.fa()}", it.targets.singleOrNull(),
                    )
                }
                Kind.DEBT, Kind.CHEQUE -> if (it.dir == Dir.OWE) it.payments.forEach { p ->
                    out += HistoryEntry(
                        it.id, it.kind, p.date, p.amount, it.title, it.party, "",
                        if (it.kind == Kind.CHEQUE) "چک صادره" else "بدهی", it.targets.singleOrNull(),
                    )
                }
                Kind.EXPENSE -> it.payments.forEach { p ->
                    out += HistoryEntry(it.id, Kind.EXPENSE, p.date, p.amount, it.title, it.party, it.tag, "", it.targets.firstOrNull())
                }
            }
        }
        return out.sortedWith(compareByDescending<HistoryEntry> { it.date }.thenBy { it.title })
    }

    /** Newest month first; inside a month newest day first. */
    fun byMonth(entries: List<HistoryEntry>): List<HistoryMonth> =
        entries.groupBy { e -> Jalali.toJalali(e.date).let { it.y * 100 + it.m } }
            .toSortedMap(compareByDescending { it })
            .map { (k, v) -> HistoryMonth(k / 100, k % 100, v) }

    fun filter(entries: List<HistoryEntry>, kind: Kind?, query: String): List<HistoryEntry> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        return entries.filter { e ->
            (kind == null || e.kind == kind) && (tokens.isEmpty() || haystack(e).let { h -> tokens.all { t -> t in h } })
        }
    }

    private fun haystack(e: HistoryEntry): String = normalize(
        listOf(e.title, e.payee, e.tag, e.detail, kindLabel(e.kind), e.destination?.number, e.destination?.holder).filterNotNull().joinToString(" ")
    )

    /** Makes Persian/Arabic spelling and digits comparable: ي→ی, ك→ک, Persian digits → Latin, ZWNJ → space. */
    fun normalize(s: String): String = s.digitsToLatin().lowercase()
        .replace('ي', 'ی').replace('ك', 'ک').replace('ۀ', 'ه').replace('‌', ' ').replace(Regex("\\s+"), " ").trim()

    private fun String.digitsToLatin(): String {
        val sb = StringBuilder(length)
        for (c in this) sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            }
        )
        return sb.toString()
    }

    // ------------------------------------------------------------------ suggestions for the entry form

    class Payee(val name: String, val lastTarget: PayTarget?, val lastTag: String, val count: Int)

    /** People the user has paid before, most recent first, with the card they used last and the tag they gave. */
    fun payees(items: List<Item>): List<Payee> {
        val expenses = items.filter { it.kind == Kind.EXPENSE && it.party.isNotBlank() }
            .sortedByDescending { it.payments.firstOrNull()?.date ?: it.created }
        return expenses.groupBy { normalize(it.party) }.map { (_, list) ->
            val latest = list.first()
            Payee(latest.party, list.firstNotNullOfOrNull { it.targets.firstOrNull() }, list.firstOrNull { it.tag.isNotBlank() }?.tag ?: "", list.size)
        }
    }

    /** Tags in use, most frequent first. */
    fun tags(items: List<Item>): List<String> =
        items.filter { it.kind == Kind.EXPENSE && it.tag.isNotBlank() }.groupBy { it.tag.trim() }
            .entries.sortedByDescending { it.value.size }.map { it.key }

    val defaultTags = listOf("ساختمان", "قبض", "خانه", "ماشین", "خوراک")
}
