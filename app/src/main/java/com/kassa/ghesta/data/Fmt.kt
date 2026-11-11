package com.kassa.ghesta.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val FA_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Latin digits -> Persian digits. */
fun String.fa(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
    return sb.toString()
}

fun Int.fa(): String = toString().fa()

/** Keep only digits, accepting Latin, Persian and Arabic-Indic digits. */
fun String.digitsOnly(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        when (c) {
            in '0'..'9' -> sb.append(c)
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
        }
    }
    return sb.toString()
}

/** 1234567 -> ۱٬۲۳۴٬۵۶۷ */
fun fmtNum(n: Long): String = String.format(Locale.US, "%,d", n).replace(',', '٬').fa()

fun fmtDate(d: LocalDate, weekday: Boolean = false, year: Boolean = true): String {
    val j = Jalali.toJalali(d)
    val sb = StringBuilder()
    if (weekday) sb.append(Jalali.weekdayName(d)).append(' ')
    sb.append(j.d).append(' ').append(Jalali.monthNames[j.m - 1])
    if (year) sb.append(' ').append(j.y)
    return sb.toString().fa()
}

fun fmtDateShort(d: LocalDate): String {
    val j = Jalali.toJalali(d)
    return "${j.y}/${j.m.toString().padStart(2, '0')}/${j.d.toString().padStart(2, '0')}".fa()
}

fun fmtMonth(y: Int, m: Int): String = "${Jalali.monthNames[m - 1]} $y".fa()

fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

/** Human relative label for (due - today) in days. */
fun relLabel(diff: Long): String = when {
    diff == 0L -> "امروز"
    diff == 1L -> "فردا"
    diff == 2L -> "پس‌فردا"
    diff in 3..60 -> "${diff.toInt().fa()} روز دیگر"
    diff > 60 -> "${(diff / 30).toInt().fa()} ماه دیگر"
    diff >= -60 -> "${(-diff).toInt().fa()} روز تأخیر"
    else -> "${(-diff / 30).toInt().fa()} ماه تأخیر"
}

/** 2500000 -> «۲ میلیون و ۵۰۰ هزار تومان» — makes typos in amounts obvious. */
fun amountWords(n: Long, unit: String): String {
    if (n <= 0) return ""
    val units = listOf(1_000_000_000_000L to "تریلیون", 1_000_000_000L to "میلیارد", 1_000_000L to "میلیون", 1_000L to "هزار")
    var r = n
    val parts = mutableListOf<String>()
    for ((u, name) in units) {
        val q = r / u
        if (q > 0) {
            parts.add("${fmtNum(q)} $name")
            r -= q * u
        }
    }
    if (r > 0) parts.add(fmtNum(r))
    return parts.joinToString(" و ") + " " + unit
}
