package com.kassa.ghesta

import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Reschedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RescheduleTest {
    private fun j(y: Int, m: Int, d: Int): LocalDate = Jalali.toGregorian(y, m, d)
    private fun inst(id: String, due: LocalDate, paid: Boolean = false) = Installment(id, due, 1_000_000, if (paid) due else null)
    private fun ymd(d: LocalDate) = Jalali.toJalali(d).let { Triple(it.y, it.m, it.d) }

    private val monthly = listOf(
        inst("a", j(1405, 1, 25), paid = true),
        inst("b", j(1405, 2, 25)),
        inst("c", j(1405, 3, 25)),
        inst("d", j(1405, 4, 25)),
    )

    @Test
    fun shiftMovesOnlyUnpaidAndKeepsTheDay() {
        val out = Reschedule.shift(monthly, 0, 2)
        assertEquals(Triple(1405, 1, 25), ymd(out[0].due)) // paid: untouched
        assertEquals(Triple(1405, 4, 25), ymd(out[1].due))
        assertEquals(Triple(1405, 5, 25), ymd(out[2].due))
        assertEquals(Triple(1405, 6, 25), ymd(out[3].due))
        assertEquals(monthly.map { it.id }, out.map { it.id }) // identity and order preserved
        assertEquals(monthly[0].paidAt, out[0].paidAt)
    }

    @Test
    fun shiftFromAGivenInstallmentLeavesEarlierOnesAlone() {
        val out = Reschedule.shift(monthly, fromIndex = 2, months = 1)
        assertEquals(Triple(1405, 2, 25), ymd(out[1].due))
        assertEquals(Triple(1405, 4, 25), ymd(out[2].due))
        assertEquals(Triple(1405, 5, 25), ymd(out[3].due))
    }

    @Test
    fun shiftingBackwardsCrossesTheYearAndClampsToMonthLength() {
        val out = Reschedule.shift(listOf(inst("x", j(1405, 2, 15))), 0, -3)
        assertEquals(Triple(1404, 11, 15), ymd(out.single().due))
        // 31 Shahrivar + 1 month = 30 Mehr (Mehr has 30 days)
        assertEquals(Triple(1405, 7, 30), ymd(Reschedule.shift(listOf(inst("y", j(1405, 6, 31))), 0, 1).single().due))
        // 30 Bahman + 1 month = 29 Esfand in a non-leap year (1405)
        assertEquals(Triple(1405, 12, 29), ymd(Reschedule.shift(listOf(inst("z", j(1405, 11, 30))), 0, 1).single().due))
    }

    @Test
    fun zeroShiftIsANoOp() {
        assertEquals(monthly, Reschedule.shift(monthly, 0, 0))
    }

    @Test
    fun rebaseKeepsSpacingAndTakesTheNewDay() {
        val out = Reschedule.rebase(monthly, 1, j(1405, 3, 10))
        assertEquals(Triple(1405, 1, 25), ymd(out[0].due)) // paid
        assertEquals(Triple(1405, 3, 10), ymd(out[1].due))
        assertEquals(Triple(1405, 4, 10), ymd(out[2].due))
        assertEquals(Triple(1405, 5, 10), ymd(out[3].due))
    }

    @Test
    fun rebasePreservesGapsBetweenInstallments() {
        val everyTwoMonths = listOf(inst("a", j(1405, 3, 25)), inst("b", j(1405, 5, 25)), inst("c", j(1405, 7, 25)))
        val out = Reschedule.rebase(everyTwoMonths, 0, j(1405, 4, 10))
        assertEquals(listOf(Triple(1405, 4, 10), Triple(1405, 6, 10), Triple(1405, 8, 10)), out.map { ymd(it.due) })
    }

    @Test
    fun rebaseOntoThe31stClampsInShorterMonths() {
        val out = Reschedule.rebase(monthly.drop(1), 0, j(1405, 6, 31))
        assertEquals(listOf(Triple(1405, 6, 31), Triple(1405, 7, 30), Triple(1405, 8, 30)), out.map { ymd(it.due) })
    }

    @Test
    fun affectedIgnoresPaidAndEarlierPositions() {
        assertEquals(listOf(1, 2, 3), Reschedule.affected(monthly, 0))
        assertEquals(listOf(2, 3), Reschedule.affected(monthly, 2))
        assertTrue(Reschedule.affected(monthly.map { it.copy(paidAt = it.due) }, 0).isEmpty())
        // rebase with nothing to move changes nothing
        val allPaid = monthly.map { it.copy(paidAt = it.due) }
        assertEquals(allPaid, Reschedule.rebase(allPaid, 0, j(1406, 1, 1)))
    }
}
