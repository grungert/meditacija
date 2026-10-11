package com.grungert.nebo.esm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.Notification
import com.grungert.nebo.MainActivity
import com.grungert.nebo.R
import com.grungert.nebo.app
import java.util.Calendar
import kotlin.random.Random

/**
 * Experience sampling: a few prompts a day at random moments inside a chosen window.
 * Uses inexact-but-Doze-friendly alarms (minutes of slack, never hours) and needs no special permission.
 * A missed prompt is simply missed.
 */
object Esm {
    const val CHANNEL = "zapazanja"
    const val EXTRA_PROMPT = "prompt_id"
    private const val ACTION_FIRE = "com.grungert.nebo.ESM_FIRE"
    private const val ACTION_PLAN = "com.grungert.nebo.ESM_PLAN"
    private const val ACTION_SILENCE = "com.grungert.nebo.ESM_SILENCE"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Zapažanja tokom dana", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nekoliko puta dnevno, u nasumično vrijeme: kako si sada?"
        })
    }

    fun today(c: Calendar = Calendar.getInstance()) = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    /** Plans today's prompts if none exist yet, then arms the next alarm. Safe to call often. */
    fun refresh(ctx: Context) {
        val s = ctx.app.settings
        val db = ctx.app.db
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!s.esmEnabled) { am.cancel(pending(ctx, ACTION_FIRE, 0)); am.cancel(pending(ctx, ACTION_PLAN, 0)); return }
        val now = System.currentTimeMillis()
        val dayStart = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val start = dayStart.timeInMillis + s.esmStartHour * 3_600_000L
        val end = dayStart.timeInMillis + s.esmEndHour * 3_600_000L
        val todays = db.prompts(dayStart.timeInMillis).filter { it.scheduledAt < dayStart.timeInMillis + 86_400_000L }
        if (todays.isEmpty() && end > now + 30 * 60_000L) {
            val from = maxOf(start, now + 10 * 60_000L)
            for (t in randomTimes(from, end, s.esmPerDay)) db.insertPrompt(t)
        }
        val next = db.prompts(now).firstOrNull { it.firedAt == null }
        if (next != null) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.scheduledAt, pending(ctx, ACTION_FIRE, next.id))
        }
        // plan tomorrow a few minutes after midnight
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dayStart.timeInMillis + 86_400_000L + 5 * 60_000L, pending(ctx, ACTION_PLAN, 0))
    }

    /** n random moments between from and to, at least 45 minutes apart where the window allows. */
    fun randomTimes(from: Long, to: Long, n: Int): List<Long> {
        if (to <= from || n <= 0) return emptyList()
        val gap = minOf(45 * 60_000L, (to - from) / (n + 1))
        repeat(200) {
            val ts = List(n) { from + (Random.nextDouble() * (to - from)).toLong() }.sorted()
            if (ts.zipWithNext().all { (a, b) -> b - a >= gap }) return ts
        }
        return List(n) { i -> from + (to - from) * (i + 1) / (n + 1) }
    }

    private fun pending(ctx: Context, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(ctx, action.hashCode(), Intent(ctx, EsmReceiver::class.java).setAction(action).putExtra(EXTRA_PROMPT, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun onAlarm(ctx: Context, intent: Intent) {
        val s = ctx.app.settings
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                ctx.app.db.markPromptFired(id, System.currentTimeMillis())
                if (s.esmEnabled && s.esmSilencedDay != today()) notify(ctx, id)
            }
            ACTION_SILENCE -> {
                s.esmSilencedDay = today()
                ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
        }
        refresh(ctx)
    }

    private const val NOTIFICATION_ID = 7

    private fun notify(ctx: Context, promptId: Long) {
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_PROMPT, promptId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val silence = pending(ctx, ACTION_SILENCE, 0)
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Kako si sada?")
            .setContentText("Pritisni i reci šta je u tebi. Može i da preskočiš.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60_000L)
            .addAction(Notification.Action.Builder(null, "Utišaj danas", silence).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }
}

class EsmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Esm.onAlarm(context, intent)
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Esm.refresh(context)
    }
}
