package com.kassa.ghesta.widget

import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.computeStats
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.relLabel
import java.time.LocalDate

enum class WidgetTone { DANGER, WARN, OK, NEUTRAL }

class WidgetRowData(
    val itemId: String,
    val title: String,
    val sub: String,
    val amount: String,
    val rel: String,
    val overdue: Boolean,
)

class WidgetContent(
    val dateText: String,
    val summary: String,
    val tone: WidgetTone,
    val rows: List<WidgetRowData>,
    /** Shown instead of the list when there are no rows. */
    val emptyNote: String,
)

/**
 * What the home-screen widget shows. Pure function so it can be tested without Android.
 * With [hideDetails] (app lock on) the widget reveals counts only: no titles and no amounts.
 */
fun buildWidgetContent(
    items: List<Item>,
    today: LocalDate,
    unit: String,
    hideDetails: Boolean,
    maxRows: Int = 8,
): WidgetContent {
    val st = computeStats(items, today)
    val date = fmtDate(today, weekday = true, year = false)
    val soon = st.upcoming.filter { daysBetween(today, it.date) <= 7 }

    val summary: String
    val tone: WidgetTone
    when {
        items.isEmpty() -> {
            summary = "هنوز موردی ثبت نکرده‌اید"
            tone = WidgetTone.NEUTRAL
        }
        st.overdue.isNotEmpty() -> {
            summary = if (hideDetails) "${st.overdue.size.fa()} مورد عقب‌افتاده"
            else "${st.overdue.size.fa()} عقب‌افتاده، ${fmtNum(st.overdueAmount)} $unit"
            tone = WidgetTone.DANGER
        }
        soon.isNotEmpty() -> {
            summary = if (hideDetails) "${soon.size.fa()} پرداخت تا ۷ روز آینده"
            else "${soon.size.fa()} پرداخت تا ۷ روز آینده، ${fmtNum(soon.sumOf { it.amount })} $unit"
            tone = WidgetTone.WARN
        }
        else -> {
            summary = "همه‌چیز به‌روز است"
            tone = WidgetTone.OK
        }
    }

    val rows = if (hideDetails) emptyList() else (st.overdue + st.upcoming).take(maxRows).map {
        WidgetRowData(
            itemId = it.item.id,
            title = it.item.title,
            // a middle dot next to Persian digits reads like a zero (۰) in the system font, so use a comma
            sub = "${it.label}، ${fmtDate(it.date, year = false)}",
            amount = fmtNum(it.amount),
            rel = relLabel(daysBetween(today, it.date)),
            overdue = it.date.isBefore(today),
        )
    }
    val note = when {
        hideDetails && items.isNotEmpty() -> "برای دیدن جزئیات، برنامه را باز کنید"
        items.isEmpty() -> "برای شروع، برنامه را باز کنید"
        else -> "پرداخت پیش‌رویی ندارید"
    }
    return WidgetContent(date, summary, tone, rows, note)
}
