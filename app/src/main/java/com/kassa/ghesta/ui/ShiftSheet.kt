package com.kassa.ghesta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.Reschedule
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.ui.theme.G
import java.time.LocalDate

private val quickMonths = listOf(1, 2, 3, 6, 12)

/**
 * "Shift the remaining installments": the bank postponed them by some months, or the due day moved.
 * Paid installments never change. A preview shows exactly what will happen before anything is applied.
 */
@Composable
fun ShiftSheet(app: AppState, item: Item, onDismiss: () -> Unit) {
    val unpaid = item.installments.filter { it.paidAt == null }.sortedBy { it.due }
    if (unpaid.isEmpty()) {
        onDismiss()
        return
    }
    GSheet(onDismiss) { close ->
        val c = G.c
        val today = Store.today
        var mode by remember { mutableIntStateOf(0) }        // 0 = by months, 1 = exact date
        var forward by remember { mutableIntStateOf(0) }     // 0 = later, 1 = earlier
        var months by remember { mutableStateOf("1") }
        val firstUnpaidNumber = item.installments.indexOfFirst { it.id == unpaid.first().id } + 1
        var from by remember { mutableStateOf(firstUnpaidNumber.toString()) }
        var newFirst by remember { mutableStateOf<LocalDate>(unpaid.first().due) }
        var pick by remember { mutableStateOf(false) }

        val fromIdx = ((from.toIntOrNull() ?: firstUnpaidNumber) - 1).coerceIn(0, item.installments.lastIndex)
        val moving = Reschedule.affected(item.installments, fromIdx)
        // the date picker must start from the first installment that will actually move
        val anchor = moving.firstOrNull()?.let { item.installments[it].due } ?: unpaid.first().due
        val n = months.toIntOrNull() ?: 0
        val after: List<Installment> = if (mode == 0) {
            Reschedule.shift(item.installments, fromIdx, if (forward == 0) n else -n)
        } else {
            Reschedule.rebase(item.installments, fromIdx, newFirst)
        }
        val changes = Reschedule.changes(item.installments, after)
        val newDue = after.associate { it.id to it.due }
        val nextOverdue = unpaid.count { (newDue[it.id] ?: it.due).isBefore(today) }

        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) { T("جابه‌جایی تاریخ اقساط", 20, FontWeight.Bold) }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T(
                "فقط قسط‌های پرداخت‌نشده‌ی «${item.title}» جابه‌جا می‌شوند؛ قسط‌های پرداخت‌شده بدون تغییر می‌مانند.",
                13, color = c.ink2, modifier = Modifier.padding(horizontal = 4.dp),
            )
            IntField(
                "از قسط شماره (به بعد)", from, { from = it }, trailing = "از ${item.installments.size.fa()}",
                max = item.installments.size,
                hint = "${moving.size.fa()} قسط جابه‌جا می‌شود. قسط‌های قبل از این شماره سر جایشان می‌مانند.",
            )
            Segmented(listOf("چند ماه", "تاریخ دقیق"), mode, { mode = it })

            if (mode == 0) {
                Segmented(listOf("عقب بیفتد (دیرتر)", "جلو بیفتد (زودتر)"), forward, { forward = it })
                IntField(
                    "چند ماه؟", months, { months = it }, trailing = "ماه", max = 120,
                    hint = "روز ماه هر قسط حفظ می‌شود؛ اگر در ماه مقصد وجود نداشت، آخرین روز آن ماه می‌آید.",
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickMonths) { m -> GChip("${m.fa()} ماه", months == m.toString(), { months = m.toString() }) }
                }
            } else {
                DateField(
                    "تاریخ جدید قسط شماره ${((moving.firstOrNull() ?: 0) + 1).fa()}",
                    newFirst, { pick = true },
                    hint = "قسط‌های بعدی با همان فاصله‌ی ماهانه و همین روز از ماه دنبالش می‌آیند.",
                )
            }

            // preview
            val movingItems = moving.map { item.installments[it] }
            val rows = (movingItems.take(3) + (if (movingItems.size > 4) listOf(movingItems.last()) else movingItems.drop(3))).distinctBy { it.id }
            GCard(bg = c.surface2, border = c.line, radius = 18) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("پیش‌نمایش", 12, FontWeight.Medium, c.muted)
                    if (changes.isEmpty()) {
                        T("با این انتخاب هیچ تاریخی تغییر نمی‌کند.", 13, color = c.ink2)
                    } else {
                        rows.forEachIndexed { i, x ->
                            val number = item.installments.indexOfFirst { it.id == x.id } + 1
                            val to = newDue[x.id] ?: x.due
                            if (i == 3 && movingItems.size > 4) T("…", 14, color = c.muted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                T("قسط ${number.fa()}", 13, color = c.ink2)
                                T("${fmtDate(x.due)}  ←  ${fmtDate(to)}", 13, FontWeight.Bold)
                            }
                        }
                        if (nextOverdue > 0) Pill("بعد از جابه‌جایی ${nextOverdue.fa()} قسط هنوز عقب‌افتاده است", Tone.Warn)
                        else Pill("هیچ قسطی عقب‌افتاده نمی‌ماند", Tone.Ok)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton(if (changes.isEmpty()) "تغییری نیست" else "اعمال روی ${changes.size.fa()} قسط", enabled = changes.isNotEmpty()) {
                val old = item.installments.filter { it.id in changes }.associate { it.id to it.due }
                Store.setInstallmentDates(item.id, changes)
                app.toast("تاریخ ${changes.size.fa()} قسط جابه‌جا شد", "بازگردانی") { Store.setInstallmentDates(item.id, old) }
                close()
            }
        }
        if (pick) JalaliPickerDialog(if (newFirst == unpaid.first().due) anchor else newFirst, "تاریخ جدید", { pick = false }) { newFirst = it; pick = false }
    }
}
