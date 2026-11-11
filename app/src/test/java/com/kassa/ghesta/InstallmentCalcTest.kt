package com.kassa.ghesta

import com.kassa.ghesta.data.CalcMode
import com.kassa.ghesta.data.InstallmentCalc
import com.kassa.ghesta.data.Jalali
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallmentCalcTest {
    private fun j(y: Int, m: Int, d: Int) = Jalali.toGregorian(y, m, d)
    private val first = j(1405, 1, 15)

    @Test
    fun amountAndCountGiveEndDateAndTotal() {
        val r = InstallmentCalc.compute(CalcMode.AMOUNT_COUNT, first, 1, 1_000_000, 12, 0, null, null).result!!
        assertEquals(12, r.count)
        assertEquals(12_000_000L, r.total)
        assertEquals(j(1405, 12, 15), r.last)
    }

    @Test
    fun amountAndEndDateGiveCount() {
        val r = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 1, 1_000_000, 0, 0, j(1405, 12, 20), null).result!!
        assertEquals(12, r.count)
        // the end date itself counts when an installment falls on it
        val r2 = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 1, 1_000_000, 0, 0, j(1405, 12, 15), null).result!!
        assertEquals(12, r2.count)
        val r3 = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 1, 1_000_000, 0, 0, j(1405, 12, 14), null).result!!
        assertEquals(11, r3.count)
        val same = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 1, 1_000_000, 0, 0, first, null).result!!
        assertEquals(1, same.count)
    }

    @Test
    fun endBeforeStartIsAnError() {
        val c = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 1, 1_000_000, 0, 0, j(1405, 1, 14), null)
        assertNull(c.result)
        assertNotNull(c.error)
    }

    @Test
    fun incompleteInputsGiveNoResultAndNoError() {
        val c = InstallmentCalc.compute(CalcMode.TOTAL_COUNT, first, 1, 0, 0, 0, null, null)
        assertNull(c.result)
        assertNull(c.error)
    }

    @Test
    fun totalAndCountSplitKeepsExactTotal() {
        val r = InstallmentCalc.compute(CalcMode.TOTAL_COUNT, first, 1, 0, 12, 100_000_000, null, null).result!!
        assertEquals(100_000_000L, r.total)
        assertEquals(8_333_000L, r.amount)
        assertEquals(8_337_000L, r.lastAmount)
    }

    @Test
    fun totalAndAmountGiveCountWithSmallerLastInstallment() {
        val r = InstallmentCalc.compute(CalcMode.TOTAL_AMOUNT, first, 1, 3_000_000, 0, 10_000_000, null, null).result!!
        assertEquals(4, r.count)
        assertEquals(1_000_000L, r.lastAmount)
        assertEquals(10_000_000L, r.total)
        val exact = InstallmentCalc.compute(CalcMode.TOTAL_AMOUNT, first, 1, 2_500_000, 0, 10_000_000, null, null).result!!
        assertEquals(4, exact.count)
        assertEquals(2_500_000L, exact.lastAmount)
    }

    @Test
    fun totalAndEndDateGiveCountAndAmount() {
        val f = j(1405, 1, 1)
        val r = InstallmentCalc.compute(CalcMode.TOTAL_END, f, 1, 0, 0, 12_000_000, j(1405, 6, 31), null).result!!
        assertEquals(6, r.count)
        assertEquals(2_000_000L, r.amount)
        assertEquals(12_000_000L, r.total)
    }

    @Test
    fun stepOfThreeMonths() {
        val r = InstallmentCalc.compute(CalcMode.AMOUNT_END, first, 3, 5_000_000, 0, 0, j(1406, 1, 15), null).result!!
        assertEquals(5, r.count) // 1405/01, 04, 07, 10, 1406/01
    }

    @Test
    fun loanWithInterest() {
        val r = InstallmentCalc.compute(CalcMode.LOAN, first, 1, 0, 12, 100_000_000, null, 18.0).result!!
        assertTrue("payment ${r.amount}", r.amount in 9_167_000..9_169_000)
        assertTrue(r.interest in 9_900_000..10_100_000)
        val zero = InstallmentCalc.compute(CalcMode.LOAN, first, 1, 0, 10, 100_000_000, null, 0.0).result!!
        assertEquals(10_000_000L, zero.amount)
        assertEquals(0L, zero.interest)
    }

    @Test
    fun tooManyInstallmentsIsAnError() {
        val c = InstallmentCalc.compute(CalcMode.TOTAL_AMOUNT, first, 1, 1_000, 0, 1_000_000_000, null, null)
        assertNull(c.result)
        assertNotNull(c.error)
    }
}
