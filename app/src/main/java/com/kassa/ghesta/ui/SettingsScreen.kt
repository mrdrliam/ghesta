package com.kassa.ghesta.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtDateShort
import com.kassa.ghesta.notify.Reminders
import com.kassa.ghesta.ui.theme.G

private val dayOptions = listOf(7 to "۷ روز قبل", 3 to "۳ روز قبل", 2 to "۲ روز قبل", 1 to "۱ روز قبل", 0 to "روز سررسید")
private val hourOptions = listOf(7, 8, 9, 10, 12, 14, 17, 18, 20, 21)

@Composable
fun SettingsScreen(app: AppState, pad: PaddingValues) {
    val c = G.c
    val ctx = LocalContext.current
    val s = Store.settings
    val granted = Store.resumeTick >= 0 && Reminders.canNotify(ctx)

    var confirmClear by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray(Charsets.UTF_8)) }
                app.toast("نسخه‌ی پشتیبان ذخیره شد")
            } catch (_: Exception) {
                app.toast("ذخیره‌ی فایل انجام نشد")
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                pendingImport = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (_: Exception) {
                app.toast("خواندن فایل انجام نشد")
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { T("تنظیمات", 24, FontWeight.Bold) }

        item { AccountCard(app) }

        // ---------------------------------------------------------------- reminders
        item {
            SettingsCard("یادآوری‌ها") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                        tint = if (granted) c.ok else c.warn, modifier = Modifier.size(26.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        T(if (granted) "اجازه‌ی اعلان داده شده است" else "اجازه‌ی اعلان داده نشده", 14, FontWeight.Bold)
                        T(
                            if (granted) "یادآوری‌ها حتی وقتی برنامه بسته است می‌رسند."
                            else "بدون اجازه‌ی اعلان، یادآوری نمی‌رسد.",
                            12, color = c.muted,
                        )
                    }
                    if (!granted) {
                        T(
                            "فعال‌سازی", 13, FontWeight.Bold, c.onPrimary,
                            Modifier.clip(RoundedCornerShape(12.dp)).background(c.primary).clickable { app.requestNotif() }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("یادآوری روزانه", 15, FontWeight.SemiBold)
                        T("موارد عقب‌افتاده هر روز تا پرداخت یادآوری می‌شوند", 12, color = c.muted)
                    }
                    Switch(
                        checked = s.notify,
                        onCheckedChange = { on -> Store.updateSettings { it.copy(notify = on) }; if (on && !granted) app.requestNotif() },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = c.ok, checkedThumbColor = Color.White, checkedBorderColor = c.ok,
                            uncheckedTrackColor = c.surface2, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.line,
                        ),
                    )
                }
                if (s.notify) {
                    Divider()
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        T("چند روز قبل از سررسید خبرم کن؟", 13, FontWeight.SemiBold, c.ink2)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(dayOptions) { (d, label) ->
                                GChip(label, d in s.remindDays, {
                                    Store.updateSettings { st ->
                                        val next = if (d in st.remindDays) st.remindDays - d else st.remindDays + d
                                        if (next.isEmpty()) st else st.copy(remindDays = next)
                                    }
                                })
                            }
                        }
                        T("ساعت یادآوری", 13, FontWeight.SemiBold, c.ink2, Modifier.padding(top = 6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(hourOptions) { h ->
                                GChip("${h.fa()}:۰۰", h == s.remindHour, { Store.updateSettings { it.copy(remindHour = h) } })
                            }
                        }
                    }
                    Divider()
                    T(
                        "ارسال اعلان آزمایشی", 14, FontWeight.Bold, c.gold,
                        Modifier.fillMaxWidth().clickable {
                            if (!granted) app.requestNotif() else Reminders.fire(ctx, force = true)
                        }.padding(16.dp),
                        align = TextAlign.Center,
                    )
                }
            }
        }

        item {
            GCard(bg = c.gold.copy(alpha = 0.10f), border = c.gold.copy(alpha = 0.3f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("اگر یادآوری دیر می‌رسد یا نمی‌رسد", 14, FontWeight.Bold)
                    T(
                        "بعضی گوشی‌ها (شیائومی، سامسونگ، هوآوی…) برنامه‌های بسته را برای صرفه‌جویی در باتری متوقف می‌کنند. برنامه را از «بهینه‌سازی باتری» مستثنی کنید.",
                        12, color = c.ink2,
                    )
                    T(
                        "باز کردن تنظیمات باتری", 13, FontWeight.Bold, c.gold,
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                            try {
                                ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Exception) {
                            }
                        }.padding(vertical = 6.dp),
                    )
                }
            }
        }

        // ---------------------------------------------------------------- appearance
        item {
            SettingsCard("ظاهر") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        T("پوسته", 13, FontWeight.SemiBold, c.ink2)
                        Segmented(listOf("خودکار", "روشن", "تیره"), s.theme, { v -> Store.updateSettings { it.copy(theme = v) } })
                    }
                }
            }
        }

        // ---------------------------------------------------------------- data
        item {
            SettingsCard("اطلاعات") {
                ActionRow("ذخیره‌ی نسخه‌ی پشتیبان", "یک فایل از همه‌ی اطلاعات بگیرید") {
                    export.launch("ghesta-backup-${fmtDateShort(Store.today).replace('/', '-')}.json")
                }
                Divider(Modifier.padding(horizontal = 16.dp))
                ActionRow("بازیابی از نسخه‌ی پشتیبان", "اطلاعات فعلی با فایل جایگزین می‌شود") {
                    import.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }
                Divider(Modifier.padding(horizontal = 16.dp))
                ActionRow("حذف همه‌ی اطلاعات", "اقساط، بدهی‌ها و سوابق پاک می‌شود", danger = true) { confirmClear = true }
            }
        }

        item {
            T(
                "قسطا · نسخه‌ی ۱٫۰\nاطلاعات شما فقط روی همین گوشی ذخیره می‌شود و جایی ارسال نمی‌شود.",
                12, color = c.muted, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
    }

    if (confirmClear) {
        ConfirmDialog("حذف همه‌ی اطلاعات؟", "همه‌ی اقساط، بدهی‌ها و سوابق پرداخت پاک می‌شود و قابل بازگشت نیست." + (if (com.kassa.ghesta.data.Account.signedIn) " چون وارد حساب هستید، از روی سرور هم پاک می‌شود." else "") + " پیشنهاد می‌کنیم قبلش پشتیبان بگیرید.", "حذف همه", { confirmClear = false }) {
            confirmClear = false
            Store.clearAll()
            app.toast("همه‌ی اطلاعات حذف شد")
        }
    }
    pendingImport?.let { text ->
        ConfirmDialog("جایگزینی اطلاعات؟", "اطلاعات فعلی حذف و با محتوای فایل جایگزین می‌شود.", "جایگزین کن", { pendingImport = null }) {
            pendingImport = null
            if (Store.importJson(text)) app.toast("اطلاعات بازیابی شد") else app.toast("فایل معتبر نبود؛ چیزی تغییر نکرد")
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)
        GCard(content = content)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = G.c
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)) {
        T(title, 15, FontWeight.SemiBold, if (danger) c.danger else c.ink)
        T(subtitle, 12, color = c.muted)
    }
}
