package com.kassa.ghesta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Installment
import com.kassa.ghesta.data.Item
import com.kassa.ghesta.data.ItemState
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.relLabel
import com.kassa.ghesta.ui.theme.G

@Composable
fun DetailScreen(app: AppState, itemId: String) {
    val item = Store.item(itemId) ?: return
    val c = G.c
    val today = Store.today
    val info = item.info(today)
    val unit = Store.settings.unit
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var showPaid by remember(itemId) { mutableStateOf(false) }
    var editItem by remember(itemId) { mutableStateOf(false) }
    var editInst by remember(itemId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(itemId) { mutableStateOf(false) }
    var confirmRemovePayment by remember(itemId) { mutableStateOf<String?>(null) }

    val unpaid = item.installments.withIndex().filter { it.value.paidAt == null }
    val paid = item.installments.withIndex().filter { it.value.paidAt != null }
    val showPayButton = item.kind == Kind.DEBT && !info.done

    Box(
        Modifier.fillMaxSize().background(c.bg).pointerInput(Unit) { detectTapGestures { } },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = bottom + if (showPayButton) 100.dp else 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") { app.detailId = null }
                    Box(Modifier.weight(1f))
                    RoundIcon(Icons.Filled.Edit, "ویرایش") { editItem = true }
                    RoundIcon(Icons.Filled.Delete, "حذف", tint = c.danger) { confirmDelete = true }
                }
            }

            item { DetailHero(item, info, unit) }

            if (item.note.isNotBlank()) {
                item {
                    GCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            T("یادداشت", 12, FontWeight.Medium, c.muted)
                            T(item.note, 14, color = c.ink2)
                        }
                    }
                }
            }

            if (item.kind == Kind.INST) {
                if (unpaid.isNotEmpty()) {
                    item { SectionTitle("قسط‌های باقی‌مانده (${unpaid.size.fa()})") }
                    item {
                        GCard {
                            unpaid.forEachIndexed { i, (idx, inst) ->
                                if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                                InstRow(idx + 1, inst, today, unit, { app.togglePaid(item.id, inst.id) }, { editInst = inst.id })
                            }
                        }
                    }
                } else {
                    item {
                        GCard(bg = c.okBg, border = c.ok.copy(alpha = 0.25f)) {
                            T("همه‌ی قسط‌ها پرداخت شده است. تبریک!", 15, FontWeight.Bold, c.ok, Modifier.fillMaxWidth().padding(18.dp), align = TextAlign.Center)
                        }
                    }
                }
                if (paid.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showPaid = !showPaid }.padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            T("پرداخت‌شده‌ها (${paid.size.fa()})", 16, FontWeight.Bold)
                            T(if (showPaid) "بستن" else "نمایش", 13, FontWeight.SemiBold, c.gold)
                        }
                    }
                    if (showPaid) {
                        item {
                            GCard {
                                paid.forEachIndexed { i, (idx, inst) ->
                                    if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                                    InstRow(idx + 1, inst, today, unit, { app.togglePaid(item.id, inst.id) }, { editInst = inst.id })
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    DebtDueCard(item, info, today)
                }
                if (item.payments.isNotEmpty()) {
                    item { SectionTitle("پرداخت‌های انجام‌شده (${item.payments.size.fa()})") }
                    item {
                        GCard {
                            item.payments.sortedByDescending { it.date }.forEachIndexed { i, p ->
                                if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        T(fmtDate(p.date, weekday = true), 14, FontWeight.SemiBold)
                                        T("${fmtNum(p.amount)} $unit", 12, color = c.ink2)
                                    }
                                    T(
                                        "حذف", 13, FontWeight.SemiBold, c.danger,
                                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { confirmRemovePayment = p.id }.padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showPayButton) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, c.bg, c.bg)))
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = bottom + 16.dp),
            ) {
                PrimaryButton(if (item.dir == Dir.OWE) "ثبت پرداخت" else "ثبت دریافت") { app.payForId = item.id }
            }
        }
    }

    if (editItem) EditItemSheet(item) { editItem = false }
    editInst?.let { id -> InstallmentSheet(item, id) { editInst = null } }
    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف «${item.title}»؟",
            text = "همه‌ی اقساط و سوابق پرداخت این مورد پاک می‌شود و قابل بازگشت نیست.",
            confirm = "حذف کن",
            onDismiss = { confirmDelete = false },
        ) {
            confirmDelete = false
            app.detailId = null
            Store.delete(item.id)
            app.toast("«${item.title}» حذف شد")
        }
    }
    confirmRemovePayment?.let { pid ->
        ConfirmDialog(
            title = "حذف این پرداخت؟",
            text = "مبلغ آن دوباره به مانده‌ی بدهی اضافه می‌شود.",
            confirm = "حذف کن",
            onDismiss = { confirmRemovePayment = null },
        ) {
            confirmRemovePayment = null
            Store.removePayment(item.id, pid)
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color = G.c.ink, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(G.c.surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun DetailHero(item: Item, info: com.kassa.ghesta.data.Info, unit: String) {
    val c = G.c
    Column(
        Modifier.fillMaxWidth().heroBackground(c).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                T(item.title, 22, FontWeight.Bold, Color.White)
                if (item.party.isNotBlank()) T(item.party, 13, color = Color.White.copy(alpha = 0.7f))
            }
            when (info.state) {
                ItemState.DONE -> HeroBadge("تسویه شد", Color(0xFF52CBA0))
                ItemState.OVERDUE -> HeroBadge("عقب‌افتاده", Color(0xFFFF8A96))
                else -> if (item.kind == Kind.DEBT) HeroBadge(if (item.dir == Dir.OWE) "بدهی من" else "طلب من", c.gold2)
            }
        }
        Column {
            T("مانده", 12, FontWeight.Medium, Color.White.copy(alpha = 0.65f))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T(fmtNum(info.remaining), 32, FontWeight.Bold, Color.White, maxLines = 1)
                T(unit, 13, FontWeight.Medium, c.gold2, Modifier.padding(bottom = 7.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GProgress(info.progress, color = c.gold2, height = 8.dp, track = Color.White.copy(alpha = 0.14f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T(
                    if (item.kind == Kind.INST) "${info.paidCount.fa()} از ${info.count.fa()} قسط پرداخت شده"
                    else "${(info.progress * 100).toInt().fa()}٪ پرداخت شده",
                    12, FontWeight.Medium, Color.White.copy(alpha = 0.8f),
                )
                T("کل: ${fmtNum(info.total)}", 12, FontWeight.Medium, Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun HeroBadge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        T(text, 12, FontWeight.Bold, color)
    }
}

@Composable
private fun InstRow(n: Int, inst: Installment, today: java.time.LocalDate, unit: String, onToggle: () -> Unit, onEdit: () -> Unit) {
    val c = G.c
    val paid = inst.paidAt != null
    val diff = daysBetween(today, inst.due)
    val overdue = !paid && diff < 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (paid) c.okBg else if (overdue) c.dangerBg else c.surface2),
            contentAlignment = Alignment.Center,
        ) { T(n.fa(), 14, FontWeight.Bold, if (paid) c.ok else if (overdue) c.danger else c.ink2) }
        Column(Modifier.weight(1f)) {
            T(fmtDate(inst.due, weekday = false), 15, FontWeight.SemiBold, if (paid) c.muted else c.ink)
            if (paid) T("پرداخت شد در ${fmtDate(inst.paidAt!!, year = true)}", 12, color = c.ok)
            else T(relLabel(diff), 12, FontWeight.Medium, toneFg(relTone(diff)))
        }
        Column(horizontalAlignment = Alignment.End) {
            T(fmtNum(inst.amount), 15, FontWeight.SemiBold, if (paid) c.muted else c.ink, maxLines = 1)
            T(unit, 10, color = c.muted)
        }
        CheckCircle(paid, onToggle, 36.dp, if (paid) "برگرداندن" else "پرداخت شد")
    }
}

@Composable
private fun DebtDueCard(item: Item, info: com.kassa.ghesta.data.Info, today: java.time.LocalDate) {
    val c = G.c
    GCard {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("سررسید", 12, FontWeight.Medium, c.muted)
                T(item.due?.let { fmtDate(it, weekday = true) } ?: "تعیین نشده", 16, FontWeight.Bold)
            }
            val d = item.due
            if (d != null && !info.done) {
                val diff = daysBetween(today, d)
                Pill(relLabel(diff), if (diff < 0) Tone.Danger else if (diff <= 3) Tone.Warn else Tone.Neutral)
            }
        }
    }
}
