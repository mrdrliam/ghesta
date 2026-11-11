package com.kassa.ghesta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Info
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.info
import com.kassa.ghesta.ui.theme.G
import java.time.LocalDate

private val filterNames = listOf("همه", "عقب‌افتاده", "قسط و وام", "بدهی و طلب", "تسویه‌شده")

private fun rank(i: Info) = when (i.state) {
    ItemState.OVERDUE -> 0
    ItemState.SOON -> 1
    ItemState.OK -> 2
    ItemState.DONE -> 3
}

@Composable
fun ItemsScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val today = Store.today
    val items = Store.items
    val rows = remember(items, today) { items.map { it to it.info(today) } }

    fun matches(f: Int, p: Pair<Item, Info>) = when (f) {
        1 -> p.second.state == ItemState.OVERDUE && p.first.dir == Dir.OWE
        2 -> p.first.kind == Kind.INST && !p.second.done
        3 -> p.first.kind == Kind.DEBT && !p.second.done
        4 -> p.second.done
        else -> true
    }

    val overdueCount = rows.count { matches(1, it) }
    val filtered = rows.filter { matches(app.filter, it) }.sortedWith(
        compareBy<Pair<Item, Info>> { rank(it.second) }
            .thenBy { it.second.overdue.firstOrNull()?.date ?: it.second.next?.date ?: LocalDate.MAX }
    )

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T("اقساط و بدهی‌ها", 24, FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filterNames.size) { i ->
                        GChip(
                            filterNames[i], app.filter == i, { app.filter = i },
                            count = if (i == 1) overdueCount else null, danger = i == 1,
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                T(
                    when {
                        items.isEmpty() -> "هنوز چیزی ثبت نکرده‌اید. با دکمه‌ی + شروع کنید."
                        app.filter == 1 -> "هیچ مورد عقب‌افتاده‌ای ندارید. آفرین!"
                        app.filter == 4 -> "هنوز موردی کاملاً تسویه نشده است."
                        else -> "موردی پیدا نشد."
                    },
                    14, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                )
            }
        }
        items(filtered, key = { it.first.id }) { (item, info) ->
            ItemCard(item, info, today) { app.detailId = item.id }
        }
    }
}

@Composable
fun ItemCard(item: Item, info: Info, today: LocalDate, onClick: () -> Unit) {
    val c = G.c
    val unit = Store.settings.unit
    val tone = stateTone(info.state)
    GCard(onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KindIcon(item.kind, if (info.state == ItemState.DONE) Tone.Ok else if (info.state == ItemState.OVERDUE) Tone.Danger else Tone.Gold)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        T(item.title, 16, FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (item.kind == Kind.DEBT) Pill(if (item.dir == Dir.OWE) "بدهی من" else "طلب من", Tone.Gold)
                    }
                    if (item.party.isNotBlank()) T(item.party, 12, color = c.muted, maxLines = 1)
                }
                StatePill(item, info, today)
            }

            GProgress(info.progress, color = if (info.state == ItemState.DONE) c.ok else c.gold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    T(
                        if (item.kind == Kind.INST) "${info.paidCount.fa()} از ${info.count.fa()} قسط پرداخت شده"
                        else "${fmtNum(info.paid)} از ${fmtNum(info.total)} پرداخت شده",
                        12, FontWeight.Medium, c.ink2,
                    )
                    val nextText = when {
                        info.done -> "تسویه کامل"
                        info.overdue.isNotEmpty() -> "از ${fmtDate(info.overdue.first().date)}"
                        info.next != null -> "سررسید: ${fmtDate(info.next.date)}"
                        else -> "بدون سررسید"
                    }
                    T(nextText, 12, color = if (info.overdue.isNotEmpty()) c.danger else c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    T("مانده", 11, color = c.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                        T(fmtNum(info.remaining), 17, FontWeight.Bold, if (info.done) c.ok else c.ink)
                        T(unit, 11, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
            }
        }
    }
}
