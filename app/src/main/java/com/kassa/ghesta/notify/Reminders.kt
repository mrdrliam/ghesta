package com.kassa.ghesta.notify

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kassa.ghesta.MainActivity
import com.kassa.ghesta.R
import com.kassa.ghesta.data.Account
import com.kassa.ghesta.data.Dir
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.Sync
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.isObligation
import com.kassa.ghesta.data.remaining
import com.kassa.ghesta.widget.GhestaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reminder notifications.
 *
 * Every payment that needs attention gets its own notification with buttons ("it is paid", "in 1 hour"),
 * grouped under a summary when there are several. A daily alarm posts them at the user's chosen hour and
 * keeps nagging about overdue ones until they are paid. While the app lock is on, only counts are shown.
 */
object Reminders {
    const val CHANNEL = "dues"
    const val CHANNEL_DONE = "dues_done"
    private const val GROUP = "ghesta_dues"
    private const val DONE_TIMEOUT_MS = 120_000L

    const val ACTION_PAY = "com.kassa.ghesta.PAY"
    const val ACTION_UNDO = "com.kassa.ghesta.UNDO"
    const val ACTION_SNOOZE = "com.kassa.ghesta.SNOOZE"
    const val ACTION_SNOOZE_ALL = "com.kassa.ghesta.SNOOZE_ALL"
    const val ACTION_SNOOZE_FIRE = "com.kassa.ghesta.SNOOZE_FIRE"
    const val EXTRA_KEY = "key"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "یادآوری اقساط و بدهی‌ها", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "یادآوری روزانه‌ی اقساط، بدهی‌های نزدیک به سررسید و موارد عقب‌افتاده"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "تأیید پرداخت", NotificationManager.IMPORTANCE_LOW).apply {
                description = "پیام کوتاه بعد از «پرداخت شد» در اعلان، همراه با امکان بازگردانی"
            }
        )
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** (Re)arms the next daily alarm, or cancels it if reminders are off. Safe to call any time. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        if (!Store.settings.notify) {
            am.cancel(pi)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.toLocalDate().atTime(Store.settings.remindHour, 0).atZone(zone)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
    }

    /** Dues that deserve a notification today. */
    fun relevantDues(today: LocalDate): List<Due> {
        val offsets = Store.settings.remindDays
        return Store.items
            // my obligations, plus every cheque (a cheque I received must be cashed on time too)
            .filter { it.isObligation || it.kind == Kind.CHEQUE }
            .flatMap { it.dues() }
            .filter { !it.paid }
            .filter {
                val diff = daysBetween(today, it.date)
                diff < 0 || diff in offsets.map { o -> o.toLong() }
            }
            .sortedBy { it.date }
    }

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun activeIds(ctx: Context): Set<Int> =
        ctx.getSystemService(NotificationManager::class.java).activeNotifications
            .map { it.id }.filter { NotifIds.isDueNotification(it) }.toSet()

    /** Posts the reminders (with sound). With [force] a friendly test message is shown even if nothing is due. */
    fun fire(ctx: Context, force: Boolean = false) = render(ctx, alert = true, force = force)

    /**
     * Brings what is already in the shade up to date, silently: removes reminders that are no longer needed
     * (paid here or on another device), updates counts, and swaps in the counts-only version when the app lock
     * was just switched on. It never creates reminders out of the blue.
     */
    fun refresh(ctx: Context) {
        if (activeIds(ctx).isEmpty()) return
        render(ctx, alert = false)
    }

    private fun render(ctx: Context, alert: Boolean, force: Boolean = false) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val nm = NotificationManagerCompat.from(ctx)
        val today = LocalDate.now()
        val list = relevantDues(today)
        val plan = planNotifications(list, today, Store.settings.unit, hideDetails = Store.settings.appLock)
        val active = activeIds(ctx)

        val wanted = plan.items.map { it.id }.toSet() + (if (plan.needSummary) setOf(NotifIds.SUMMARY) else emptySet())
        for (id in active) if (id !in wanted) nm.cancel(id)

        if (list.isEmpty()) {
            if (force) post(nm, NotifIds.SUMMARY, testNotification(ctx))
            return
        }
        // silent refreshes only touch what is on screen (a swiped-away reminder must not come back),
        // except when the counts-only summary has to replace detailed ones
        fun shouldPost(id: Int) = alert || id in active || (id == NotifIds.SUMMARY && plan.generic && active.isNotEmpty())
        for (s in plan.items) if (shouldPost(s.id)) post(nm, s.id, itemNotification(ctx, s, plan.needSummary, alert))
        if (plan.needSummary && shouldPost(NotifIds.SUMMARY)) post(nm, NotifIds.SUMMARY, summaryNotification(ctx, plan, alert))
    }

    /** Posts the reminders for specific dues again (after "undo" or when a snooze ends). */
    fun postKeys(ctx: Context, keys: List<String>, alert: Boolean) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        if (Store.settings.appLock) {
            render(ctx, alert = alert) // counts only
            return
        }
        val today = LocalDate.now()
        val wanted = keys.toSet()
        val dues = Store.items
            .filter { it.isObligation || it.kind == Kind.CHEQUE }
            .flatMap { it.dues() }
            .filter { !it.paid && dueKeyOf(it) in wanted }
        val plan = planNotifications(dues, today, Store.settings.unit, hideDetails = false)
        val nm = NotificationManagerCompat.from(ctx)
        for (s in plan.items) post(nm, s.id, itemNotification(ctx, s, grouped = false, alert = alert))
    }

    private fun post(nm: NotificationManagerCompat, id: Int, n: Notification) {
        try {
            nm.notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    // ------------------------------------------------------------------ builders

    private fun openApp(ctx: Context, itemId: String?, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (itemId != null) i.data = Uri.parse("ghesta://item/$itemId")
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** One broadcast per (action, key): the data URI keeps the PendingIntents apart. */
    private fun actionIntent(ctx: Context, action: String, key: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, NotifActionReceiver::class.java).setAction(action)
            .setData(Uri.parse("ghesta://notif/${Uri.encode(action)}/${Uri.encode(key)}"))
            .putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(ctx: Context, alert: Boolean) = NotificationCompat.Builder(ctx, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_notify)
        .setColor(0xFF0E3B43.toInt())
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setOnlyAlertOnce(!alert)

    private fun itemNotification(ctx: Context, s: NotifSpec, grouped: Boolean, alert: Boolean): Notification {
        val b = base(ctx, alert)
            .setContentTitle(s.title)
            .setContentText(s.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(s.text))
            .setContentIntent(openApp(ctx, s.itemId, s.id))
        if (s.overdue) b.setColor(0xFFC23B4A.toInt())
        if (s.payLabel != null) b.addAction(R.drawable.ic_stat_notify, s.payLabel, actionIntent(ctx, ACTION_PAY, s.key))
        b.addAction(R.drawable.ic_stat_notify, "۱ ساعت دیگر", actionIntent(ctx, ACTION_SNOOZE, s.key))
        if (grouped) b.setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        return b.build()
    }

    private fun summaryNotification(ctx: Context, plan: NotifPlan, alert: Boolean): Notification {
        val style = NotificationCompat.InboxStyle().setBigContentTitle(plan.summaryTitle).setSummaryText(plan.summaryText)
        plan.summaryLines.forEach { style.addLine(it) }
        if (plan.summaryLines.isEmpty()) style.addLine(plan.summaryText)
        return base(ctx, alert)
            .setContentTitle(plan.summaryTitle)
            .setContentText(plan.summaryLines.firstOrNull() ?: plan.summaryText)
            .setSubText(if (plan.generic) null else plan.summaryText)
            .setStyle(style)
            .setContentIntent(openApp(ctx, null, NotifIds.SUMMARY))
            .addAction(R.drawable.ic_stat_notify, "همه ۱ ساعت دیگر", actionIntent(ctx, ACTION_SNOOZE_ALL, "all"))
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_ALL)
            .build()
    }

    private fun testNotification(ctx: Context): Notification = base(ctx, alert = true)
        .setContentTitle("یادآوری‌ها فعال است ✓")
        .setContentText("از این به بعد اقساط و بدهی‌های نزدیک را همین‌جا به شما یادآوری می‌کنیم.")
        .setStyle(NotificationCompat.BigTextStyle().bigText("از این به بعد اقساط و بدهی‌های نزدیک را همین‌جا به شما یادآوری می‌کنیم."))
        .setContentIntent(openApp(ctx, null, NotifIds.SUMMARY))
        .build()

    /** The short "paid" confirmation with an undo button. It lives in its own id range so refreshes never remove it. */
    fun postDone(ctx: Context, key: String, title: String, text: String) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setColor(0xFF1F8A63.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(DONE_TIMEOUT_MS)
            .setContentIntent(openApp(ctx, splitDueKey(key).first, NotifIds.done(key)))
            .addAction(R.drawable.ic_stat_notify, "بازگردانی", actionIntent(ctx, ACTION_UNDO, key))
            .build()
        post(NotificationManagerCompat.from(ctx), NotifIds.done(key), n)
    }

    fun cancelDone(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(NotifIds.done(key))

    fun cancelDue(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(NotifIds.item(key))

    fun cancelAllDues(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        activeIds(ctx).forEach { nm.cancel(it) }
    }
}

/** Snoozed reminders survive a reboot: their wake-up times are kept and re-armed on boot. */
object SnoozeStore {
    const val SNOOZE_MS = 60 * 60 * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ghesta_snooze", Context.MODE_PRIVATE)

    private fun alarmFor(ctx: Context, key: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, NotifActionReceiver::class.java).setAction(Reminders.ACTION_SNOOZE_FIRE)
            .setData(Uri.parse("ghesta://snooze/${Uri.encode(key)}"))
            .putExtra(Reminders.EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun add(ctx: Context, key: String, at: Long = System.currentTimeMillis() + SNOOZE_MS) {
        prefs(ctx).edit().putLong(key, at).apply()
        arm(ctx, key, at)
    }

    fun remove(ctx: Context, key: String) {
        prefs(ctx).edit().remove(key).apply()
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmFor(ctx, key))
    }

    private fun arm(ctx: Context, key: String, at: Long) {
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmFor(ctx, key))
    }

    fun rescheduleAll(ctx: Context) {
        val now = System.currentTimeMillis()
        prefs(ctx).all.forEach { (key, value) ->
            val at = value as? Long ?: return@forEach
            arm(ctx, key, maxOf(at, now + 5_000))
        }
    }
}

/** Handles the buttons on the reminder notifications and the end of a snooze. */
class NotifActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Account.init(context)
        val action = intent.action ?: return
        val key = intent.getStringExtra(Reminders.EXTRA_KEY) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                handle(context, action, key)
                // the phone must tell the server before this process is stopped
                if (Account.signedIn && action != Reminders.ACTION_SNOOZE && action != Reminders.ACTION_SNOOZE_ALL) {
                    withTimeoutOrNull(SYNC_BUDGET_MS) { Sync.syncNow() }
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun handle(ctx: Context, action: String, key: String) {
        val (itemId, dueKey) = splitDueKey(key)
        val today = LocalDate.now()
        when (action) {
            Reminders.ACTION_PAY -> {
                val item = Store.item(itemId) ?: return Reminders.cancelDue(ctx, key)
                val done: Pair<String, String> = when (item.kind) {
                    Kind.INST -> {
                        val inst = item.installments.firstOrNull { it.id == dueKey } ?: return Reminders.cancelDue(ctx, key)
                        if (inst.paidAt != null) return Reminders.cancelDue(ctx, key)
                        val n = item.installments.indexOf(inst) + 1
                        Store.setPaid(itemId, dueKey, true, today)
                        "قسط ${n.fa()} «${item.title}» پرداخت شد" to "ثبت شد. اگر اشتباه بود، «بازگردانی» را بزنید."
                    }
                    Kind.CHEQUE -> {
                        if (item.remaining <= 0) return Reminders.cancelDue(ctx, key)
                        Store.settleCheque(itemId, today)
                        (if (item.dir == Dir.OWE) "«${item.title}» پاس شد" else "«${item.title}» وصول شد") to
                            "ثبت شد. اگر اشتباه بود، «بازگردانی» را بزنید."
                    }
                    Kind.DEBT -> return
                }
                Reminders.cancelDue(ctx, key)
                Reminders.postDone(ctx, key, done.first, done.second)
                Reminders.refresh(ctx)
            }
            Reminders.ACTION_UNDO -> {
                val item = Store.item(itemId) ?: return Reminders.cancelDone(ctx, key)
                when (item.kind) {
                    Kind.INST -> Store.setPaid(itemId, dueKey, false)
                    Kind.CHEQUE -> Store.unsettleCheque(itemId)
                    Kind.DEBT -> {}
                }
                Reminders.cancelDone(ctx, key)
                Reminders.postKeys(ctx, listOf(key), alert = false)
            }
            Reminders.ACTION_SNOOZE -> {
                Reminders.cancelDue(ctx, key)
                SnoozeStore.add(ctx, key)
                Reminders.refresh(ctx)
            }
            Reminders.ACTION_SNOOZE_ALL -> {
                // the key only identifies the button; what to snooze is whatever is relevant right now
                Reminders.relevantDues(today).forEach { SnoozeStore.add(ctx, dueKeyOf(it)) }
                Reminders.cancelAllDues(ctx)
            }
            Reminders.ACTION_SNOOZE_FIRE -> {
                SnoozeStore.remove(ctx, key)
                Reminders.postKeys(ctx, listOf(key), alert = true)
            }
        }
    }

    private companion object {
        const val SYNC_BUDGET_MS = 7_000L
    }
}

class AlarmReceiver : BroadcastReceiver() {
    /**
     * Fires the daily reminders. When signed in, the phone first pulls the latest data from the server
     * (an installment paid on another device must not be nagged about), but never waits long:
     * offline or slow, they are built from what the phone already has.
     */
    override fun onReceive(context: Context, intent: Intent?) {
        Store.init(context)
        Account.init(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                if (Account.signedIn) withTimeoutOrNull(SYNC_BUDGET_MS) { Sync.syncNow() }
            } catch (_: Exception) {
            }
            try {
                Reminders.fire(context)
                Reminders.schedule(context)
                GhestaWidget.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** A broadcast receiver must finish within ~10 s, so stay well below that. */
        const val SYNC_BUDGET_MS = 7_000L
    }
}

/** Re-arms the alarms after reboot, app update, or clock / timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Store.init(context)
        Reminders.ensureChannel(context)
        Reminders.schedule(context)
        SnoozeStore.rescheduleAll(context)
    }
}
