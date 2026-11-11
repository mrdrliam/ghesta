package com.kassa.ghesta.data

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToLong

/** Which two or three numbers the user knows; the calculator derives the rest. */
enum class CalcMode {
    /** installment amount + count  ->  end date, total */
    AMOUNT_COUNT,

    /** installment amount + end date  ->  count, total */
    AMOUNT_END,

    /** total + count  ->  installment amount */
    TOTAL_COUNT,

    /** total + installment amount  ->  count, end date */
    TOTAL_AMOUNT,

    /** total + end date  ->  count, installment amount */
    TOTAL_END,

    /** loan principal + yearly interest + count  ->  installment, total interest */
    LOAN,
}

class CalcResult(
    val count: Int,
    val amount: Long,
    /** The last installment can differ slightly so that the sum matches the total exactly. */
    val lastAmount: Long,
    val dates: List<LocalDate>,
    val interest: Long = 0,
) {
    val total: Long get() = amount * (count - 1) + lastAmount
    val first: LocalDate get() = dates.first()
    val last: LocalDate get() = dates.last()
}

/** [result] is null while inputs are incomplete; [error] is set only for real conflicts the user should fix. */
class Calc(val result: CalcResult?, val error: String?)

object InstallmentCalc {
    const val MAX = 360

    /** How many installments (every [step] months from [first]) fall on or before [end]. */
    fun countUntil(first: LocalDate, end: LocalDate, step: Int): Int {
        if (end.isBefore(first)) return 0
        return Jalali.schedule(first, MAX, step).count { !it.isAfter(end) }
    }

    /** Splits [total] into [count] installments; the last one absorbs the rounding difference. */
    fun split(total: Long, count: Int): Pair<Long, Long> {
        if (count <= 1) return total to total
        val raw = total / count
        var base = if (raw >= 10_000) ((total.toDouble() / count) / 1000.0).roundToLong() * 1000 else raw
        var last = total - base * (count - 1)
        if (base <= 0 || last <= 0) {
            base = raw
            last = total - base * (count - 1)
        }
        return base to last
    }

    fun compute(
        mode: CalcMode,
        first: LocalDate,
        step: Int,
        amount: Long,
        count: Int,
        total: Long,
        end: LocalDate?,
        yearlyRate: Double?,
    ): Calc {
        fun ok(n: Int, a: Long, last: Long, interest: Long = 0) =
            Calc(CalcResult(n, a, last, Jalali.schedule(first, n, step), interest), null)

        val incomplete = Calc(null, null)
        val endBeforeStart = Calc(null, "تاریخ پایان باید بعد از تاریخ اولین قسط باشد")
        val tooMany = Calc(null, "تعداد اقساط بیشتر از ۳۶۰ می‌شود")

        return when (mode) {
            CalcMode.AMOUNT_COUNT ->
                if (amount > 0 && count in 1..MAX) ok(count, amount, amount) else incomplete

            CalcMode.AMOUNT_END -> {
                if (amount <= 0 || end == null) return incomplete
                val n = countUntil(first, end, step)
                if (n == 0) endBeforeStart else ok(n, amount, amount)
            }

            CalcMode.TOTAL_COUNT -> {
                if (total <= 0 || count !in 1..MAX) return incomplete
                val (a, l) = split(total, count)
                ok(count, a, l)
            }

            CalcMode.TOTAL_AMOUNT -> {
                if (total <= 0 || amount <= 0) return incomplete
                if (amount >= total) return ok(1, total, total)
                val n = ((total + amount - 1) / amount).toInt()
                if (n > MAX) tooMany else ok(n, amount, total - amount * (n - 1))
            }

            CalcMode.TOTAL_END -> {
                if (total <= 0 || end == null) return incomplete
                val n = countUntil(first, end, step)
                if (n == 0) return endBeforeStart
                val (a, l) = split(total, n)
                ok(n, a, l)
            }

            CalcMode.LOAN -> {
                if (total <= 0 || yearlyRate == null || count !in 1..MAX) return incomplete
                val r = yearlyRate / 100.0 / 12.0 * step
                val payment = if (r == 0.0) total.toDouble() / count else total * r / (1 - (1 + r).pow(-count))
                val a = ceil(payment).toLong()
                ok(count, a, a, interest = a * count - total)
            }
        }
    }
}
