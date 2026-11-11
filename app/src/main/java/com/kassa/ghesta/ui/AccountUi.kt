package com.kassa.ghesta.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kassa.ghesta.data.Account
import com.kassa.ghesta.data.ApiException
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.Sync
import com.kassa.ghesta.data.SyncState
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.ui.theme.G
import kotlinx.coroutines.launch

private val usernameRe = Regex("^[a-z0-9_.]{3,32}$")

// ====================================================================== settings card

@Composable
fun AccountCard(app: AppState) {
    val c = G.c
    val scope = rememberCoroutineScope()
    val session = Account.session
    var showAuth by remember { mutableStateOf(false) }
    var showChangePw by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("حساب کاربری")
        GCard {
            if (session == null) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T("حساب کاربری ندارید", 15, FontWeight.Bold)
                    T(
                        "الان همه‌چیز فقط روی همین گوشی ذخیره می‌شود. با ساخت حساب، اطلاعاتتان روی سرور هم نگه‌داری می‌شود و با عوض کردن گوشی یا پاک شدن برنامه از بین نمی‌رود.",
                        13, color = c.ink2,
                    )
                    PrimaryButton("ورود یا ساخت حساب") { showAuth = true }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                        T(session.username.take(1).uppercase(), 18, FontWeight.Bold, c.onPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            T(session.username, 16, FontWeight.Bold, maxLines = 1)
                        }
                        T(syncStatusText(), 12, FontWeight.Medium, if (Sync.state == SyncState.Error) c.danger else c.muted)
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth()) {
                    CardAction("همگام‌سازی الان", Modifier.weight(1f), c.gold) {
                        scope.launch {
                            val ok = Sync.syncNow()
                            app.toast(if (ok) "همگام شد" else Sync.lastError ?: "اتصال به سرور برقرار نشد")
                        }
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth()) {
                    CardAction("تغییر رمز", Modifier.weight(1f)) { showChangePw = true }
                    Box(Modifier.size(1.dp, 48.dp).background(c.line))
                    CardAction("خروج", Modifier.weight(1f)) { confirmLogout = true }
                    Box(Modifier.size(1.dp, 48.dp).background(c.line))
                    CardAction("حذف حساب", Modifier.weight(1f), c.danger) { showDelete = true }
                }
            }
        }
    }

    if (showAuth) AuthSheet(app) { showAuth = false }
    if (showChangePw) ChangePasswordSheet(app) { showChangePw = false }
    if (confirmLogout) {
        ConfirmDialog(
            "خروج از حساب؟", "اطلاعات روی همین گوشی می‌ماند، ولی دیگر با سرور همگام نمی‌شود تا دوباره وارد شوید.",
            "خروج", { confirmLogout = false },
        ) {
            confirmLogout = false
            scope.launch {
                Account.logout()
                app.toast("از حساب خارج شدید")
            }
        }
    }
    if (showDelete) DeleteAccountDialog(app) { showDelete = false }
}

@Composable
private fun CardAction(text: String, modifier: Modifier, color: androidx.compose.ui.graphics.Color = G.c.ink2, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        T(text, 13, FontWeight.Bold, color, maxLines = 1)
    }
}

private fun syncStatusText(): String {
    val pending = Store.pendingCount
    return when (Sync.state) {
        SyncState.Syncing -> "در حال همگام‌سازی…"
        SyncState.Offline -> "آفلاین" + if (pending > 0) "، ${pending.fa()} تغییر منتظر ارسال" else ""
        SyncState.Error -> Sync.lastError ?: "خطا در همگام‌سازی"
        SyncState.Idle -> when {
            pending > 0 -> "${pending.fa()} تغییر منتظر ارسال"
            Sync.lastSync > 0 -> "همگام شد، " + agoText(Sync.lastSync)
            else -> "آماده‌ی همگام‌سازی"
        }
    }
}

private fun agoText(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60_000
    return when {
        min < 1 -> "همین الان"
        min < 60 -> "${min.toInt().fa()} دقیقه پیش"
        min < 24 * 60 -> "${(min / 60).toInt().fa()} ساعت پیش"
        else -> "${(min / 1440).toInt().fa()} روز پیش"
    }
}

// ====================================================================== sign in / register / recover

@Composable
fun AuthSheet(app: AppState, onDismiss: () -> Unit) {
    GSheet(onDismiss) { close ->
        val c = G.c
        val scope = rememberCoroutineScope()
        var mode by remember { mutableIntStateOf(0) } // 0 login, 1 register, 2 recover
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var password2 by remember { mutableStateOf("") }
        var recovery by remember { mutableStateOf("") }
        var showPw by remember { mutableStateOf(false) }
        var server by remember { mutableStateOf(Account.serverUrl) }
        var showServer by remember { mutableStateOf(Account.serverUrl.isBlank()) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var tried by remember { mutableStateOf(false) }

        val u = username.trim().lowercase()
        val pwTransform: VisualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation()
        val userOk = usernameRe.matches(u)
        val passOk = password.length >= 8
        val matchOk = mode != 1 && mode != 2 || password == password2

        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) {
            T(if (mode == 2) "بازیابی با کد بازیابی" else "حساب کاربری", 20, FontWeight.Bold)
        }
        if (mode != 2) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Segmented(listOf("ورود", "ساخت حساب"), mode, { mode = it; error = null; tried = false })
            }
        }

        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GField(
                "نام کاربری", username, { username = it.filter { ch -> !ch.isWhitespace() }; error = null },
                placeholder = "مثلاً ali_01", keyboardType = KeyboardType.Ascii,
                error = tried && !userOk,
                hint = if (tried && !userOk) "۳ تا ۳۲ نویسه: حروف کوچک انگلیسی، عدد، «_» و «.»" else if (mode == 1) "حروف کوچک انگلیسی، عدد، _ و ." else null,
            )
            if (mode == 2) {
                GField(
                    "کد بازیابی", recovery, { recovery = it; error = null }, placeholder = "XXXXX-XXXXX-XXXXX-XXXXX",
                    keyboardType = KeyboardType.Ascii, error = tried && recovery.isBlank(),
                )
            }
            GField(
                if (mode == 2) "رمز عبور جدید" else "رمز عبور", password, { password = it; error = null },
                keyboardType = KeyboardType.Password, transformation = pwTransform,
                error = tried && (mode != 0 && !passOk || mode == 0 && password.isEmpty()),
                hint = if (tried && mode != 0 && !passOk) "رمز عبور باید حداقل ۸ نویسه باشد" else null,
                imeAction = if (mode == 0) ImeAction.Done else ImeAction.Next,
            )
            if (mode != 0) {
                GField(
                    "تکرار رمز عبور", password2, { password2 = it; error = null },
                    keyboardType = KeyboardType.Password, transformation = pwTransform,
                    error = tried && !matchOk, hint = if (tried && !matchOk) "دو رمز یکسان نیستند" else null,
                    imeAction = ImeAction.Done,
                )
            }
            T(
                if (showPw) "پنهان کردن رمز" else "نمایش رمز", 13, FontWeight.SemiBold, c.gold,
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { showPw = !showPw }.padding(horizontal = 4.dp, vertical = 4.dp),
            )

            if (mode == 0) {
                T(
                    "رمز را فراموش کرده‌ام", 13, FontWeight.SemiBold, c.ink2,
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { mode = 2; error = null; tried = false }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (mode == 2) {
                T(
                    "کد بازیابی را هنگام ساخت حساب به شما نشان دادیم. با آن می‌توانید رمز تازه بگذارید.", 12, color = c.muted,
                )
                T(
                    "بازگشت به ورود", 13, FontWeight.SemiBold, c.gold,
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { mode = 0; error = null; tried = false }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            if (mode == 1) {
                GCard(bg = c.gold.copy(alpha = 0.10f), border = c.gold.copy(alpha = 0.3f), radius = 16) {
                    T(
                        "بعد از ساخت حساب، یک «کد بازیابی» نشان داده می‌شود. اگر رمزتان را فراموش کنید فقط با همان کد می‌توانید دوباره وارد شوید.",
                        12, color = c.ink2, modifier = Modifier.padding(14.dp),
                    )
                }
            }

            // server address
            if (!showServer) {
                T(
                    "سرور: ${Account.serverUrl}  ·  تغییر", 12, color = c.muted,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { showServer = true }.padding(4.dp),
                )
            } else {
                GField(
                    "آدرس سرور", server, { server = it.trim(); error = null }, placeholder = "https://example.com",
                    keyboardType = KeyboardType.Uri, error = tried && server.isBlank(),
                    hint = "آدرس سرور قسطا؛ برای امنیت باید با https شروع شود.",
                )
            }

            if (error != null) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.dangerBg).padding(14.dp)) {
                    T(error!!, 13, FontWeight.Medium, c.danger)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp).navigationBarsPadding()) {
            PrimaryButton(
                when {
                    busy -> "لطفاً صبر کنید…"
                    mode == 0 -> "ورود"
                    mode == 1 -> "ساخت حساب"
                    else -> "تعیین رمز جدید و ورود"
                },
                enabled = !busy,
            ) {
                tried = true
                val ready = userOk && server.isNotBlank() && when (mode) {
                    0 -> password.isNotEmpty()
                    1 -> passOk && matchOk
                    else -> passOk && matchOk && recovery.isNotBlank()
                }
                if (!ready) return@PrimaryButton
                scope.launch {
                    busy = true
                    error = null
                    try {
                        Account.setServer(server)
                        when (mode) {
                            0 -> {
                                Account.login(u, password)
                                finishSignIn(app, null)
                            }
                            1 -> {
                                val code = Account.register(u, password)
                                Store.markAllPending() // whatever is on the phone becomes the account's data
                                finishSignIn(app, code, hasLocalChoice = false)
                            }
                            else -> {
                                val code = Account.recover(u, recovery, password)
                                finishSignIn(app, code)
                            }
                        }
                        close()
                    } catch (e: ApiException) {
                        error = e.message
                        if (e.code == "no_server" || e.code == "bad_url" || e.offline) showServer = true
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

/** After any successful sign-in: show the recovery code (if new), ask about phone data (if any), then sync. */
private fun finishSignIn(app: AppState, recoveryCode: String?, hasLocalChoice: Boolean = true) {
    app.pendingRecovery = recoveryCode
    if (hasLocalChoice && Store.items.isNotEmpty()) {
        Account.awaitingDataChoice = true
        app.showDataChoice = true
    } else if (recoveryCode == null) {
        Sync.requestSync(0)
    }
}

// ====================================================================== dialogs shown at app level

@Composable
fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val c = G.c
    val ctx = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = c.surface,
        shape = RoundedCornerShape(24.dp),
        title = { T("کد بازیابی شما", 18, FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("این کد فقط همین یک بار نشان داده می‌شود. آن را جای امن نگه دارید (یادداشت یا مدیر رمز). اگر رمز را فراموش کنید، فقط با همین کد می‌توانید دوباره وارد شوید.", 13, color = c.ink2)
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface2).padding(14.dp), contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        T(code, 15, FontWeight.Bold, c.gold, align = TextAlign.Center, maxLines = 1)
                    }
                }
                T(
                    if (copied) "کپی شد ✓" else "کپی کردن کد", 14, FontWeight.Bold, c.primary,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("recovery", code))
                        copied = true
                    }.padding(10.dp),
                    align = TextAlign.Center,
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { saved = !saved }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CheckCircle(saved, { saved = !saved }, 28.dp)
                    T("کد را در جای امن ذخیره کردم", 13, FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            T(
                "ادامه", 15, FontWeight.Bold, if (saved) c.primary else c.muted,
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = saved, onClick = onDone).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}

/** Shown after signing in while the phone already holds data. */
@Composable
fun DataChoiceDialog(onDone: () -> Unit) {
    val c = G.c
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = c.surface,
        shape = RoundedCornerShape(24.dp),
        title = { T("اطلاعات روی این گوشی", 18, FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("روی این گوشی ${Store.items.size.fa()} مورد ثبت‌شده دارید. با آن‌ها چه کنیم؟", 14, color = c.ink2)
                T("«ادغام» اطلاعات گوشی را به حساب اضافه می‌کند و با اطلاعات حساب یکی می‌کند. اگر یک مورد در هر دو جا بود، نسخه‌ی جدیدتر می‌ماند.", 12, color = c.muted)
            }
        },
        confirmButton = {
            T(
                "ادغام با حساب", 15, FontWeight.Bold, c.primary,
                Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    Store.markAllPending()
                    Account.awaitingDataChoice = false
                    Sync.requestSync(0)
                    onDone()
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
        dismissButton = {
            T(
                "فقط اطلاعات حساب", 14, FontWeight.SemiBold, c.danger,
                Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                    Store.discardLocalData()
                    Account.awaitingDataChoice = false
                    Sync.requestSync(0)
                    onDone()
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
    )
}

// ====================================================================== account management

@Composable
private fun ChangePasswordSheet(app: AppState, onDismiss: () -> Unit) {
    GSheet(onDismiss) { close ->
        val scope = rememberCoroutineScope()
        var old by remember { mutableStateOf("") }
        var new1 by remember { mutableStateOf("") }
        var new2 by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var tried by remember { mutableStateOf(false) }
        val pw = PasswordVisualTransformation()

        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)) { T("تغییر رمز عبور", 20, FontWeight.Bold) }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GField("رمز فعلی", old, { old = it; error = null }, keyboardType = KeyboardType.Password, transformation = pw, error = tried && old.isEmpty())
            GField(
                "رمز جدید", new1, { new1 = it; error = null }, keyboardType = KeyboardType.Password, transformation = pw,
                error = tried && new1.length < 8, hint = if (tried && new1.length < 8) "حداقل ۸ نویسه" else null,
            )
            GField(
                "تکرار رمز جدید", new2, { new2 = it; error = null }, keyboardType = KeyboardType.Password, transformation = pw,
                error = tried && new1 != new2, hint = if (tried && new1 != new2) "دو رمز یکسان نیستند" else null, imeAction = ImeAction.Done,
            )
            T("بعد از تغییر رمز، در دستگاه‌های دیگر دوباره باید وارد شوید.", 12, color = G.c.muted)
            if (error != null) T(error!!, 13, FontWeight.Medium, G.c.danger)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp, bottom = 14.dp).navigationBarsPadding()) {
            PrimaryButton(if (busy) "لطفاً صبر کنید…" else "تغییر رمز", enabled = !busy) {
                tried = true
                if (old.isEmpty() || new1.length < 8 || new1 != new2) return@PrimaryButton
                scope.launch {
                    busy = true
                    try {
                        Account.changePassword(old, new1)
                        app.toast("رمز عبور تغییر کرد")
                        close()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(app: AppState, onDismiss: () -> Unit) {
    val c = G.c
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = c.surface,
        shape = RoundedCornerShape(24.dp),
        title = { T("حذف حساب؟", 18, FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("حساب و همه‌ی اطلاعات ذخیره‌شده روی سرور برای همیشه پاک می‌شود. اطلاعات روی همین گوشی می‌ماند.", 13, color = c.ink2)
                GField("رمز عبور", pw, { pw = it; error = null }, keyboardType = KeyboardType.Password, transformation = PasswordVisualTransformation(), imeAction = ImeAction.Done)
                if (error != null) T(error!!, 12, FontWeight.Medium, c.danger)
            }
        },
        confirmButton = {
            T(
                if (busy) "…" else "حذف حساب", 15, FontWeight.Bold, c.danger,
                Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy && pw.isNotEmpty()) {
                    scope.launch {
                        busy = true
                        try {
                            Account.deleteAccount(pw)
                            app.toast("حساب حذف شد")
                            onDismiss()
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                }.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        },
        dismissButton = {
            T("انصراف", 15, FontWeight.SemiBold, c.ink2, Modifier.clip(RoundedCornerShape(12.dp)).clickable { if (!busy) onDismiss() }.padding(horizontal = 14.dp, vertical = 10.dp))
        },
    )
}
