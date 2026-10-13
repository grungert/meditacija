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

    companion object {
        const val EXTRA_NOTE = "write_note"

        /** Redraws every Nebo widget; cheap, so it is called whenever the week koan may have changed. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NeboWidget::class.java))
            if (ids.isEmpty()) return
            val app = context.applicationContext as NeboApp
            val koan = app.library.byId(app.settings.activeWeekKoan())
            val views = RemoteViews(context.packageName, R.layout.widget_nebo)
            if (koan != null) {
                views.setTextViewText(R.id.widget_label, "KOAN SEDMICE · ${app.settings.weekKoanDay()}. DAN")
                views.setTextViewText(R.id.widget_text, koan.question ?: koan.title ?: koan.text)
            } else {
                views.setTextViewText(R.id.widget_label, "NEBO")
                views.setTextViewText(R.id.widget_text, context.getString(R.string.widget_text_default))
            }
            fun open(code: Int, extra: String?) = PendingIntent.getActivity(context, code,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    if (extra != null) putExtra(extra, true)
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_root, open(40, null))
            views.setOnClickPendingIntent(R.id.widget_start, open(41, Esm.EXTRA_START))
            views.setOnClickPendingIntent(R.id.widget_note, open(42, EXTRA_NOTE))
            manager.updateAppWidget(ids, views)
        }
    }
}
