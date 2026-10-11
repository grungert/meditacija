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
    const val EXTRA_WEEK = "week_review"
    private const val EXTRA_MOOD = "mood"
    private const val KEY_TEXT = "text"
    private const val ACTION_REPLY = "com.grungert.nebo.ESM_REPLY"
    private const val ACTION_WEEK = "com.grungert.nebo.WEEK"
    private const val ACTION_DAILY = "com.grungert.nebo.DAILY"
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
        scheduleWeek(ctx, am)
        scheduleDaily(ctx, am)
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

    /** Sunday at 19:00: the week is ready to be looked at. */
    private fun scheduleWeek(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_WEEK, 0)
        if (!ctx.app.settings.weekReminder) { am.cancel(pi); return }
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.WEEK_OF_YEAR, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }

    /** Each morning at the chosen hour: one question to carry into the day. */
    private fun scheduleDaily(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_DAILY, 0)
        val s = ctx.app.settings
        if (!s.dailyQuestion) { am.cancel(pi); return }
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, s.dailyHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }

    fun weekKey(c: Calendar = Calendar.getInstance()): Int {
        val k = c.clone() as Calendar
        k.firstDayOfWeek = Calendar.MONDAY; k.minimalDaysInFirstWeek = 4
        return k.get(Calendar.YEAR) * 100 + k.get(Calendar.WEEK_OF_YEAR)
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

    private fun pending(ctx: Context, action: String, id: Long, mood: String? = null, mutable: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(ctx, (action + (mood ?: "")).hashCode(),
            Intent(ctx, EsmReceiver::class.java).setAction(action).putExtra(EXTRA_PROMPT, id).putExtra(EXTRA_MOOD, mood),
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun onAlarm(ctx: Context, intent: Intent) {
        val s = ctx.app.settings
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                ctx.app.db.markPromptFired(id, System.currentTimeMillis())
                if (s.esmEnabled && s.esmSilencedDay != today()) notify(ctx, id)
            }
            ACTION_REPLY -> {
                // answered straight from the notification: typed words, or one tap on a mood
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                val typed = android.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim()
                val mood = intent.getStringExtra(EXTRA_MOOD)
                val db = ctx.app.db
                val entryId = db.insertEntry(com.grungert.nebo.data.Entry(createdAt = System.currentTimeMillis(), kind = "esm",
                    promptId = id.takeIf { it > 0 }, text = typed?.ifBlank { null }, moods = listOfNotNull(mood)))
                if (id > 0) db.markPromptAnswered(id, entryId)
                val nm = ctx.getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Zapisano").setContentText("Hvala. Vrati se onome što si radio.").setTimeoutAfter(2500).build())
                return
            }
            ACTION_DAILY -> {
                val lib = ctx.app.library
                // the week's koan question when one is being carried, otherwise a question from the library
                val item = lib.byId(s.activeWeekKoan()) ?: lib.items.filter { it.isKoan && it.question != null }.randomOrNull()
                val q = item?.question
                if (s.dailyQuestion && q != null) {
                    val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(NotificationManager::class.java).notify(DAILY_NOTIFICATION_ID, Notification.Builder(ctx, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Pitanje za danas").setContentText(q)
                        .setStyle(Notification.BigTextStyle().bigText(q + (item.title?.let { "\n— $it" } ?: "")))
                        .setContentIntent(open).setAutoCancel(true).build())
                }
            }
            ACTION_WEEK -> {
                val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_WEEK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (s.weekReviewed != weekKey()) {
                    ctx.getSystemService(NotificationManager::class.java).notify(WEEK_NOTIFICATION_ID, Notification.Builder(ctx, CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Sedmica je iza tebe")
                        .setContentText("Pogledaj šta se vraćalo, i šta ti primjećuješ.").setContentIntent(open).setAutoCancel(true).build())
                }
            }
            ACTION_SILENCE -> {
                s.esmSilencedDay = today()
                ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
        }
        refresh(ctx)
    }

    private const val NOTIFICATION_ID = 7
    private const val WEEK_NOTIFICATION_ID = 8
    private const val DAILY_NOTIFICATION_ID = 9

    private fun notify(ctx: Context, promptId: Long) {
        val open = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_PROMPT, promptId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val reply = Notification.Action.Builder(null, "Napiši", pending(ctx, ACTION_REPLY, promptId, mutable = true))
            .addRemoteInput(android.app.RemoteInput.Builder(KEY_TEXT).setLabel("Šta je u tebi?").build()).build()
        fun mood(m: String) = Notification.Action.Builder(null, m, pending(ctx, ACTION_REPLY, promptId, mood = m)).build()
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Kako si sada?")
            .setContentText("Dodirni da govoriš, ili odgovori odmah ovdje.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(60 * 60_000L)
            .addAction(reply).addAction(mood("mirno")).addAction(mood("nemirno"))
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
