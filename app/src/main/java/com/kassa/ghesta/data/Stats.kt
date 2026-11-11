package com.kassa.ghesta.data

import java.time.LocalDate

/** Dashboard numbers derived from all items. Only obligations (things I must pay) feed the main totals. */
class Stats(
    val total: Long,
    val paid: Long,
    val overdue: List<Due>,
    val upcoming: List<Due>,
    val monthAmount: Long,
    val next30Amount: Long,
    val recvRemaining: Long,
    val recvCount: Int,
    val recvOverdue: Int,
) {
    val remaining: Long get() = (total - paid).coerceAtLeast(0)
    val overdueAmount: Long get() = overdue.sumOf { it.amount }
    val progress: Float get() = if (total <= 0) 0f else (paid.toFloat() / total).coerceIn(0f, 1f)
}

fun computeStats(items: List<Item>, today: LocalDate): Stats {
    var total = 0L
    var paid = 0L
    val unpaid = mutableListOf<Due>()
    var recvRemaining = 0L
    var recvCount = 0
    var recvOverdue = 0
    for (it in items) {
        if (it.isObligation) {
            total += it.totalAmount
            paid += it.paidTotal
            unpaid += it.dues().filter { d -> !d.paid }
        } else {
            val rem = it.remaining
            if (rem > 0) {
                recvRemaining += rem
                recvCount++
                if (it.due?.isBefore(today) == true) recvOverdue++
            }
        }
    }
    unpaid.sortBy { it.date }
    val overdue = unpaid.filter { it.date.isBefore(today) }
    val upcoming = unpaid.filter { !it.date.isBefore(today) }
    val jNow = Jalali.toJalali(today)
    val monthAmount = unpaid.filter {
        val j = Jalali.toJalali(it.date)
        j.y == jNow.y && j.m == jNow.m
    }.sumOf { it.amount }
    val next30 = upcoming.filter { daysBetween(today, it.date) <= 30 }.sumOf { it.amount }
    return Stats(total, paid, overdue, upcoming, monthAmount, next30, recvRemaining, recvCount, recvOverdue)
}
