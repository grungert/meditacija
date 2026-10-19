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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Experience sampling: a few prompts a day at random moments inside a chosen window.
 * Alarms fire at the chosen minute when exact alarms are allowed, otherwise within a ten-minute window
 * (a plain inexact alarm may drift by most of the time left until it, which can be hours).
 * A missed prompt is simply missed.
 */
object Esm {
    const val CHANNEL = "zapazanja"
    private const val REMIND_CHANNEL = "podsjetnici"
    const val EXTRA_PROMPT = "prompt_id"
    const val EXTRA_WEEK = "week_review"
    private const val EXTRA_MOOD = "mood"
    private const val KEY_TEXT = "text"
    private const val ACTION_REPLY = "com.grungert.nebo.ESM_REPLY"
    private const val ACTION_WEEK = "com.grungert.nebo.WEEK"
    private const val ACTION_DAILY = "com.grungert.nebo.DAILY"
    private const val ACTION_REMIND = "com.grungert.nebo.REMIND"
    private const val ACTION_SNOOZE = "com.grungert.nebo.SNOOZE"
    const val EXTRA_START = "start_session"
    private const val ACTION_FIRE = "com.grungert.nebo.ESM_FIRE"
    private const val ACTION_PLAN = "com.grungert.nebo.ESM_PLAN"
    private const val ACTION_SILENCE = "com.grungert.nebo.ESM_SILENCE"
    private const val ACTION_FIFTY = "com.grungert.nebo.FIFTY"
    const val EXTRA_FIFTY = "fifty_fifty"
    const val EXTRA_HOURGLASS = "hourglass"
    private const val FIFTY_CHANNEL = "pedeset"
    private val FIFTY_LINES = listOf(
        "Pola pažnje u tijelu, pola napolju. Stopala na podu, i zvukovi oko tebe.",
        "Osjeti dah, i u isto vrijeme vidi svjetlo oko sebe.",
        "Ruke, njihova težina. I jedna boja koju sada vidiš.",
        "Gdje je tvoje tijelo upravo sada? I šta se čuje izdaleka?",
        "Pola unutra, pola vani. Samo nekoliko daha.",
    )

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Zapažanja tokom dana", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nekoliko puta dnevno, u nasumično vrijeme: kako si sada?"
        })
        nm.createNotificationChannel(NotificationChannel(REMIND_CHANNEL, "Podsjetnici", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Vrijeme za tišinu, pitanje za danas i pogled na sedmicu."
        })
        nm.createNotificationChannel(NotificationChannel(FIFTY_CHANNEL, "50-50", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nekoliko puta dnevno: pola pažnje u tijelu, pola u svijetu oko tebe."
        })
    }

    fun today(c: Calendar = Calendar.getInstance()) = c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    /** Local midnight, [days] days from today. Steps by the calendar, so a day with a clock change is 23 or 25 hours long. */
    fun midnight(days: Int = 0, from: Calendar = Calendar.getInstance()): Calendar = (from.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    /** That day at the given hour on the wall clock (never a raw count of milliseconds, which slips on clock-change days). */
    private fun at(day: Calendar, hour: Int, minute: Int = 0): Long = (day.clone() as Calendar).apply {
        if (hour >= 24) { add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, hour - 24) } else set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }.timeInMillis

    /**
     * Plans today's prompts if none exist yet, then arms the next alarm. Safe to call often, and from several places at
     * once: a cold start runs it from the app and again from the receiver that woke it, so it is one at a time.
     */
    @Synchronized
    fun refresh(ctx: Context) {
        val s = ctx.app.settings
        val db = ctx.app.db
        val am = ctx.getSystemService(AlarmManager::class.java)
        scheduleWeek(ctx, am)
        scheduleDaily(ctx, am)
        scheduleReminder(ctx, am)
        scheduleFifty(ctx, am)
        if (!s.esmEnabled) { am.cancel(pending(ctx, ACTION_FIRE, 0)); am.cancel(pending(ctx, ACTION_PLAN, 0)); return }
        val now = System.currentTimeMillis()
        val dayStart = midnight()
        val tomorrow = midnight(1).timeInMillis
        val start = at(dayStart, s.esmStartHour)
        val end = at(dayStart, s.esmEndHour)
        val todays = db.prompts(dayStart.timeInMillis).filter { it.scheduledAt < tomorrow }
        // the rest of the day's prompts, when none are waiting: all of them in the morning, or what is left of them
        // after the person changed the window or the count during the day
        val left = s.esmPerDay - todays.count { it.firedAt != null }
        if (todays.none { it.firedAt == null } && left > 0 && end > now + 30 * 60_000L) {
            val from = maxOf(start, now + 10 * 60_000L)
            for (t in randomTimes(from, end, left)) db.insertPrompt(t)
        }
        val next = db.prompts(now).firstOrNull { it.firedAt == null }
        if (next != null) arm(am, next.scheduledAt, pending(ctx, ACTION_FIRE, next.id))
        else am.cancel(pending(ctx, ACTION_FIRE, 0))
        // plan tomorrow a few minutes after midnight
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tomorrow + 5 * 60_000L, pending(ctx, ACTION_PLAN, 0))
    }

    /** Fires at the time asked: exactly when the person allowed exact alarms, otherwise within ten minutes. */
    private fun arm(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
    }

    /** Sunday at 19:00: the week is ready to be looked at. */
    private fun scheduleWeek(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_WEEK, 0)
        if (!ctx.app.settings.weekReminder) { am.cancel(pi); return }
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 19); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.WEEK_OF_YEAR, 1)
        arm(am, c.timeInMillis, pi)
    }

    /** Each morning at the chosen hour: one question to carry into the day. */
    private fun scheduleDaily(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_DAILY, 0)
        val s = ctx.app.settings
        if (!s.dailyQuestion) { am.cancel(pi); return }
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, s.dailyHour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        arm(am, c.timeInMillis, pi)
    }

    /** Plans the day's 50-50 moments at random within the chosen hours (or tomorrow's, when today's are over) and arms the next. */
    private fun scheduleFifty(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_FIFTY, 0)
        val s = ctx.app.settings
        if (!s.fiftyOn) { am.cancel(pi); s.fiftyPlanDay = 0; return }
        val now = System.currentTimeMillis()
        // one plan is kept at a time: once tomorrow's is drawn, today is over and is not planned again
        val tomorrowKey = today(midnight(1))
        fun plan(offset: Int): List<Long> {
            val day = midnight(offset)
            val key = today(day)
            if (s.fiftyPlanDay == key) return s.fiftyPlan
            if (offset == 0 && s.fiftyPlanDay == tomorrowKey) return emptyList()
            val from = maxOf(at(day, s.fiftyFrom), now + 10 * 60_000L)
            val times = randomTimes(from, at(day, s.fiftyTo), s.fiftyPerDay)
            s.fiftyPlanDay = key; s.fiftyPlan = times
            return times
        }
        val next = plan(0).firstOrNull { it > now + 30_000L } ?: plan(1).firstOrNull { it > now } ?: return
        arm(am, next, pi)
    }

    /** A gentle 50-50 reminder; a touch opens the one-minute practice. */
    private fun notifyFifty(ctx: Context) {
        val open = PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_FIFTY, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val line = FIFTY_LINES.random()
        ctx.getSystemService(NotificationManager::class.java).notify(FIFTY_NOTIFICATION_ID, Notification.Builder(ctx, FIFTY_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("50-50").setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line + "\nDodirni za vježbu od jednog minuta."))
            .setContentIntent(open).setAutoCancel(true).setTimeoutAfter(20 * 60_000L).build())
    }

    private fun reminderDay(days: String, c: Calendar): Boolean {
        val d = c.get(Calendar.DAY_OF_WEEK)
        val weekend = d == Calendar.SATURDAY || d == Calendar.SUNDAY
        return when (days) { "weekdays" -> !weekend; "weekend" -> weekend; else -> true }
    }

    /** The next chosen time on a chosen day. */
    private fun scheduleReminder(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_REMIND, 0)
        val s = ctx.app.settings
        if (!s.reminderOn) { am.cancel(pi); return }
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, s.reminderHour); set(Calendar.MINUTE, s.reminderMinute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1)
        repeat(7) { if (!reminderDay(s.reminderDays, c)) c.add(Calendar.DAY_OF_YEAR, 1) }
        arm(am, c.timeInMillis, pi)
    }

    /** The ISO week (Monday first) as yyyyww; the year is the week's own, so 28 Dec – 3 Jan is one week. */
    fun weekKey(c: Calendar = Calendar.getInstance()): Int {
        val k = c.clone() as Calendar
        k.firstDayOfWeek = Calendar.MONDAY; k.minimalDaysInFirstWeek = 4
        return k.weekYear * 100 + k.get(Calendar.WEEK_OF_YEAR)
    }

    /**
     * Midnight of the Monday that starts the week a review looks back on. From Tuesday to Sunday that is this week;
     * on Monday the week has only just begun, so it is the one that ended last night.
     */
    fun reviewWeekStart(now: Calendar = Calendar.getInstance()): Calendar = (now.clone() as Calendar).apply {
        firstDayOfWeek = Calendar.MONDAY
        if (get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
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
                // a prompt taken away by a change in the settings, or already shown, stays quiet
                val fresh = ctx.app.db.prompt(id)?.let { it.firedAt == null } == true
                ctx.app.db.markPromptFired(id, System.currentTimeMillis())
                if (fresh && s.esmEnabled && s.esmSilencedDay != today()) notify(ctx, id)
            }
            ACTION_REPLY -> {
                // answered straight from the notification: typed words, or one tap on a mood
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                val typed = android.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT)?.toString()?.trim()
                val mood = intent.getStringExtra(EXTRA_MOOD)
                val db = ctx.app.db
                val nm = ctx.getSystemService(NotificationManager::class.java)
                // nothing typed and no mood tapped, or a prompt answered already (a second tap, a broadcast sent again): no note
                if ((typed.isNullOrBlank() && mood == null) || (id > 0 && db.prompt(id)?.answeredEntryId != null)) {
                    nm.cancel(NOTIFICATION_ID); return
                }
                val entryId = db.insertEntry(com.grungert.nebo.data.Entry(createdAt = System.currentTimeMillis(), kind = "esm",
                    promptId = id.takeIf { it > 0 }, text = typed?.ifBlank { null }, moods = listOfNotNull(mood)))
                if (id > 0) db.markPromptAnswered(id, entryId)
                nm.notify(NOTIFICATION_ID, Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notification).setOnlyAlertOnce(true)
                    .setContentTitle("Zapisano").setContentText("Hvala. Vrati se onome što si radio.").setTimeoutAfter(2500).build())
                ctx.app.scope.launch { ctx.app.recommender.tagNotes() }   // its themes, now rather than at the next start
                return
            }
            ACTION_REMIND, ACTION_SNOOZE -> {
                val doneToday = ctx.app.db.sessions(midnight().timeInMillis).isNotEmpty()
                ctx.getSystemService(NotificationManager::class.java).cancel(REMIND_NOTIFICATION_ID)
                if (s.reminderOn && !(s.reminderSkipIfDone && doneToday)) {
                    val open = PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val start = PendingIntent.getActivity(ctx, 5, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_START, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    // or just the three-minute breathing space, for a full day
                    val short = PendingIntent.getActivity(ctx, 6, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_HOURGLASS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(NotificationManager::class.java).notify(REMIND_NOTIFICATION_ID, Notification.Builder(ctx, REMIND_CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Vrijeme za tišinu")
                        .setContentText("Nekoliko minuta disanja ispod neba?").setContentIntent(open).setAutoCancel(true)
                        .setTimeoutAfter(3 * 60 * 60_000L)
                        .addAction(Notification.Action.Builder(null, "Započni", start).build())
                        .addAction(Notification.Action.Builder(null, "3 minuta", short).build())
                        .addAction(Notification.Action.Builder(null, "Za 15 min", pending(ctx, ACTION_SNOOZE + ".req", 0)).build())
                        .build())
                }
            }
            ACTION_SNOOZE + ".req" -> {
                // later, once: a one-off reminder in a quarter of an hour
                ctx.getSystemService(NotificationManager::class.java).cancel(REMIND_NOTIFICATION_ID)
                arm(ctx.getSystemService(AlarmManager::class.java), System.currentTimeMillis() + 15 * 60_000L, pending(ctx, ACTION_SNOOZE, 0))
                return
            }
            ACTION_DAILY -> {
                val lib = ctx.app.library
                // the week's koan question when one is being carried, otherwise a question from the library
                val item = lib.byId(s.activeWeekKoan()) ?: lib.items.filter { it.question != null }.randomOrNull()   // koans and readings carry a question
                val q = item?.question
                if (s.dailyQuestion && q != null) {
                    val open = PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(NotificationManager::class.java).notify(DAILY_NOTIFICATION_ID, Notification.Builder(ctx, REMIND_CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Pitanje za danas").setContentText(q)
                        .setStyle(Notification.BigTextStyle().bigText(q + (item.title?.let { "\n— $it" } ?: "")))
                        .setContentIntent(open).setAutoCancel(true).build())
                }
            }
            ACTION_WEEK -> {
                val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java).putExtra(EXTRA_WEEK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (s.weekReviewed != weekKey(reviewWeekStart())) {
                    ctx.getSystemService(NotificationManager::class.java).notify(WEEK_NOTIFICATION_ID, Notification.Builder(ctx, REMIND_CHANNEL)
                        .setSmallIcon(R.drawable.ic_notification).setContentTitle("Sedmica je iza tebe")
                        .setContentText("Pogledaj šta se vraćalo, i šta ti primjećuješ.").setContentIntent(open).setAutoCancel(true).build())
                }
            }
            ACTION_FIFTY -> if (s.fiftyOn) notifyFifty(ctx)
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
    private const val REMIND_NOTIFICATION_ID = 10
    private const val FIFTY_NOTIFICATION_ID = 12

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

/** Database, settings and alarms are work for a background thread; the receiver stays alive until it is done. */
private fun BroadcastReceiver.inBackground(context: Context, work: () -> Unit) {
    val result = goAsync()
    context.app.scope.launch(Dispatchers.IO) { try { work() } finally { result.finish() } }
}

class EsmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = inBackground(context) { Esm.onAlarm(context, intent) }
}

private val REARM = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // after a reboot or an update, when exact alarms are allowed or taken away, or when the clock or the time zone
        // changes (travel, a hand-set time), everything is armed again at the right wall-clock time
        if (intent.action in REARM) inBackground(context) { Esm.refresh(context) }
    }
}
