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
import kotlinx.coroutines.launch

/**
 * The home-screen widget: what is suggested now and why (or the question of the koan carried this week), the week's
 * minutes and its seven days, the suggested session one touch away, and the microphone for a note.
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
        /** A kind of session to start straight away, as suggested on the widget. */
        const val EXTRA_START_TYPE = "start_type"

        private val SESSION_NAME = mapOf(
            "breath" to "Disanje", "walk" to "Šetnja", "thoughts" to "Oblaci", "silence" to "Tišina", "points" to "4 tačke",
            "hourglass" to "3 minuta", "bodyscan" to "Skeniranje tijela", "urge" to "Talas", "rain" to "RAIN",
        )

        /**
         * Redraws every Nebo widget: the sky of this hour, what is suggested now and why (or the koan being carried),
         * the week's minutes and its seven days. Read and drawn off the main thread; called on the system's schedule,
         * when the app goes to the background, and whenever the week koan may have changed.
         */
        fun refresh(context: Context) {
            val app = context.applicationContext as NeboApp
            app.scope.launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { draw(context.applicationContext) } }
        }

        private fun draw(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NeboWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as NeboApp
            val s = app.settings
            val now = System.currentTimeMillis()
            val entries = app.db.entries()
            val sessions = app.db.sessions()
            val part = com.grungert.nebo.koan.Links.part(now)
            val helps = com.grungert.nebo.koan.WhatHelps.suggestion(com.grungert.nebo.koan.WhatHelps.find(sessions, entries))
            val suggestion = com.grungert.nebo.ui.suggestNow(entries, helps, part)
            val koan = app.library.byId(s.activeWeekKoan())
            val dayFmt = java.text.SimpleDateFormat("EEEE, d. MMMM", java.util.Locale.forLanguageTag("sr-Latn-ME"))
            val label = if (koan != null) "KOAN SEDMICE · ${s.weekKoanDay()}. DAN" else com.grungert.nebo.ui.ijekavian(dayFmt.format(java.util.Date(now))).uppercase()
            val text = koan?.let { it.question ?: it.title ?: it.text } ?: suggestion.why
            // the week, Monday to Sunday: minutes so far, and which days held a sit of at least half a minute
            val monday = com.grungert.nebo.ui.weekStart()
            val week = sessions.filter { it.startedAt >= monday }
            val minutes = week.sumOf { it.actualSec } / 60
            val sat = BooleanArray(7) { i -> week.any { it.actualSec >= 30 && it.startedAt >= monday + i * 86_400_000L && it.startedAt < monday + (i + 1) * 86_400_000L } }
            val today = ((now - monday) / 86_400_000L).toInt().coerceIn(0, 6)
            val info = if (minutes == 0 && sat.none { it }) "Sedmica tek počinje" else "$minutes min ove sedmice · ${sat.count { it }} ${com.grungert.nebo.ui.plural(sat.count { it }, "dan", "dana", "dana")}"
            val density = context.resources.displayMetrics.density
            for (id in ids) {
                // the widget's size in portrait, as the launcher reports it: narrowest width with tallest height
                // (the widest width goes with the lowest height, which is landscape)
                val o = manager.getAppWidgetOptions(id)
                val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 330
                val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 } ?: 160
                val k = minOf(density, 2.5f)   // keeps the picture within what a widget may carry
                val sky = runCatching { WidgetArt.render(s, (wDp * k).toInt(), (hDp * k).toInt(), k, sat, today) }.getOrNull()
                manager.updateAppWidget(id, views(context, label, text, info, suggestion.type, sky))
            }
        }

        private fun views(context: Context, label: String, text: String, info: String, type: String, sky: android.graphics.Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_nebo)
            views.setTextViewText(R.id.widget_label, label)
            views.setTextViewText(R.id.widget_text, text)
            views.setTextViewText(R.id.widget_info, info)
            views.setTextViewText(R.id.widget_start, SESSION_NAME[type] ?: context.getString(R.string.widget_start))
            if (sky != null) views.setImageViewBitmap(R.id.widget_sky, sky)
            fun open(code: Int, put: Intent.() -> Unit) = PendingIntent.getActivity(context, code,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    put()
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open(40) {})
            views.setOnClickPendingIntent(R.id.widget_start, open(41) { putExtra(EXTRA_START_TYPE, type) })
            views.setOnClickPendingIntent(R.id.widget_note, open(42) { putExtra(EXTRA_NOTE, true) })
            return views
        }
    }
}
