package com.kassa.ghesta.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings as SettingsIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.Sync
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.info
import com.kassa.ghesta.data.isObligation
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.theme.G
import com.kassa.ghesta.ui.theme.GhestaTheme
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class AppState(
    val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
    private val haptic: HapticFeedback,
    val requestNotif: () -> Unit,
) {
    var tab by mutableIntStateOf(0)
    var filter by mutableIntStateOf(0)
    var detailId by mutableStateOf<String?>(null)
    var showAdd by mutableStateOf(false)
    var payForId by mutableStateOf<String?>(null)

    /** Set after sign-up / recovery: the one-time recovery code that must be shown to the user. */
    var pendingRecovery by mutableStateOf<String?>(null)

    /** Set after sign-in when the phone already holds data: ask whether to merge it into the account. */
    var showDataChoice by mutableStateOf(false)

    fun toast(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    fun openList(filter: Int) {
        this.filter = filter
        tab = 1
    }

    /** Installment being paid / un-paid: a confirmation is shown first (itemId to installmentId). */
    var payConfirm by mutableStateOf<Pair<String, String>?>(null)
    var unpayConfirm by mutableStateOf<Pair<String, String>?>(null)

    /** Tapping the check of an installment never changes anything directly: it asks first. */
    fun togglePaid(itemId: String, instId: String) {
        val item = Store.item(itemId) ?: return
        val inst = item.installments.firstOrNull { it.id == instId } ?: return
        if (inst.paidAt == null) payConfirm = itemId to instId else unpayConfirm = itemId to instId
    }

    fun confirmPay(itemId: String, instId: String, date: LocalDate) {
        val item = Store.item(itemId) ?: return
        val n = item.installments.indexOfFirst { it.id == instId } + 1
        Store.setPaid(itemId, instId, true, date)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (Store.item(itemId)?.info(Store.today)?.done == true) {
            Celebrate.burst()
            toast("«${item.title}» کامل تسویه شد 🎉")
        } else {
            toast("قسط ${n.fa()} «${item.title}» پرداخت شد", "بازگردانی") { Store.setPaid(itemId, instId, false) }
        }
    }

    /** Takes a payment back (paid by mistake). The toast lets you redo it with the original date. */
    fun confirmUnpay(itemId: String, instId: String) {
        val item = Store.item(itemId) ?: return
        val inst = item.installments.firstOrNull { it.id == instId } ?: return
        val oldDate = inst.paidAt ?: return
        val n = item.installments.indexOf(inst) + 1
        Store.setPaid(itemId, instId, false)
        toast("پرداخت قسط ${n.fa()} «${item.title}» لغو شد", "برگردان") { Store.setPaid(itemId, instId, true, oldDate) }
    }

    fun payDue(due: Due) {
        if (due.item.kind == Kind.INST) togglePaid(due.item.id, due.key) else payForId = due.item.id
    }
}

@Composable
fun GhestaRoot() {
    GhestaTheme(Store.settings.theme) { AppContent() }
}

@Composable
private fun AppContent() {
    val c = G.c
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Store.onResume()
        Reminders.schedule(ctx)
    }
    val requestNotif: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            ctx.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    val app = remember { AppState(snack, scope, haptic, requestNotif) }

    // keep the account in step while the app is open
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            Sync.requestSync(0)
        }
    }

    BackHandler(enabled = app.detailId != null) { app.detailId = null }
    BackHandler(enabled = app.detailId == null && app.tab != 0) { app.tab = 0 }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barH = 66.dp
    val pad = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = barH + bottom + 36.dp)

    var shownId by remember { mutableStateOf<String?>(null) }
    if (app.detailId != null) shownId = app.detailId

    val overdueCount = Store.items.filter { it.isObligation }.sumOf { it.info(Store.today).overdue.size }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Crossfade(app.tab, label = "tabs") { tab ->
            when (tab) {
                0 -> HomeScreen(app, pad)
                1 -> ItemsScreen(app, pad)
                2 -> ScheduleScreen(app, pad)
                else -> SettingsScreen(app, pad)
            }
        }

        Box(Modifier.align(Alignment.BottomCenter)) {
            BottomBar(app, overdueCount, barH)
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).padding(bottom = barH + bottom + 14.dp, start = 16.dp, end = 16.dp),
        ) { data ->
            Snackbar(
                data,
                shape = RoundedCornerShape(16.dp),
                containerColor = if (c.dark) c.surface2 else c.ink,
                contentColor = if (c.dark) c.ink else c.bg,
                actionColor = c.gold2,
            )
        }

        AnimatedVisibility(
            visible = app.detailId != null,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            val item = Store.item(shownId)
            if (item != null) DetailScreen(app, item.id) else app.detailId = null
        }

        if (app.showAdd) AddSheet(app) { app.showAdd = false }
        app.payForId?.let { id ->
            val it = Store.item(id)
            if (it != null) PaymentSheet(app, it) { app.payForId = null } else app.payForId = null
        }

        app.payConfirm?.let { (itemId, instId) ->
            val item = Store.item(itemId)
            if (item != null) PayInstallmentSheet(app, item, instId) { app.payConfirm = null } else app.payConfirm = null
        }
        app.unpayConfirm?.let { (itemId, instId) ->
            val item = Store.item(itemId)
            val n = (item?.installments?.indexOfFirst { it.id == instId } ?: -1) + 1
            if (item == null || n == 0) app.unpayConfirm = null
            else ConfirmDialog(
                "لغو پرداخت قسط ${n.fa()}؟",
                "«${item.title}» · قسط ${n.fa()} دوباره به حالت پرداخت‌نشده برمی‌گردد و به مانده‌ی بدهی اضافه می‌شود.",
                "بله، لغو شود", { app.unpayConfirm = null },
            ) {
                app.unpayConfirm = null
                app.confirmUnpay(itemId, instId)
            }
        }
        app.pendingRecovery?.let { code ->
            RecoveryCodeDialog(code) {
                app.pendingRecovery = null
                if (!app.showDataChoice) Sync.requestSync(0)
            }
        }
        if (app.pendingRecovery == null && app.showDataChoice) DataChoiceDialog { app.showDataChoice = false }

        ConfettiOverlay()
    }
}

private data class TabDef(val label: String, val icon: ImageVector)

@Composable
private fun BottomBar(app: AppState, overdueCount: Int, barH: androidx.compose.ui.unit.Dp) {
    val c = G.c
    val tabs = listOf(
        TabDef("خانه", Icons.Filled.Home),
        TabDef("فهرست", Icons.AutoMirrored.Filled.List),
        TabDef("برنامه", Icons.Filled.DateRange),
        TabDef("تنظیمات", Icons.Filled.SettingsIcon),
    )
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .border(1.dp, c.line, androidx.compose.ui.graphics.RectangleShape)
                .navigationBarsPadding()
                .height(barH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(tabs[0], app.tab == 0, if (overdueCount > 0) overdueCount else null, Modifier.weight(1f)) { app.tab = 0 }
            TabItem(tabs[1], app.tab == 1, null, Modifier.weight(1f)) { app.tab = 1; app.filter = 0 }
            Box(Modifier.weight(1f))
            TabItem(tabs[2], app.tab == 2, null, Modifier.weight(1f)) { app.tab = 2 }
            TabItem(tabs[3], app.tab == 3, null, Modifier.weight(1f)) { app.tab = 3 }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-22).dp)
                .shadow(10.dp, CircleShape, ambientColor = c.gold, spotColor = c.gold)
                .size(60.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.gold2, c.gold)))
                .border(3.dp, c.bg, CircleShape)
                .clickable { app.showAdd = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, "افزودن", tint = Color(0xFF2A1F05), modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun TabItem(tab: TabDef, selected: Boolean, badge: Int?, modifier: Modifier, onClick: () -> Unit) {
    val c = G.c
    val color = if (selected) (if (c.dark) c.gold2 else c.teal) else c.muted
    Column(
        modifier.fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(tab.icon, tab.label, tint = color, modifier = Modifier.size(25.dp))
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 9.dp, y = (-5).dp)
                        .clip(CircleShape)
                        .background(c.danger)
                        .padding(horizontal = 5.dp),
                ) { T(badge.coerceAtMost(99).fa(), 10, FontWeight.Bold, Color.White) }
            }
        }
        T(tab.label, 11, if (selected) FontWeight.Bold else FontWeight.Medium, color)
    }
}
