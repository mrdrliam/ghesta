package com.kassa.ghesta.data

import java.time.DayOfWeek
import java.time.LocalDate

/** A date in the Jalali (Solar Hijri) calendar. */
data class JDate(val y: Int, val m: Int, val d: Int)

/**
 * Jalali <-> Gregorian conversion (port of the well-known jalaali algorithm).
 * Everything else in the app stores plain [LocalDate]s; only the UI speaks Jalali.
 */
object Jalali {
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private class Cal(val leap: Int, val gy: Int, val march: Int)

    private fun div(a: Int, b: Int) = a / b
    private fun mod(a: Int, b: Int) = a - (a / b) * b

    private fun cal(jy: Int): Cal {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        require(jy >= jp && jy < breaks[bl - 1]) { "Invalid Jalali year $jy" }
        for (i in 1 until bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return Cal(leap, gy, march)
    }

    fun isLeap(jy: Int): Boolean = cal(jy).leap == 0

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeap(jy)) 30 else 29
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): LocalDate {
        val r = cal(jy)
        val nowruz = LocalDate.of(r.gy, 3, r.march).toEpochDay()
        return LocalDate.ofEpochDay(nowruz + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1)
    }

    fun toJalali(date: LocalDate): JDate {
        val gy = date.year
        var jy = gy - 621
        val r = cal(jy)
        val nowruz = LocalDate.of(gy, 3, r.march).toEpochDay()
        var k = (date.toEpochDay() - nowruz).toInt()
        if (k >= 0) {
            if (k <= 185) return JDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun weekdayName(d: LocalDate): String = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> "شنبه"
        DayOfWeek.SUNDAY -> "یکشنبه"
        DayOfWeek.MONDAY -> "دوشنبه"
        DayOfWeek.TUESDAY -> "سه‌شنبه"
        DayOfWeek.WEDNESDAY -> "چهارشنبه"
        DayOfWeek.THURSDAY -> "پنجشنبه"
        DayOfWeek.FRIDAY -> "جمعه"
    }

    /** 0 = Saturday … 6 = Friday (column in a Persian calendar grid). */
    fun weekColumn(d: LocalDate): Int = (d.dayOfWeek.value + 1) % 7

    /** Dates of a repeating schedule that keeps the original day-of-month (clamped to month length). */
    fun schedule(first: LocalDate, count: Int, stepMonths: Int): List<LocalDate> {
        val j = toJalali(first)
        return (0 until count).map { i ->
            val total = (j.m - 1) + i * stepMonths
            val y = j.y + total / 12
            val m = total % 12 + 1
            toGregorian(y, m, minOf(j.d, monthLength(y, m)))
        }
    }
}
