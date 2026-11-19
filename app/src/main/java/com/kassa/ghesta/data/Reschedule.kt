package com.kassa.ghesta.data

import java.time.LocalDate

/**
 * Moving the remaining installments of a loan in time (the bank postponed them, or the due day changed).
 * Everything works in Jalali months, and a day that does not exist in the target month
 * (31st of Mehr, 30th of Esfand in a normal year) is clamped to the month's last day.
 *
 * Paid installments are never touched. The functions keep the list's order and the installments' identity;
 * "fromIndex" is the position in the list (0-based) of the first installment that may move, so a postponement
 * can start at a later installment and leave earlier unpaid ones alone.
 */
object Reschedule {
    private fun monthIndex(j: JDate) = j.y * 12 + (j.m - 1)

    private fun fromIndex(index: Int, day: Int): LocalDate {
        val y = Math.floorDiv(index, 12)
        val m = Math.floorMod(index, 12) + 1
        return Jalali.toGregorian(y, m, minOf(day, Jalali.monthLength(y, m)))
    }

    /** [d] moved by [months] Jalali months (negative = earlier), keeping its day of month where possible. */
    fun addMonths(d: LocalDate, months: Int): LocalDate {
        val j = Jalali.toJalali(d)
        return fromIndex(monthIndex(j) + months, j.d)
    }

    private fun movable(list: List<Installment>, from: Int) =
        list.indices.filter { it >= from && list[it].paidAt == null }

    /** Positions of the installments that a move starting at [fromIndex] would touch (unpaid ones only). */
    fun affected(list: List<Installment>, fromIndex: Int): List<Int> = movable(list, fromIndex)

    /** Every unpaid installment from [fromIndex] on moves by [months] months, each keeping its own day. */
    fun shift(list: List<Installment>, fromIndex: Int, months: Int): List<Installment> {
        if (months == 0) return list
        val touched = movable(list, fromIndex).toSet()
        return list.mapIndexed { i, x -> if (i in touched) x.copy(due = addMonths(x.due, months)) else x }
    }

    /**
     * The first unpaid installment at or after [fromIndex] moves to [newFirst]; the later ones keep the same
     * spacing in months (so a schedule that skips months still skips them) and take [newFirst]'s day of month.
     */
    fun rebase(list: List<Installment>, fromIndex: Int, newFirst: LocalDate): List<Installment> {
        val touched = movable(list, fromIndex)
        val first = touched.minByOrNull { list[it].due } ?: return list
        val base = monthIndex(Jalali.toJalali(list[first].due))
        val target = Jalali.toJalali(newFirst)
        val targetBase = monthIndex(target)
        val set = touched.toSet()
        return list.mapIndexed { i, x ->
            if (i in set) x.copy(due = fromIndex(targetBase + (monthIndex(Jalali.toJalali(x.due)) - base), target.d)) else x
        }
    }

    /** id -> new due date, only for the installments that actually change. */
    fun changes(before: List<Installment>, after: List<Installment>): Map<String, LocalDate> {
        val old = before.associate { it.id to it.due }
        return after.filter { old[it.id] != it.due }.associate { it.id to it.due }
    }

    // Whole-list conveniences (everything unpaid), kept in date order.
    fun shiftUnpaid(list: List<Installment>, months: Int): List<Installment> = shift(list, 0, months).sortedBy { it.due }
    fun moveUnpaidTo(list: List<Installment>, newFirst: LocalDate): List<Installment> = rebase(list, 0, newFirst).sortedBy { it.due }
}
