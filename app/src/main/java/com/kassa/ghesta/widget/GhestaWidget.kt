package com.kassa.ghesta.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kassa.ghesta.MainActivity
import com.kassa.ghesta.R
import com.kassa.ghesta.data.Kind
import com.kassa.ghesta.data.Store
import java.time.LocalDate

object GhestaWidget {
    private fun content(): WidgetContent =
        buildWidgetContent(Store.items.filter { it.kind != Kind.EXPENSE }, LocalDate.now(), Store.settings.unit, hideDetails = Store.settings.appLock)

    private fun color(t: WidgetTone): Int = when (t) {
        WidgetTone.DANGER -> 0xFFFF8A96.toInt()
        WidgetTone.WARN -> 0xFFE2C070.toInt()
        WidgetTone.OK -> 0xFF52CBA0.toInt()
        WidgetTone.NEUTRAL -> 0xFFFFFFFF.toInt()
    }

    internal fun rows(): List<WidgetRowData> = content().rows

    fun render(ctx: Context): RemoteViews {
        val c = content()
        val rv = RemoteViews(ctx.packageName, R.layout.widget_main)
        rv.setTextViewText(R.id.w_date, c.dateText)
        rv.setTextViewText(R.id.w_summary, c.summary)
        rv.setTextColor(R.id.w_summary, color(c.tone))
        rv.setTextViewText(R.id.w_empty, c.emptyNote)
        rv.setRemoteAdapter(R.id.w_list, Intent(ctx, WidgetService::class.java))
        rv.setEmptyView(R.id.w_list, R.id.w_empty)

        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        rv.setOnClickPendingIntent(R.id.widget_root, open)
        // each row fills in its own item id; the template must be mutable so the fill-in can be merged
        val template = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
        )
        rv.setPendingIntentTemplate(R.id.w_list, template)
        return rv
    }

    /** Redraws every placed widget. Cheap no-op when none is on the home screen. */
    fun updateAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, GhestaWidgetProvider::class.java))
        if (ids.isEmpty()) return
        mgr.updateAppWidget(ids, render(ctx))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
    }
}

class GhestaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Store.init(context)
        manager.updateAppWidget(ids, GhestaWidget.render(context))
        manager.notifyAppWidgetViewDataChanged(ids, R.id.w_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        super.onReceive(context, intent)
        // midnight / clock changes: "tomorrow" and "N days left" must move on
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_UPDATE) GhestaWidget.updateAll(context)
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var rows: List<WidgetRowData> = emptyList()

        override fun onCreate() {
            Store.init(ctx)
        }

        override fun onDataSetChanged() {
            rows = GhestaWidget.rows()
        }

        override fun onDestroy() {}
        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val r = rows[position]
            val rv = RemoteViews(ctx.packageName, R.layout.widget_row)
            rv.setTextViewText(R.id.r_title, r.title)
            rv.setTextViewText(R.id.r_sub, r.sub)
            rv.setTextViewText(R.id.r_amount, r.amount)
            rv.setTextViewText(R.id.r_rel, r.rel)
            rv.setTextColor(R.id.r_rel, if (r.overdue) 0xFFFF8A96.toInt() else 0xFFE2C070.toInt())
            rv.setOnClickFillInIntent(R.id.r_title, Intent().setData(Uri.parse("ghesta://item/${r.itemId}")))
            rv.setOnClickFillInIntent(R.id.r_sub, Intent().setData(Uri.parse("ghesta://item/${r.itemId}")))
            rv.setOnClickFillInIntent(R.id.r_amount, Intent().setData(Uri.parse("ghesta://item/${r.itemId}")))
            rv.setOnClickFillInIntent(R.id.r_rel, Intent().setData(Uri.parse("ghesta://item/${r.itemId}")))
            return rv
        }
    }
}
