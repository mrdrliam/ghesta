package com.kassa.ghesta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kassa.ghesta.data.Jalali
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.ui.theme.G
import java.time.LocalDate

private enum class PickMode { Days, Months, Years }

private val weekHeads = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** Persian (Jalali) date picker. Tap a day to choose it. */
@Composable
fun JalaliPickerDialog(
    initial: LocalDate?,
    title: String = "انتخاب تاریخ",
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val c = G.c
    val today = Store.today
    val startJ = Jalali.toJalali(initial ?: today)
    var vy by remember { mutableIntStateOf(startJ.y) }
    var vm by remember { mutableIntStateOf(startJ.m) }
    var mode by remember { mutableStateOf(PickMode.Days) }
    var yearPage by remember { mutableIntStateOf(startJ.y - startJ.y % 12) }

    fun prevMonth() { if (vm == 1) { vm = 12; vy-- } else vm-- }
    fun nextMonth() { if (vm == 12) { vm = 1; vy++ } else vm++ }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            T(title, 13, FontWeight.Medium, c.muted, Modifier.fillMaxWidth(), align = TextAlign.Center)

            // header: prev | title | next
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NavArrow(next = false) {
                    when (mode) {
                        PickMode.Days -> prevMonth()
                        PickMode.Months -> vy--
                        PickMode.Years -> yearPage -= 12
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            mode = when (mode) {
                                PickMode.Days -> PickMode.Months
                                PickMode.Months -> { yearPage = vy - vy % 12; PickMode.Years }
                                PickMode.Years -> PickMode.Days
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    T(
                        when (mode) {
                            PickMode.Days -> "${Jalali.monthNames[vm - 1]} ${vy.fa()}"
                            PickMode.Months -> vy.fa()
                            PickMode.Years -> "${yearPage.fa()} – ${(yearPage + 11).fa()}"
                        },
                        17, FontWeight.Bold,
                    )
                }
                NavArrow(next = true) {
                    when (mode) {
                        PickMode.Days -> nextMonth()
                        PickMode.Months -> vy++
                        PickMode.Years -> yearPage += 12
                    }
                }
            }

            when (mode) {
                PickMode.Days -> DaysGrid(vy, vm, initial, today) { onPick(it) }
                PickMode.Months -> Grid(
                    items = Jalali.monthNames.mapIndexed { i, n -> n to (i + 1) },
                    selected = if (vy == startJ.y) startJ.m else -1,
                    current = if (vy == Jalali.toJalali(today).y) Jalali.toJalali(today).m else -1,
                ) { vm = it; mode = PickMode.Days }
                PickMode.Years -> Grid(
                    items = (0 until 12).map { (yearPage + it).fa() to (yearPage + it) },
                    selected = startJ.y,
                    current = Jalali.toJalali(today).y,
                ) { vy = it; mode = PickMode.Months }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("امروز", { onPick(today) }, Modifier.weight(1f), color = c.gold)
                GhostButton("انصراف", onDismiss, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavArrow(next: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(G.c.surface2).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // RTL: "previous" points to the right, "next" to the left.
        Icon(
            if (next) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            if (next) "بعدی" else "قبلی",
            tint = G.c.ink2,
        )
    }
}

@Composable
private fun DaysGrid(vy: Int, vm: Int, selected: LocalDate?, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = G.c
    val len = Jalali.monthLength(vy, vm)
    val first = Jalali.toGregorian(vy, vm, 1)
    val offset = Jalali.weekColumn(first)
    val rows = 6 // fixed height so the dialog does not jump between months
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            weekHeads.forEachIndexed { i, h ->
                Box(Modifier.weight(1f).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                    T(h, 12, FontWeight.SemiBold, if (i == 6) c.danger.copy(alpha = 0.8f) else c.muted)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val day = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..len) {
                            val date = Jalali.toGregorian(vy, vm, day)
                            val sel = date == selected
                            val isToday = date == today
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(if (sel) c.primary else Color.Transparent)
                                    .then(if (isToday && !sel) Modifier.border(1.5.dp, c.gold, CircleShape) else Modifier)
                                    .clickable { onPick(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                T(
                                    day.fa(), 15, if (sel || isToday) FontWeight.Bold else FontWeight.Medium,
                                    if (sel) c.onPrimary else if (col == 6) c.danger.copy(alpha = 0.85f) else c.ink,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Grid(items: List<Pair<String, Int>>, selected: Int, current: Int, onPick: (Int) -> Unit) {
    val c = G.c
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    val sel = value == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) c.primary else c.surface2)
                            .then(if (value == current && !sel) Modifier.border(1.5.dp, c.gold, RoundedCornerShape(14.dp)) else Modifier)
                            .clickable { onPick(value) },
                        contentAlignment = Alignment.Center,
                    ) { T(label, 14, FontWeight.SemiBold, if (sel) c.onPrimary else c.ink) }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
