package com.kassa.ghesta

import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Reschedule
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.notify.NotifIds
import com.kassa.ghesta.notify.dueKeyOf
import com.kassa.ghesta.notify.planNotifications
import com.kassa.ghesta.notify.splitDueKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RescheduleAndNotifPlanTest {
    private fun j(y: Int, m: Int, d: Int) = Jalali.toGregorian(y, m, d)
    private fun inst(id: String, due: LocalDate, paid: Boolean = false) = Installment(id, due, 1_000_000, if (paid) due else null)
    private fun ymd(d: LocalDate) = Jalali.toJalali(d).let { Triple(it.y, it.m, it.d) }

    // ------------------------------------------------------------------ date shifting

    @Test
    fun addMonthsKeepsDayAndClampsAtMonthEnd() {
        assertEquals(Triple(1405, 7, 30), ymd(Reschedule.addMonths(j(1405, 1, 31), 6)))   // Mehr has 30 days
        assertEquals(Triple(1405, 12, 29), ymd(Reschedule.addMonths(j(1405, 1, 31), 11)))  // Esfand 1405 has 29
        assertEquals(Triple(1406, 1, 31), ymd(Reschedule.addMonths(j(1405, 1, 31), 12)))
        assertEquals(Triple(1403, 12, 30), ymd(Reschedule.addMonths(j(1403, 6, 31), 6)))   // 1403 is a leap year
    }

    @Test
    fun addMonthsGoesBackwardsAcrossYears() {
        assertEquals(Triple(1404, 11, 15), ymd(Reschedule.addMonths(j(1405, 3, 15), -4)))
        assertEquals(Triple(1404, 12, 10), ymd(Reschedule.addMonths(j(1405, 2, 10), -2)))
        assertEquals(Triple(1404, 1, 10), ymd(Reschedule.addMonths(j(1405, 1, 10), -12)))
        assertEquals(j(1405, 5, 5), Reschedule.addMonths(j(1405, 5, 5), 0))
    }

    @Test
    fun shiftingLeavesPaidInstallmentsAlone() {
        val list = listOf(
            inst("a", j(1405, 5, 10), paid = true),
            inst("b", j(1405, 6, 10)),
            inst("c", j(1405, 7, 10)),
        )
        val out = Reschedule.shiftUnpaid(list, 2)
        assertEquals(j(1405, 5, 10), out.first { it.id == "a" }.due)
        assertEquals(Triple(1405, 8, 10), ymd(out.first { it.id == "b" }.due))
        assertEquals(Triple(1405, 9, 10), ymd(out.first { it.id == "c" }.due))
        assertTrue(out.first { it.id == "a" }.paidAt != null)
        assertEquals(listOf("a", "b", "c"), out.map { it.id }) // still in date order
    }

    @Test
    fun shiftingKeepsEachInstallmentsOwnDay() {
        // the second one was edited to a different day earlier; a plain shift must not flatten it
        val list = listOf(inst("a", j(1405, 6, 10)), inst("b", j(1405, 7, 18)))
        val out = Reschedule.shiftUnpaid(list, 1)
        assertEquals(Triple(1405, 7, 10), ymd(out[0].due))
        assertEquals(Triple(1405, 8, 18), ymd(out[1].due))
    }

    @Test
    fun movingTheFirstKeepsMonthSpacingAndTakesTheNewDay() {
        // months 8, 9 and 11 (a gap), all on the 28th
        val list = listOf(inst("a", j(1405, 8, 28)), inst("b", j(1405, 9, 28)), inst("c", j(1405, 11, 28)))
        val out = Reschedule.moveUnpaidTo(list, j(1405, 10, 5))
        assertEquals(Triple(1405, 10, 5), ymd(out[0].due))
        assertEquals(Triple(1405, 11, 5), ymd(out[1].due))
        assertEquals(Triple(1406, 1, 5), ymd(out[2].due)) // the two-month gap is preserved
    }

    @Test
    fun movingToThe31stClampsPerMonth() {
        val list = listOf(inst("a", j(1405, 5, 10)), inst("b", j(1405, 6, 10)), inst("c", j(1405, 7, 10)))
        val out = Reschedule.moveUnpaidTo(list, j(1405, 6, 31))
        assertEquals(Triple(1405, 6, 31), ymd(out[0].due))
        assertEquals(Triple(1405, 7, 30), ymd(out[1].due)) // Mehr has 30 days
        assertEquals(Triple(1405, 8, 30), ymd(out[2].due))
    }

    @Test
    fun movingIgnoresPaidAndHandlesNothingLeft() {
        val allPaid = listOf(inst("a", j(1405, 5, 10), paid = true))
        assertEquals(allPaid, Reschedule.moveUnpaidTo(allPaid, j(1406, 1, 1)))
        val mixed = listOf(inst("p", j(1405, 4, 10), paid = true), inst("u", j(1405, 5, 10)))
        val out = Reschedule.moveUnpaidTo(mixed, j(1405, 9, 20))
        assertEquals(j(1405, 4, 10), out.first { it.id == "p" }.due)
        assertEquals(j(1405, 9, 20), out.first { it.id == "u" }.due)
    }

    @Test
    fun changesListsOnlyWhatMoved() {
        val before = listOf(inst("a", j(1405, 5, 10), paid = true), inst("b", j(1405, 6, 10)))
        val after = Reschedule.shiftUnpaid(before, 1)
        assertEquals(mapOf("b" to j(1405, 7, 10)), Reschedule.changes(before, after))
        assertTrue(Reschedule.changes(before, before).isEmpty())
    }

    @Test
    fun shiftingFromALaterInstallmentLeavesEarlierUnpaidOnesAlone() {
        val list = listOf(inst("a", j(1405, 5, 10)), inst("b", j(1405, 6, 10)), inst("c", j(1405, 7, 10)))
        val out = Reschedule.shift(list, 1, 3)
        assertEquals(j(1405, 5, 10), out[0].due) // before the starting position
        assertEquals(Triple(1405, 9, 10), ymd(out[1].due))
        assertEquals(Triple(1405, 10, 10), ymd(out[2].due))
        assertEquals(listOf(1, 2), Reschedule.affected(list, 1))
        // a list that is no longer in date order is returned in the same order (identity and position preserved)
        assertEquals(listOf("a", "b", "c"), out.map { it.id })
    }

    // ------------------------------------------------------------------ notification plan

    private val today = LocalDate.of(2026, 11, 19)

    private fun loan(title: String, due: LocalDate) = Item(
        "loan-$title", Kind.INST, title, created = today, installments = listOf(inst("i1", due)),
    )

    private fun duesOf(vararg items: Item): List<Due> = items.flatMap { it.dues() }.sortedBy { it.date }

    @Test
    fun oneDueGetsOneNotificationWithPayAndNoSummary() {
        val plan = planNotifications(duesOf(loan("وام خودرو", today.minusDays(2))), today, "تومان", hideDetails = false)
        assertEquals(1, plan.items.size)
        assertFalse(plan.needSummary)
        val s = plan.items.single()
        assertEquals("وام خودرو", s.title)
        assertEquals("پرداخت شد", s.payLabel)
        assertTrue(s.overdue)
        assertTrue(s.text.contains("۲ روز تأخیر"))
        assertEquals("loan-وام خودرو|i1", s.key)
        assertEquals("loan-وام خودرو" to "i1", splitDueKey(s.key))
    }

    @Test
    fun severalDuesNeedASummaryAndCapTheItems() {
        val many = (1..9).map { loan("وام $it", today.minusDays(it.toLong())) }.toTypedArray()
        val plan = planNotifications(duesOf(*many), today, "تومان", hideDetails = false, maxItems = 6)
        assertEquals(6, plan.items.size)
        assertTrue(plan.needSummary)
        assertEquals(9, plan.keys.size) // "snooze all" still covers every due
        assertTrue(plan.summaryTitle.contains("۹ مورد عقب‌افتاده"))
        assertEquals(8, plan.summaryLines.size) // 7 lines + "and N more"
        assertTrue(plan.summaryLines.last().contains("۲ مورد دیگر"))
    }

    @Test
    fun payButtonDependsOnTheKindOfDue() {
        val debt = Item("d", Kind.DEBT, "قرض", created = today, total = 100, due = today.plusDays(1))
        val issued = Item("c1", Kind.CHEQUE, "چک صادره", created = today, dir = Dir.OWE, total = 100, due = today.plusDays(1))
        val received = Item("c2", Kind.CHEQUE, "چک دریافتی", created = today, dir = Dir.OWED, total = 100, due = today.plusDays(1))
        val byTitle = planNotifications(duesOf(debt, issued, received), today, "تومان", false).items.associateBy { it.title }
        assertNull(byTitle["قرض"]!!.payLabel) // a debt can be paid in parts, so no one-tap button
        assertEquals("پاس شد", byTitle["چک صادره"]!!.payLabel)
        assertEquals("وصول شد", byTitle["چک دریافتی"]!!.payLabel)
    }

    @Test
    fun hiddenDetailsRevealOnlyCounts() {
        val plan = planNotifications(duesOf(loan("وام محرمانه", today.minusDays(1)), loan("وام دیگر", today)), today, "تومان", hideDetails = true)
        assertTrue(plan.items.isEmpty()) // no per-item buttons that would reveal or change anything
        assertTrue(plan.generic)
        assertTrue(plan.needSummary)
        assertEquals("۱ مورد عقب‌افتاده و ۱ مورد امروز", plan.summaryTitle)
        assertFalse((plan.summaryTitle + plan.summaryText).contains("وام"))
        assertFalse((plan.summaryTitle + plan.summaryText).contains("تومان"))
    }

    @Test
    fun idsAreStableAndInTheirRanges() {
        val a = NotifIds.item("x|y")
        assertEquals(a, NotifIds.item("x|y"))
        assertTrue(NotifIds.isDueNotification(a))
        assertTrue(NotifIds.isDueNotification(NotifIds.SUMMARY))
        assertFalse(NotifIds.isDueNotification(NotifIds.done("x|y"))) // the "paid" confirmation is never swept away with the digest
        assertEquals(dueKeyOf(duesOf(loan("w", today)).single()), "loan-w|i1")
    }
}
