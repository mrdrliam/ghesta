package com.kassa.ghesta.data

import java.time.LocalDate

enum class Kind { INST, DEBT, CHEQUE }

/** OWE = I owe someone. OWED = someone owes me. */
enum class Dir { OWE, OWED }

enum class ItemState { DONE, OVERDUE, SOON, OK }

enum class TargetType { CARD, SHEBA, ACCOUNT, FACILITY }

/**
 * Where to send the money for a loan or debt: a card, Sheba (IBAN) or account number,
 * with the amount that goes to it. A FACILITY is a bank's loan (تسهیلات) number, which some banks
 * want instead of an account when you pay an installment; [holder] then holds the bank's name.
 * [number] is stored the way it is pasted into a banking app: a card is 16 digits, a Sheba is
 * "IR" + 24 digits, an account or facility number is digits (dashes and slashes allowed).
 */
data class PayTarget(
    val id: String,
    val type: TargetType,
    val number: String,
    val holder: String = "",
    val amount: Long = 0,
)

data class Installment(
    val id: String,
    val due: LocalDate,
    val amount: Long,
    val paidAt: LocalDate?,
)

data class Payment(
    val id: String,
    val date: LocalDate,
    val amount: Long,
)

/**
 * One tracked obligation. [Kind.INST] has a schedule of [installments];
 * [Kind.DEBT] has a [total], an optional [due] date and a list of partial [payments];
 * [Kind.CHEQUE] is a single amount ([total]) that is settled on [due]: [Dir.OWE] is a cheque I issued
 * (it must be covered), [Dir.OWED] a cheque I received (I must cash it). It is settled by one payment.
 */
data class Item(
    val id: String,
    val kind: Kind,
    val title: String,
    val party: String = "",
    val note: String = "",
    val created: LocalDate,
    val installments: List<Installment> = emptyList(),
    val dir: Dir = Dir.OWE,
    val total: Long = 0,
    val due: LocalDate? = null,
    val payments: List<Payment> = emptyList(),
    /** Cards / accounts to pay into (optional). */
    val targets: List<PayTarget> = emptyList(),
    /** Cheque only: serial or Sayad number, and the bank it is drawn on. */
    val chequeNo: String = "",
    val bank: String = "",
    /** Cheque only: the cheque bounced, which needs attention even before the due date. */
    val bounced: Boolean = false,
    /** Last local edit time (epoch ms). Used to resolve conflicts when syncing between devices. */
    val updatedAt: Long = 0,
)

/** A single thing that has to be paid (or collected) on a date. */
data class Due(
    val item: Item,
    val key: String,
    val n: Int,
    val of: Int,
    val date: LocalDate,
    val amount: Long,
    val paid: Boolean,
) {
    val label: String
        get() = when {
            item.kind == Kind.INST -> "قسط ${n.fa()} از ${of.fa()}"
            item.kind == Kind.CHEQUE -> if (item.dir == Dir.OWE) "چک صادره" else "چک دریافتی"
            item.dir == Dir.OWE -> "بدهی"
            else -> "طلب"
        }
}

data class Info(
    val total: Long,
    val paid: Long,
    val count: Int,
    val paidCount: Int,
    val overdue: List<Due>,
    val next: Due?,
    val done: Boolean,
    val state: ItemState,
) {
    val remaining: Long get() = (total - paid).coerceAtLeast(0)
    val overdueAmount: Long get() = overdue.sumOf { it.amount }
    val progress: Float get() = if (total <= 0) 0f else (paid.toFloat() / total).coerceIn(0f, 1f)
}

val Item.paidTotal: Long
    get() = when (kind) {
        Kind.INST -> installments.filter { it.paidAt != null }.sumOf { it.amount }
        Kind.DEBT, Kind.CHEQUE -> payments.sumOf { it.amount }
    }

val Item.totalAmount: Long
    get() = when (kind) {
        Kind.INST -> installments.sumOf { it.amount }
        Kind.DEBT, Kind.CHEQUE -> total
    }

val Item.remaining: Long get() = (totalAmount - paidTotal).coerceAtLeast(0)

/** Receivables (someone owes me) never count as my obligations. */
val Item.isObligation: Boolean get() = !(kind != Kind.INST && dir == Dir.OWED)

/** Everything that is a single sum with an optional due date (a debt, or a cheque). */
val Item.isLumpSum: Boolean get() = kind != Kind.INST

fun Item.dues(): List<Due> = when (kind) {
    Kind.INST -> installments.mapIndexed { i, x ->
        Due(this, x.id, i + 1, installments.size, x.due, x.amount, x.paidAt != null)
    }
    Kind.DEBT, Kind.CHEQUE -> {
        val rem = remaining
        val d = due
        if (d != null) listOf(Due(this, "debt", 1, 1, d, rem, rem <= 0)) else emptyList()
    }
}

fun Item.info(today: LocalDate): Info {
    val total = totalAmount
    val paid = paidTotal
    val unpaid = dues().filter { !it.paid }
    val overdue = unpaid.filter { it.date.isBefore(today) }
    val next = unpaid.firstOrNull { !it.date.isBefore(today) }
    val done = when (kind) {
        Kind.INST -> installments.isNotEmpty() && installments.all { it.paidAt != null }
        Kind.DEBT, Kind.CHEQUE -> total > 0 && remaining <= 0
    }
    val count = if (kind == Kind.INST) installments.size else 1
    val paidCount = when (kind) {
        Kind.INST -> installments.count { it.paidAt != null }
        Kind.DEBT, Kind.CHEQUE -> if (done) 1 else 0
    }
    val state = when {
        done -> ItemState.DONE
        overdue.isNotEmpty() || (kind == Kind.CHEQUE && bounced) -> ItemState.OVERDUE
        next != null && daysBetween(today, next.date) <= 7 -> ItemState.SOON
        else -> ItemState.OK
    }
    return Info(total, paid, count, paidCount, overdue, next, done, state)
}

data class Settings(
    /** 0 = follow system, 1 = light, 2 = dark */
    val theme: Int = 0,
    val notify: Boolean = true,
    val remindDays: Set<Int> = setOf(3, 1, 0),
    val remindHour: Int = 9,
    /** Ask for the fingerprint / screen lock when the app is opened. Off by default. */
    val appLock: Boolean = false,
    /** How long the app may be left before it asks again (seconds). */
    val lockGraceSec: Int = 60,
) {
    /** The whole app works in Toman only. */
    val unit: String get() = "تومان"
}
