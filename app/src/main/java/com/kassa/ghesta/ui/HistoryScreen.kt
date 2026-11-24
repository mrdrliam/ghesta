package com.kassa.ghesta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kassa.ghesta.data.History
import com.kassa.ghesta.data.HistoryEntry
import com.kassa.ghesta.data.HistoryMonth
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtMonth
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.ui.theme.G
import com.kassa.ghesta.ui.theme.Vazir

private val filterKinds = listOf<Kind?>(null, Kind.INST, Kind.DEBT, Kind.CHEQUE, Kind.EXPENSE)
private val filterLabels = listOf("همه", "اقساط", "بدهی‌ها", "چک‌ها", "پرداخت‌ها")

/** Title and the "upcoming | paid" switch shared by both halves of the Schedule tab. */
@Composable
fun ScheduleHeader(app: AppState) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            T(if (app.scheduleMode == 0) "برنامه‌ی پرداخت" else "پرداخت‌شده‌ها", 24, FontWeight.Bold)
            T(if (app.scheduleMode == 0) "ببینید هر ماه چقدر باید بپردازید" else "ببینید چه زمانی، چقدر و به چه کسی پرداخته‌اید", 13, color = c.muted)
        }
        Segmented(listOf("پیش‌رو", "پرداخت‌شده‌ها"), app.scheduleMode, { app.scheduleMode = it })
    }
}

/** Everything that has been paid out, newest month first, with totals, filters and a search. */
@Composable
fun HistoryScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val items = Store.items
    val unit = Store.settings.unit
    val today = Store.today
    val all = remember(items) { History.build(items) }
    var kindFilter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(all, kindFilter, query) { History.filter(all, filterKinds[kindFilter], query) }
    val months = remember(filtered) { History.byMonth(filtered) }
    val filtering = query.isNotBlank() || kindFilter != 0
    val jNow = remember(today) { Jalali.toJalali(today) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScheduleHeader(app) }

        if (all.isEmpty()) {
            item {
                GCard {
                    Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T("هنوز پرداختی ثبت نشده", 17, FontWeight.Bold)
                        T(
                            "هزینه‌هایی مثل برق‌کار، کناف‌کار یا قبض را همین‌جا ثبت کنید تا بعداً ببینید چه زمانی، چقدر و به چه کسی داده‌اید. پرداخت قسط‌ها و بدهی‌ها هم خودکار به این فهرست می‌آید.",
                            13, color = c.ink2, align = TextAlign.Center,
                        )
                        PrimaryButton("ثبت اولین پرداخت") { app.addKind = 3; app.showAdd = true }
                    }
                }
            }
            return@LazyColumn
        }

        item { SearchBox(query, { query = it }) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterLabels.size) { i -> GChip(filterLabels[i], kindFilter == i, { kindFilter = i }) }
            }
        }

        // the summary: this month by default; the matching results when a filter or search is active
        val summaryEntries = if (filtering) filtered else months.firstOrNull { it.y == jNow.y && it.m == jNow.m }?.entries.orEmpty()
        item {
            SummaryCard(
                title = if (filtering) "جمع نتیجه‌ها" else "جمع پرداختی ${fmtMonth(jNow.y, jNow.m)}",
                entries = summaryEntries, unit = unit,
                emptyNote = if (filtering) "چیزی پیدا نشد." else "در این ماه هنوز پرداختی ثبت نشده است.",
            )
        }

        if (filtered.isEmpty()) {
            item { T("نتیجه‌ای پیدا نشد. فیلتر یا جست‌وجو را تغییر دهید.", 14, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) }
        }
        months.forEach { g -> item(key = "h${g.y}-${g.m}") { MonthBlock(app, g, unit) } }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val c = G.c
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = c.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) T("جست‌وجو: نام، برچسب یا شماره کارت", 14, color = c.muted.copy(alpha = 0.85f), maxLines = 1)
            BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(fontFamily = Vazir, fontSize = 15.sp, color = c.ink, lineHeight = 22.sp),
                cursorBrush = SolidColor(c.gold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) T("پاک کردن", 12, FontWeight.SemiBold, c.gold, Modifier.clip(RoundedCornerShape(8.dp)).clickable { onChange("") }.padding(4.dp))
    }
}

private val barColors = listOf(Color(0xFFE2C070), Color(0xFF52CBA0), Color(0xFF85B7EB), Color(0xFFF09595), Color(0xFFB4B2A9))

@Composable
private fun SummaryCard(title: String, entries: List<HistoryEntry>, unit: String, emptyNote: String) {
    val c = G.c
    val total = entries.sumOf { it.amount }
    // the four biggest buckets, everything else together
    val sorted = entries.groupBy { it.bucket }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
    val parts = if (sorted.size > 4) sorted.take(4) + ("سایر" to sorted.drop(4).sumOf { it.second }) else sorted
    Column(Modifier.fillMaxWidth().heroBackground(c).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            T(title, 13, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
            if (entries.isNotEmpty()) T("${entries.size.fa()} پرداخت", 12, color = Color.White.copy(alpha = 0.65f))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            T(fmtNum(total), 28, FontWeight.Bold, Color.White, maxLines = 1)
            T(unit, 12, FontWeight.Medium, c.gold2, Modifier.padding(bottom = 6.dp))
        }
        if (total > 0) {
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                parts.forEachIndexed { i, (_, v) ->
                    Box(Modifier.weight(v.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(barColors[i % barColors.size]))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                parts.forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(barColors[i % barColors.size]))
                        T(k, 12, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f), maxLines = 1)
                        T("${fmtNum(v)}  ·  ${(v * 100f / total).toInt().fa()}٪", 12, FontWeight.Medium, Color.White.copy(alpha = 0.9f), maxLines = 1)
                    }
                }
            }
        } else {
            T(emptyNote, 12, color = Color.White.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun MonthBlock(app: AppState, g: HistoryMonth, unit: String) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            T(fmtMonth(g.y, g.m), 17, FontWeight.Bold, modifier = Modifier.weight(1f))
            T("${fmtNum(g.total)} $unit", 13, FontWeight.Bold, c.ink2, maxLines = 1)
        }
        GCard {
            g.entries.forEachIndexed { i, e ->
                if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                HistoryRow(e) {
                    if (e.kind == Kind.EXPENSE) app.expenseEditId = e.itemId else app.detailId = e.itemId
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(e: HistoryEntry, onClick: () -> Unit) {
    val c = G.c
    val day = Jalali.toJalali(e.date).d
    val sub = listOfNotNull(
        if (e.kind == Kind.EXPENSE) e.tag.ifBlank { "پرداخت" } else e.detail,
        e.destination?.let { destinationText(it) },
    ).joinToString(" · ")
    val head = if (e.kind == Kind.EXPENSE && e.payee.isNotBlank() && e.payee != e.title) "${e.title} · ${e.payee}" else e.title
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
            T(day.fa(), 16, FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            T(head, 15, FontWeight.SemiBold, maxLines = 1)
            if (sub.isNotBlank()) T(sub, 12, color = c.muted, maxLines = 1)
        }
        T(fmtNum(e.amount), 15, FontWeight.SemiBold, maxLines = 1)
    }
}
