package com.kassa.ghesta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtMonth
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.isObligation
import com.kassa.ghesta.data.relLabel
import com.kassa.ghesta.ui.theme.G

private data class MonthGroup(val y: Int, val m: Int, val dues: List<Due>)

/** Month-by-month plan: how much is due when, what is already paid. */
@Composable
fun ScheduleScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val today = Store.today
    val items = Store.items
    val unit = Store.settings.unit
    val jNow = remember(today) { Jalali.toJalali(today) }

    val groups = remember(items, today) {
        items.filter { it.isObligation }.flatMap { it.dues() }
            .groupBy { Jalali.toJalali(it.date).let { j -> j.y * 100 + j.m } }
            .toSortedMap()
            .map { (k, list) -> MonthGroup(k / 100, k % 100, list.sortedBy { it.date }) }
            // keep current/future months, and past months that still have something unpaid
            .filter { g -> (g.y * 100 + g.m) >= (jNow.y * 100 + jNow.m) || g.dues.any { !it.paid } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                T("برنامه‌ی پرداخت", 24, FontWeight.Bold)
                T("ببینید هر ماه چقدر باید بپردازید", 13, color = c.muted)
            }
        }
        if (groups.isEmpty()) {
            item {
                T("هنوز سررسیدی ثبت نشده است.", 14, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp))
            }
        }
        groups.forEach { g ->
            val isCurrent = g.y == jNow.y && g.m == jNow.m
            val isPast = (g.y * 100 + g.m) < (jNow.y * 100 + jNow.m)
            val unpaid = g.dues.filter { !it.paid }
            val monthOverdue = g.dues.any { !it.paid && it.date.isBefore(today) }
            item(key = "m${g.y}-${g.m}") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        T(fmtMonth(g.y, g.m), 17, FontWeight.Bold, maxLines = 1)
                        if (isCurrent) Pill("این ماه", Tone.Gold)
                        if (monthOverdue) Pill("عقب‌افتاده", Tone.Danger)
                        Box(Modifier.weight(1f))
                        if (unpaid.isEmpty()) Pill("پرداخت شد", Tone.Ok)
                        else T("${fmtNum(unpaid.sumOf { it.amount })} $unit", 13, FontWeight.Bold, c.ink2, maxLines = 1)
                    }
                    GCard {
                        val shown = if (isPast) unpaid else g.dues
                        shown.forEachIndexed { i, d ->
                            if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                            ScheduleRow(app, d, today)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(app: AppState, d: Due, today: java.time.LocalDate) {
    val c = G.c
    val diff = daysBetween(today, d.date)
    val overdue = !d.paid && diff < 0
    val j = Jalali.toJalali(d.date)
    Row(
        Modifier.fillMaxWidth().clickable { app.detailId = d.item.id }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(if (d.paid) c.okBg else if (overdue) c.dangerBg else c.surface2),
            contentAlignment = Alignment.Center,
        ) { T(j.d.fa(), 17, FontWeight.Bold, if (d.paid) c.ok else if (overdue) c.danger else c.ink) }
        Column(Modifier.weight(1f)) {
            T(d.item.title, 15, FontWeight.SemiBold, if (d.paid) c.muted else c.ink, maxLines = 1)
            T(
                if (d.paid) "${d.label} · پرداخت شد" else "${d.label} · ${relLabel(diff)}",
                12, FontWeight.Medium, if (overdue) c.danger else c.muted, maxLines = 1,
            )
        }
        T(fmtNum(d.amount), 15, FontWeight.SemiBold, if (d.paid) c.muted else c.ink, maxLines = 1)
        if (d.item.kind == Kind.INST) {
            CheckCircle(d.paid, { app.togglePaid(d.item.id, d.key) }, 34.dp, "پرداخت")
        } else {
            CheckCircle(d.paid, { app.payDue(d) }, 34.dp, "پرداخت")
        }
    }
}
