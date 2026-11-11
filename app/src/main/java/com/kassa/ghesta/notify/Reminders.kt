package com.kassa.ghesta.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kassa.ghesta.MainActivity
import com.kassa.ghesta.R
import com.kassa.ghesta.data.Due
import com.kassa.ghesta.data.Store
import com.kassa.ghesta.data.daysBetween
import com.kassa.ghesta.data.dues
import com.kassa.ghesta.data.fa
import com.kassa.ghesta.data.fmtNum
import com.kassa.ghesta.data.isObligation
import com.kassa.ghesta.data.relLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Daily digest notification, fired by an alarm at the user's chosen hour.
 * It lists everything overdue plus everything that falls on one of the chosen
 * "days before" offsets, and keeps nagging about overdue items every day until they are paid.
 */
object Reminders {
    private const val CHANNEL = "dues"
    private const val NOTIF_ID = 1001

    fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "یادآوری اقساط و بدهی‌ها", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "یادآوری روزانه‌ی اقساط، بدهی‌های نزدیک به سررسید و موارد عقب‌افتاده"
            enableVibration(true)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
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
            .filter { it.isObligation }
            .flatMap { it.dues() }
            .filter { !it.paid }
            .filter {
                val diff = daysBetween(today, it.date)
                diff < 0 || diff in offsets.map { o -> o.toLong() }
            }
            .sortedBy { it.date }
    }

    fun canNotify(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** Posts the digest. With [force] a friendly test message is shown even if nothing is due. */
    fun fire(ctx: Context, force: Boolean = false) {
        if (!canNotify(ctx)) return
        ensureChannel(ctx)
        val today = LocalDate.now()
        val list = relevantDues(today)
        if (list.isEmpty() && !force) return

        val unit = Store.settings.unit
        val overdueN = list.count { it.date.isBefore(today) }
        val todayN = list.count { it.date == today }
        val soonN = list.size - overdueN - todayN

        val title: String
        val summary: String
        val lines: List<String>
        if (list.isEmpty()) {
            title = "یادآوری‌ها فعال است ✓"
            summary = "از این به بعد اقساط و بدهی‌های نزدیک را همین‌جا به شما یادآوری می‌کنیم."
            lines = emptyList()
        } else {
            title = when {
                overdueN > 0 -> "${overdueN.fa()} مورد عقب‌افتاده" + if (todayN > 0) " و ${todayN.fa()} مورد امروز" else ""
                todayN > 0 -> "${todayN.fa()} پرداخت امروز دارید"
                else -> "${soonN.fa()} پرداخت نزدیک دارید"
            }
            summary = "مجموع: ${fmtNum(list.sumOf { it.amount })} $unit"
            lines = list.take(7).map {
                "${it.item.title} · ${it.label} · ${fmtNum(it.amount)} · ${relLabel(daysBetween(today, it.date))}"
            }
        }

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText(summary)
        lines.forEach { style.addLine(it) }
        if (lines.isEmpty()) style.addLine(summary)
        if (list.size > lines.size) style.addLine("و ${(list.size - lines.size).fa()} مورد دیگر…")

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(if (lines.isNotEmpty()) lines.first() else summary)
            .setSubText(summary.takeIf { lines.isNotEmpty() })
            .setStyle(style)
            .setColor(0xFF0E3B43.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Store.init(context)
        Reminders.fire(context)
        Reminders.schedule(context)
    }
}

/** Re-arms the alarm after reboot, app update, or clock / timezone changes. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Store.init(context)
        Reminders.ensureChannel(context)
        Reminders.schedule(context)
    }
}
