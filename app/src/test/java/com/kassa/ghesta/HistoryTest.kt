package com.kassa.ghesta

import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.History
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.PayTarget
import com.kassa.ghesta.data.Payment
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.TargetType
import com.kassa.ghesta.data.computeStats
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.isObligation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HistoryTest {
    private fun j(y: Int, m: Int, d: Int) = Jalali.toGregorian(y, m, d)
    private val today = j(1405, 8, 28)

    private val card = PayTarget("t1", TargetType.CARD, "6037991234567893", "علی رضایی", 1_500_000)

    private fun expense(
        id: String, title: String, payee: String, amount: Long, date: LocalDate, tag: String = "", target: PayTarget? = null,
    ) = Item(
        id, Kind.EXPENSE, title, payee, created = date, dir = Dir.OWE, total = amount, tag = tag,
        payments = listOf(Payment("p-$id", date, amount)), targets = listOfNotNull(target),
    )

    private val loan = Item(
        "loan", Kind.INST, "وام خودرو", "بانک ملت", created = today,
        installments = listOf(
            Installment("i1", j(1405, 7, 25), 4_200_000, j(1405, 7, 26)),
            Installment("i2", j(1405, 8, 25), 4_200_000, j(1405, 8, 25)),
            Installment("i3", j(1405, 9, 25), 4_200_000, null), // not paid yet: not history
        ),
    )

    private val debtOwed = Item("d1", Kind.DEBT, "قرض به رضا", created = today, dir = Dir.OWED, total = 5_000_000, payments = listOf(Payment("x", today, 1_000_000)))
    private val debtOwe = Item("d2", Kind.DEBT, "قرض از علی", created = today, dir = Dir.OWE, total = 5_000_000, payments = listOf(Payment("y", j(1405, 8, 10), 2_000_000)))
    private val chequeOut = Item("c1", Kind.CHEQUE, "چک اجاره", created = today, dir = Dir.OWE, total = 12_000_000, due = j(1405, 8, 20), payments = listOf(Payment("z", j(1405, 8, 20), 12_000_000)))
    private val chequeIn = Item("c2", Kind.CHEQUE, "چک مشتری", created = today, dir = Dir.OWED, total = 30_000_000, due = j(1405, 8, 20), payments = listOf(Payment("w", j(1405, 8, 21), 30_000_000)))

    @Test
    fun historyMergesEverythingIPaidAndNothingIReceived() {
        val e1 = expense("e1", "برق‌کار", "علی رضایی", 1_500_000, j(1405, 8, 27), "ساختمان", card)
        val all = History.build(listOf(loan, debtOwed, debtOwe, chequeOut, chequeIn, e1))
        val ids = all.map { it.itemId }
        assertTrue("loan" in ids && "d2" in ids && "c1" in ids && "e1" in ids)
        assertFalse("d1" in ids) // a debt someone owes me: money coming in
        assertFalse("c2" in ids) // a cheque I received
        assertEquals(2, all.count { it.itemId == "loan" }) // only the two paid installments
        assertEquals(all.sortedByDescending { it.date }.map { it.date }, all.map { it.date }) // newest first
        assertEquals("قسط ۲ از ۳", all.first { it.itemId == "loan" && it.date == j(1405, 8, 25) }.detail)
    }

    @Test
    fun monthsFollowTheJalaliCalendarAndTotalsAreRight() {
        val last = expense("a", "الف", "x", 100, j(1405, 8, 30))   // last day of Aban
        val first = expense("b", "ب", "x", 200, j(1405, 9, 1))     // first day of Azar
        val groups = History.byMonth(History.build(listOf(last, first)))
        assertEquals(listOf(1405 to 9, 1405 to 8), groups.map { it.y to it.m })
        assertEquals(200L, groups[0].total)
        assertEquals(100L, groups[1].total)
    }

    @Test
    fun breakdownGroupsByTagOrKindLargestFirst() {
        val items = listOf(
            expense("a", "برق", "علی", 1_500_000, j(1405, 8, 27), "ساختمان"),
            expense("b", "کناف", "احمدی", 6_000_000, j(1405, 8, 24), "ساختمان"),
            expense("c", "گاز", "شرکت", 850_000, j(1405, 8, 12), "قبض"),
            loan,
        )
        val aban = History.byMonth(History.build(items)).first { it.m == 8 }
        assertEquals(listOf("ساختمان" to 7_500_000L, "اقساط" to 4_200_000L, "قبض" to 850_000L), aban.breakdown)
        assertEquals(12_550_000L, aban.total)
    }

    @Test
    fun filterByKindAndSearchByNameTagAndCardNumber() {
        val all = History.build(
            listOf(
                expense("a", "برق‌کار", "علی رضایی", 1_500_000, j(1405, 8, 27), "ساختمان", card),
                expense("b", "قبض گاز", "شرکت گاز", 850_000, j(1405, 8, 12), "قبض"),
                loan,
            )
        )
        assertEquals(2, History.filter(all, Kind.INST, "").size)
        assertEquals(listOf("a"), History.filter(all, null, "رضایی").map { it.itemId })
        assertEquals(listOf("a"), History.filter(all, null, "ساختمان").map { it.itemId })
        assertEquals(listOf("a"), History.filter(all, null, "۷۸۹۳").map { it.itemId })      // Persian digits match the stored Latin digits
        assertEquals(listOf("a"), History.filter(all, null, "6037 9912").map { it.itemId })  // several words, all must match
        assertEquals(listOf("b"), History.filter(all, null, "قبض گاز").map { it.itemId })
        assertEquals(listOf("b"), History.filter(all, null, "قبض").map { it.itemId })
        assertTrue(History.filter(all, null, "ناموجود").isEmpty())
        // spelling variants: Arabic ي / ك match the Persian ی / ک
        assertEquals(listOf("a"), History.filter(all, null, "برق‌كار").map { it.itemId })
        assertEquals(all.size, History.filter(all, null, "  ").size) // a blank search shows everything
    }

    @Test
    fun payeeSuggestionsRememberTheLastCardAndTag() {
        val newCard = card.copy(id = "t2", number = "6104337912345678")
        val items = listOf(
            expense("1", "برق‌کار", "علی رضایی", 1_000_000, j(1405, 6, 1), "ساختمان", card),
            expense("2", "برق‌کار ۲", "علی رضایی", 500_000, j(1405, 8, 1), "ساختمان", newCard),
            expense("3", "نقاشی", "احمدی", 2_000_000, j(1405, 7, 1), "", null),
        )
        val p = History.payees(items)
        assertEquals(listOf("علی رضایی", "احمدی"), p.map { it.name }) // most recent payment first
        assertEquals(2, p.first().count)
        assertEquals("6104337912345678", p.first().lastTarget!!.number) // the newest card, not the first one
        assertEquals("ساختمان", p.first().lastTag)
        assertNull(p[1].lastTarget)
        assertEquals(listOf("ساختمان"), History.tags(items))
    }

    @Test
    fun expensesAreNeverObligationsRemindersOrStats() {
        val e = expense("e", "برق‌کار", "علی", 1_500_000, today, "ساختمان")
        assertFalse(e.isObligation)
        assertTrue(e.dues().isEmpty())
        assertEquals(ItemState.DONE, e.info(today).state)
        val st = computeStats(listOf(e, loan), today)
        assertEquals(loan.installments.sumOf { it.amount }, st.total) // the expense adds nothing to what I owe
        assertEquals(4_200_000L * 2, st.paid)
    }

    @Test
    fun expenseSurvivesJsonRoundTripAndOldAppsFilesStillLoad() {
        val e = expense("e", "برق‌کار", "علی رضایی", 1_500_000, j(1405, 8, 27), "ساختمان", card).copy(note = "سیم‌کشی", updatedAt = 99)
        val back = Store.itemFromJson(Store.itemToJson(e))
        assertEquals(e, back)
        assertEquals("ساختمان", back.tag)
        val old = Store.itemToJson(loan).apply { remove("tag") } // written before tags existed
        assertEquals("", Store.itemFromJson(old).tag)
    }
}
