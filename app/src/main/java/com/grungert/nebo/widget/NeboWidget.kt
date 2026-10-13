package com.grungert.nebo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.grungert.nebo.MainActivity
import com.grungert.nebo.NeboApp
import com.grungert.nebo.R
import com.grungert.nebo.esm.Esm

/**
 * The home-screen widget: the koan carried this week (its question), a session one touch away
 * set up like the last one, and a quick note.
 */
class NeboWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // a new version of the app, or a restart of the phone: draw again, or the launcher shows an empty grey tile
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || intent.action == Intent.ACTION_BOOT_COMPLETED) refresh(context)
    }

    companion object {
        const val EXTRA_NOTE = "write_note"

        /** Redraws every Nebo widget with the sky of this hour; called on the system's schedule and whenever the week koan may have changed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NeboWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as NeboApp
            val koan = app.library.byId(app.settings.activeWeekKoan())
            val label = if (koan != null) "KOAN SEDMICE · ${app.settings.weekKoanDay()}. DAN" else "NEBO"
            val text = if (koan != null) koan.question ?: koan.title ?: koan.text else context.getString(R.string.widget_text_default)
            val density = context.resources.displayMetrics.density
            for (id in ids) {
                // the widget's size in this orientation, as the launcher reports it
                val o = manager.getAppWidgetOptions(id)
                val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).takeIf { it > 0 } ?: 330
                val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 160
                val k = minOf(density, 2.5f)   // keeps the picture within what a widget may carry
                val sky = runCatching { WidgetArt.render(app.settings, (wDp * k).toInt(), (hDp * k).toInt(), k) }.getOrNull()
                manager.updateAppWidget(id, views(context, label, text, sky))
            }
        }

        private fun views(context: Context, label: String, text: String, sky: android.graphics.Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_nebo)
            views.setTextViewText(R.id.widget_label, label)
            views.setTextViewText(R.id.widget_text, text)
            if (sky != null) views.setImageViewBitmap(R.id.widget_sky, sky)
            fun open(code: Int, extra: String?) = PendingIntent.getActivity(context, code,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    if (extra != null) putExtra(extra, true)
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open(40, null))
            views.setOnClickPendingIntent(R.id.widget_start, open(41, Esm.EXTRA_START))
            views.setOnClickPendingIntent(R.id.widget_note, open(42, EXTRA_NOTE))
            return views
        }
    }
}
