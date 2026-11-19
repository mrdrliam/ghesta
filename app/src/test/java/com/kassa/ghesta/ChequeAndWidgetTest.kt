package com.kassa.ghesta

import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Payment
import com.kassa.ghesta.data.PayTarget
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.TargetType
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.isObligation
import com.kassa.ghesta.ui.AppLock
import com.kassa.ghesta.widget.WidgetTone
import com.kassa.ghesta.widget.buildWidgetContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChequeAndWidgetTest {
    private val today = LocalDate.of(2026, 11, 19)

    private fun cheque(due: LocalDate, dir: Dir = Dir.OWE, bounced: Boolean = false, paid: Long = 0, amount: Long = 5_000_000) = Item(
        id = "c1", kind = Kind.CHEQUE, title = "چک اجاره", created = today, dir = dir, total = amount, due = due,
        chequeNo = "1234567890123456", bank = "بانک ملی", bounced = bounced,
        payments = if (paid > 0) listOf(Payment("p", today, paid)) else emptyList(),
    )

    @Test
    fun chequeStates() {
        assertEquals(ItemState.OK, cheque(today.plusDays(30)).info(today).state)
        assertEquals(ItemState.SOON, cheque(today.plusDays(3)).info(today).state)
        assertEquals(ItemState.OVERDUE, cheque(today.minusDays(1)).info(today).state)
        // a bounced cheque needs attention even before its date
        assertEquals(ItemState.OVERDUE, cheque(today.plusDays(30), bounced = true).info(today).state)
        // once it has passed, nothing else matters
        val passed = cheque(today.minusDays(5), bounced = true, paid = 5_000_000).info(today)
        assertEquals(ItemState.DONE, passed.state)
        assertEquals(0L, passed.remaining)
    }

    @Test
    fun chequeOwnershipAndDues() {
        assertTrue(cheque(today, Dir.OWE).isObligation)
        assertFalse(cheque(today, Dir.OWED).isObligation) // a cheque I received is money coming in
        val due = cheque(today.plusDays(2)).dues().single()
        assertEquals("چک صادره", due.label)
        assertEquals("چک دریافتی", cheque(today, Dir.OWED).dues().single().label)
        assertEquals(5_000_000L, due.amount)
        assertFalse(due.paid)
    }

    @Test
    fun widgetShowsOverdueFirstAndHidesDetailsWhenLocked() {
        val loan = Item(
            "l1", Kind.INST, "وام خودرو", created = today,
            installments = listOf(
                Installment("a", today.minusDays(3), 1_000_000, null),
                Installment("b", today.plusDays(27), 1_000_000, null),
            ),
        )
        val c = buildWidgetContent(listOf(loan, cheque(today.plusDays(2))), today, "تومان", hideDetails = false)
        assertEquals(WidgetTone.DANGER, c.tone)
        assertTrue(c.summary.contains("عقب‌افتاده"))
        assertEquals("وام خودرو", c.rows.first().title) // overdue sorts first
        assertTrue(c.rows.first().overdue)
        assertEquals(3, c.rows.size)

        val hidden = buildWidgetContent(listOf(loan, cheque(today.plusDays(2))), today, "تومان", hideDetails = true)
        assertTrue(hidden.rows.isEmpty())
        assertFalse(hidden.summary.contains("تومان")) // no amounts while locked
        assertFalse(hidden.summary.contains("وام"))   // and no names
    }

    @Test
    fun widgetStates() {
        assertEquals(WidgetTone.NEUTRAL, buildWidgetContent(emptyList(), today, "تومان", false).tone)
        val far = Item("x", Kind.DEBT, "قرض", created = today, total = 100, due = today.plusDays(60))
        val ok = buildWidgetContent(listOf(far), today, "تومان", false)
        assertEquals(WidgetTone.OK, ok.tone)
        assertEquals("همه‌چیز به‌روز است", ok.summary)
        val soon = buildWidgetContent(listOf(far.copy(due = today.plusDays(4))), today, "تومان", false)
        assertEquals(WidgetTone.WARN, soon.tone)
    }

    @Test
    fun lockRule() {
        assertFalse(AppLock.shouldLock(enabled = false, everUnlocked = false, awayMs = 0, graceSec = 60))
        assertTrue(AppLock.shouldLock(true, everUnlocked = false, awayMs = 0, graceSec = 60)) // cold start
        assertFalse(AppLock.shouldLock(true, true, awayMs = 30_000, graceSec = 60)) // quick trip to the bank app
        assertTrue(AppLock.shouldLock(true, true, awayMs = 61_000, graceSec = 60))
    }

    @Test
    fun chequeTargetsAndSettingsSurviveJsonRoundTrip() {
        val item = cheque(today.plusDays(9)).copy(
            note = "یادداشت", targets = listOf(PayTarget("t1", TargetType.FACILITY, "1403/123456", "بانک ملی", 2_500_000)),
            updatedAt = 123,
        )
        val back = Store.itemFromJson(Store.itemToJson(item))
        assertEquals(item, back)
        assertEquals("1234567890123456", back.chequeNo)
        assertEquals(TargetType.FACILITY, back.targets.single().type)
        // an item written by an older app (no cheque fields) still loads
        val old = Store.itemToJson(item.copy(kind = Kind.DEBT)).apply { remove("chequeNo"); remove("bank"); remove("bounced") }
        val oldItem = Store.itemFromJson(old)
        assertEquals("", oldItem.chequeNo)
        assertFalse(oldItem.bounced)
        assertNull(null)
    }
}
