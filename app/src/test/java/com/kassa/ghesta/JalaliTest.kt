package com.kassa.ghesta

import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.amountWords
import com.kassa.ghesta.data.digitsOnly
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.relLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JalaliTest {
    /** Reference values produced by the JS engine's built-in Persian calendar (Intl). */
    private val samples = listOf(
        "2026-03-21" to Triple(1405, 1, 1),
        "2026-03-20" to Triple(1404, 12, 29),
        "2025-03-20" to Triple(1403, 12, 30),
        "2025-03-21" to Triple(1404, 1, 1),
        "2024-03-19" to Triple(1402, 12, 29),
        "2024-03-20" to Triple(1403, 1, 1),
        "2026-11-11" to Triple(1405, 8, 20),
        "2030-03-20" to Triple(1408, 12, 30),
        "2031-03-21" to Triple(1410, 1, 1),
        "2049-03-20" to Triple(1428, 1, 1),
        "2050-03-21" to Triple(1429, 1, 1),
        "2037-08-02" to Triple(1416, 5, 12),
        "2014-10-24" to Triple(1393, 8, 2),
        "2036-02-10" to Triple(1414, 11, 21),
        "2059-09-25" to Triple(1438, 7, 3),
        "2039-03-02" to Triple(1417, 12, 11),
        "2038-10-02" to Triple(1417, 7, 10),
        "2006-03-24" to Triple(1385, 1, 4),
        "2052-05-28" to Triple(1431, 3, 8),
        "2040-12-21" to Triple(1419, 10, 1),
        "2048-10-15" to Triple(1427, 7, 24),
        "2043-01-23" to Triple(1421, 11, 3),
        "2016-12-14" to Triple(1395, 9, 24),
        "2046-08-08" to Triple(1425, 5, 17),
        "2007-06-30" to Triple(1386, 4, 9),
        "2032-09-09" to Triple(1411, 6, 19),
        "2010-03-12" to Triple(1388, 12, 21),
        "2008-07-31" to Triple(1387, 5, 10),
        "2046-03-11" to Triple(1424, 12, 21),
        "2051-08-20" to Triple(1430, 5, 29),
        "2049-11-10" to Triple(1428, 8, 20),
        "2002-03-28" to Triple(1381, 1, 8),
        "2025-07-29" to Triple(1404, 5, 7),
        "2042-07-15" to Triple(1421, 4, 24),
        "2018-10-16" to Triple(1397, 7, 24),
        "2013-02-26" to Triple(1391, 12, 8),
        "2018-12-08" to Triple(1397, 9, 17),
        "2054-09-19" to Triple(1433, 6, 28),
        "2051-08-07" to Triple(1430, 5, 16),
        "2022-04-02" to Triple(1401, 1, 13),
        "2022-11-11" to Triple(1401, 8, 20),
        "2048-04-12" to Triple(1427, 1, 24),
        "2005-04-16" to Triple(1384, 1, 27),
        "2015-08-30" to Triple(1394, 6, 8),
        "2019-07-03" to Triple(1398, 4, 12),
        "2027-10-17" to Triple(1406, 7, 25),
        "2058-03-30" to Triple(1437, 1, 10),
        "2025-05-22" to Triple(1404, 3, 1),
        "2043-12-24" to Triple(1422, 10, 3),
        "2009-08-23" to Triple(1388, 6, 1),
        "2023-05-05" to Triple(1402, 2, 15),
        "2051-10-10" to Triple(1430, 7, 18),
        "2048-03-25" to Triple(1427, 1, 6),
        "2015-01-05" to Triple(1393, 10, 15),
        "2029-10-29" to Triple(1408, 8, 8),
        "2050-01-10" to Triple(1428, 10, 21),
    )

    @Test
    fun matchesReferenceCalendar() {
        for ((iso, exp) in samples) {
            val g = LocalDate.parse(iso)
            val j = Jalali.toJalali(g)
            assertEquals("to jalali $iso", exp, Triple(j.y, j.m, j.d))
            assertEquals("to gregorian $iso", g, Jalali.toGregorian(exp.first, exp.second, exp.third))
        }
    }

    @Test
    fun everyDayRoundTripsAndAdvancesByOne() {
        var d = Jalali.toGregorian(1380, 1, 1)
        val end = Jalali.toGregorian(1450, 1, 1)
        var prev = Jalali.toJalali(d)
        assertEquals(Triple(1380, 1, 1), Triple(prev.y, prev.m, prev.d))
        d = d.plusDays(1)
        while (d.isBefore(end)) {
            val j = Jalali.toJalali(d)
            assertEquals(d, Jalali.toGregorian(j.y, j.m, j.d))
            val sameMonth = j.y == prev.y && j.m == prev.m
            if (sameMonth) assertEquals(prev.d + 1, j.d)
            else {
                assertEquals(1, j.d)
                assertEquals(Jalali.monthLength(prev.y, prev.m), prev.d)
            }
            prev = j
            d = d.plusDays(1)
        }
    }

    @Test
    fun leapYears() {
        assertTrue(Jalali.isLeap(1403))
        assertTrue(Jalali.isLeap(1408))
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test
    fun scheduleKeepsDayOfMonthAndClampsAtMonthEnd() {
        // 31 Farvardin 1405, monthly: Ordibehesht..Shahrivar have 31 days, Mehr..Bahman 30, Esfand 29 (1405 is not leap)
        val first = Jalali.toGregorian(1405, 1, 31)
        val dates = Jalali.schedule(first, 13, 1).map { Jalali.toJalali(it) }
        assertEquals(Triple(1405, 1, 31), Triple(dates[0].y, dates[0].m, dates[0].d))
        assertEquals(Triple(1405, 6, 31), Triple(dates[5].y, dates[5].m, dates[5].d))
        assertEquals(Triple(1405, 7, 30), Triple(dates[6].y, dates[6].m, dates[6].d))
        assertEquals(Triple(1405, 12, 29), Triple(dates[11].y, dates[11].m, dates[11].d))
        assertEquals(Triple(1406, 1, 31), Triple(dates[12].y, dates[12].m, dates[12].d))
    }

    @Test
    fun formatting() {
        assertEquals("۲۰ آبان ۱۴۰۵", fmtDate(LocalDate.of(2026, 11, 11)))
        assertEquals("چهارشنبه ۲۰ آبان", fmtDate(LocalDate.of(2026, 11, 11), weekday = true, year = false))
        assertEquals("۱٬۲۳۴٬۵۶۷", fmtNum(1234567))
        assertEquals("123", "۱۲۳abc".digitsOnly())
        assertEquals("۲ میلیون و ۵۰۰ هزار تومان", amountWords(2_500_000, "تومان"))
        assertEquals("امروز", relLabel(0))
        assertEquals("۳ روز تأخیر", relLabel(-3))
    }

    @Test
    fun itemStates() {
        val today = LocalDate.of(2026, 11, 11)
        fun inst(due: LocalDate, paid: Boolean) = Installment(due.toString(), due, 100, if (paid) due else null)
        fun item(vararg i: Installment) = Item("x", Kind.INST, "t", created = today, installments = i.toList())

        assertEquals(ItemState.OVERDUE, item(inst(today.minusDays(2), false), inst(today.plusDays(30), false)).info(today).state)
        assertEquals(ItemState.SOON, item(inst(today.minusDays(2), true), inst(today.plusDays(3), false)).info(today).state)
        assertEquals(ItemState.OK, item(inst(today.plusDays(20), false)).info(today).state)
        assertEquals(ItemState.DONE, item(inst(today.minusDays(2), true)).info(today).state)

        val debt = Item("d", Kind.DEBT, "قرض", created = today, dir = Dir.OWE, total = 1000, due = today.minusDays(1))
        assertEquals(ItemState.OVERDUE, debt.info(today).state)
        assertEquals(1000L, debt.info(today).remaining)
    }
}
