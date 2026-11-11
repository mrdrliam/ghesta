package com.kassa.ghesta.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Stats
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.computeStats
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDate
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.theme.G
import java.time.LocalTime

@Composable
fun HomeScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val ctx = LocalContext.current
    val today = Store.today
    val items = Store.items
    val unit = Store.settings.unit
    val st = remember(items, today) { computeStats(items, today) }
    // re-evaluated on every resume so the banner disappears after granting permission
    val notifOff = Store.resumeTick >= 0 && Store.settings.notify && !Reminders.canNotify(ctx)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = pad,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Header(app) }

        if (items.isEmpty()) {
            item { EmptyState(app) }
            return@LazyColumn
        }

        item { Hero(st, unit) }

        if (notifOff) item { NotifBanner(app) }

        if (st.overdue.isNotEmpty()) {
            item { OverdueCard(app, st, today, unit) }
        } else {
            item { AllGoodCard() }
        }

        if (st.upcoming.isNotEmpty()) {
            item { SectionTitle("پرداخت‌های پیش رو", action = "برنامه‌ی کامل") { app.tab = 2 } }
            item {
                GCard {
                    st.upcoming.take(5).forEachIndexed { i, d ->
                        if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
                        DueRow(d, today, { app.detailId = d.item.id }, { app.payDue(d) })
                    }
                }
            }
        }

        if (st.recvCount > 0) item { ReceivablesCard(app, st, unit) }
    }
}

@Composable
private fun Header(app: AppState) {
    val c = G.c
    val today = Store.today
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> "شب بخیر"
        hour < 12 -> "صبح بخیر"
        hour < 16 -> "ظهر بخیر"
        hour < 19 -> "عصر بخیر"
        else -> "شب بخیر"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(greeting, 14, FontWeight.Medium, c.muted)
            T(fmtDate(today, weekday = true), 22, FontWeight.Bold)
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c.teal2, c.tealDeep))),
            contentAlignment = Alignment.Center,
        ) { BrandMark(26) }
    }
}

@Composable
fun BrandMark(sizeDp: Int) {
    val c = G.c
    Canvas(Modifier.size(sizeDp.dp)) {
        val s = size.minDimension
        val w = s * 0.11f
        drawArc(
            Brush.linearGradient(listOf(c.gold2, c.gold)), -90f, 270f, false,
            topLeft = Offset(w, w), size = Size(s - 2 * w, s - 2 * w), style = Stroke(w, cap = StrokeCap.Round),
        )
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(s * 0.32f, s * 0.52f); lineTo(s * 0.45f, s * 0.65f); lineTo(s * 0.70f, s * 0.36f)
        }
        drawPath(p, c.gold2, style = Stroke(w, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun Hero(st: Stats, unit: String) {
    val c = G.c
    Column(
        Modifier.fillMaxWidth().heroBackground(c).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                T("مانده‌ی کل بدهی‌ها", 13, FontWeight.Medium, Color.White.copy(alpha = 0.7f))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(fmtNum(st.remaining), 32, FontWeight.Bold, Color.White, maxLines = 1)
                    T(unit, 13, FontWeight.Medium, c.gold2, Modifier.padding(bottom = 7.dp))
                }
            }
            Ring(st.progress, 72.dp, 7.dp, Color.White.copy(alpha = 0.14f), c.gold2) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("${(st.progress * 100).toInt().fa()}٪", 15, FontWeight.Bold, Color.White)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroTile("این ماه", st.monthAmount, unit, Modifier.weight(1f))
            HeroTile("۳۰ روز آینده", st.next30Amount, unit, Modifier.weight(1f))
            HeroTile("عقب‌افتاده", st.overdueAmount, unit, Modifier.weight(1f), danger = st.overdueAmount > 0)
        }
    }
}

@Composable
private fun HeroTile(label: String, amount: Long, unit: String, modifier: Modifier, danger: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (danger) Color(0xFFFF6B7A).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.09f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        T(label, 11, FontWeight.Medium, if (danger) Color(0xFFFFB3BB) else Color.White.copy(alpha = 0.65f), maxLines = 1)
        T(fmtNum(amount), 14, FontWeight.Bold, if (danger) Color(0xFFFFD0D5) else Color.White, maxLines = 1)
        T(unit, 10, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun OverdueCard(app: AppState, st: Stats, today: java.time.LocalDate, unit: String) {
    val c = G.c
    GCard(border = c.danger.copy(alpha = 0.35f)) {
        Row(
            Modifier.fillMaxWidth().background(c.dangerBg).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(c.danger.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Warning, null, tint = c.danger, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                T("عقب‌افتاده‌ها", 16, FontWeight.Bold, c.danger)
                T("${st.overdue.size.fa()} مورد · جمعاً ${fmtNum(st.overdueAmount)} $unit", 12, FontWeight.Medium, c.ink2)
            }
        }
        st.overdue.take(4).forEachIndexed { i, d ->
            if (i > 0) Divider(Modifier.padding(horizontal = 14.dp))
            DueRow(d, today, { app.detailId = d.item.id }, { app.payDue(d) })
        }
        if (st.overdue.size > 4) {
            Divider()
            T(
                "مشاهده‌ی همه (${st.overdue.size.fa()} مورد)", 14, FontWeight.Bold, c.danger,
                Modifier.fillMaxWidth().clickable { app.openList(1) }.padding(14.dp), align = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AllGoodCard() {
    val c = G.c
    GCard(bg = c.okBg, border = c.ok.copy(alpha = 0.25f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = c.ok, modifier = Modifier.size(30.dp))
            Column {
                T("همه‌چیز به‌روز است", 15, FontWeight.Bold, c.ok)
                T("هیچ قسط یا بدهی عقب‌افتاده‌ای ندارید.", 12, color = c.ink2)
            }
        }
    }
}

@Composable
private fun NotifBanner(app: AppState) {
    val c = G.c
    GCard(bg = c.gold.copy(alpha = 0.12f), border = c.gold.copy(alpha = 0.35f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Notifications, null, tint = c.gold, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                T("یادآوری‌ها خاموش است", 15, FontWeight.Bold)
                T("اجازه‌ی اعلان بدهید تا سررسیدها را از دست ندهید.", 12, color = c.ink2)
            }
            T(
                "فعال‌سازی", 14, FontWeight.Bold, c.onPrimary,
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.primary).clickable { app.requestNotif() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ReceivablesCard(app: AppState, st: Stats, unit: String) {
    val c = G.c
    GCard(onClick = { app.openList(3) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                T("طلب‌های من", 13, FontWeight.Medium, c.muted)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    T(fmtNum(st.recvRemaining), 20, FontWeight.Bold)
                    T(unit, 12, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Pill("${st.recvCount.fa()} مورد", Tone.Gold)
                if (st.recvOverdue > 0) {
                    Spacer(Modifier.height(4.dp))
                    Pill("${st.recvOverdue.fa()} موعد گذشته", Tone.Warn)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(app: AppState) {
    val c = G.c
    Column(
        Modifier.fillMaxWidth().heroBackground(c).padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandMark(86)
        T("به قسطا خوش آمدید", 22, FontWeight.Bold, Color.White, align = TextAlign.Center)
        T(
            "اقساط، وام‌ها و بدهی‌هایتان را یک‌جا ثبت کنید؛ ما سررسیدها را یادآوری می‌کنیم و می‌گوییم کدام عقب افتاده، کدام پرداخت شده و چند قسط مانده.",
            14, color = Color.White.copy(alpha = 0.78f), align = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(c.gold2, c.gold)))
                .clickable { app.showAdd = true }
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) { T("ثبت اولین مورد", 16, FontWeight.Bold, Color(0xFF2A1F05)) }
        Spacer(Modifier.width(0.dp))
    }
}
