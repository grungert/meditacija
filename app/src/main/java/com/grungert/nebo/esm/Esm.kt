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
 * Alarms fire at the chosen minute when exact alarms are allowed. Otherwise see [arm]: they arrive up to about fifteen
 * minutes late, also while the phone lies still in Doze. A missed prompt is simply missed.
 */
object Esm {
    // the channels carry the bell of the sessions as their sound; a channel's sound cannot change once made, so these
    // are new ones, and the old ones without a bell are removed (see ensureChannel)
    const val CHANNEL = "zapazanja_zvono"
    private const val REMIND_CHANNEL = "podsjetnici_zvono"
    private val OLD_CHANNELS = listOf("zapazanja", "podsjetnici", "pedeset")
    const val EXTRA_PROMPT = "prompt_id"
    const val EXTRA_WEEK = "week_review"
    private const val EXTRA_MOOD = "mood"
    private const val KEY_TEXT = "text"
    private const val ACTION_REPLY = "com.grungert.nebo.ESM_REPLY"
    private const val ACTION_DISMISS = "com.grungert.nebo.ESM_DISMISS"
    private const val ACTION_FIFTY_DISMISS = "com.grungert.nebo.FIFTY_DISMISS"
    private const val ACTION_WEEK = "com.grungert.nebo.WEEK"
    private const val ACTION_DAILY = "com.grungert.nebo.DAILY"
    private const val ACTION_REMIND = "com.grungert.nebo.REMIND"
    private const val ACTION_SNOOZE = "com.grungert.nebo.SNOOZE"
    const val EXTRA_START = "start_session"
    private const val ACTION_FIRE = "com.grungert.nebo.ESM_FIRE"
    private const val ACTION_PLAN = "com.grungert.nebo.ESM_PLAN"
    private const val ACTION_HOP = "com.grungert.nebo.ESM_HOP"   // a step towards a far alarm; only refreshes
    private const val ACTION_SILENCE = "com.grungert.nebo.ESM_SILENCE"
    private const val ACTION_FIFTY = "com.grungert.nebo.FIFTY"
    const val EXTRA_FIFTY = "fifty_fifty"
    /** Which 50-50 reminder opened the practice, so the minute it led to is kept with it. */
    const val EXTRA_FIFTY_CALL = "fifty_call"
    const val EXTRA_HOURGLASS = "hourglass"
    const val EXTRA_EVENING = "evening_review"
    const val EXTRA_FOLLOW = "follow_up"
    /** The message a tap opened the app from, so it is kept as opened. */
    const val EXTRA_NOTICE = "notice"
    private const val ACTION_NOTICE_DISMISS = "com.grungert.nebo.NOTICE_DISMISS"
    private const val EXTRA_FIRST = "first"
    private const val ACTION_FOLLOW = "com.grungert.nebo.FOLLOW"
    private const val ACTION_EVENING = "com.grungert.nebo.EVENING"
    private const val FIFTY_CHANNEL = "pedeset_zvono"
    private const val ACTION_LATER = "com.grungert.nebo.LATER"
    private const val EXTRA_WHAT = "what"
    private val FIFTY_LINES = listOf(
        "Pola pažnje u tijelu, pola napolju. Stopala na podu, i zvukovi oko tebe.",
        "Osjeti dah, i u isto vrijeme vidi svjetlo oko sebe.",
        "Ruke, njihova težina. I jedna boja koju sada vidiš.",
        "Gdje je tvoje tijelo upravo sada? I šta se čuje izdaleka?",
        "Pola unutra, pola vani. Samo nekoliko daha.",
    )

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        fun bell(raw: Int) = android.net.Uri.parse("android.resource://${ctx.packageName}/$raw")
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Zapažanja tokom dana", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nekoliko puta dnevno, u nasumično vrijeme: kako si sada?"
            setSound(bell(R.raw.nebo_bell_soft), attrs)
        })
        nm.createNotificationChannel(NotificationChannel(REMIND_CHANNEL, "Podsjetnici", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Vrijeme za tišinu, pitanje za danas i pogled na sedmicu."
            setSound(bell(R.raw.nebo_bell), attrs)
        })
        nm.createNotificationChannel(NotificationChannel(FIFTY_CHANNEL, "50-50", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Nekoliko puta dnevno: pola pažnje u tijelu, pola u svijetu oko tebe."
            setSound(bell(R.raw.nebo_bell_soft), attrs)
        })
        OLD_CHANNELS.forEach { nm.deleteNotificationChannel(it) }
    }

    /** Every Nebo notification: the small icon tinted in the warm gold of the app. */
    /**
     * Every Nebo notification is private on the lock screen: what it says may come from the person's notes ("Juče ti je
     * bilo teško…", "Uveče ti često dođe „tužno“"), so a locked phone shows only that Nebo has something.
     */
    private fun builder(ctx: Context, channel: String) = Notification.Builder(ctx, channel)
        .setSmallIcon(R.drawable.ic_notification).setColor(0xFFE0A04E.toInt())
        .setVisibility(Notification.VISIBILITY_PRIVATE)
        .setPublicVersion(Notification.Builder(ctx, channel).setSmallIcon(R.drawable.ic_notification).setColor(0xFFE0A04E.toInt())
            .setContentTitle("Nebo").setContentText("Otključaj telefon da vidiš poruku.").build())

    /** When another Nebo notification came in the last ten minutes, this one waits: two at once feel like a nudge, not a pause. */
    private fun crowded(ctx: Context): Boolean {
        val now = System.currentTimeMillis()
        return ctx.getSystemService(NotificationManager::class.java).activeNotifications.any { now - it.postTime < 10 * 60_000L }
    }

    /**
     * The same notification again in twenty minutes: after a sit that was going on, or when another just came. [first]
     * is when it was first due; one held back past an hour after that, or past the end of its window [until], is not
     * sent at all: a message missed is only missed.
     */
    private fun later(ctx: Context, action: String, first: Long = System.currentTimeMillis(), until: Long = Long.MAX_VALUE) {
        val next = System.currentTimeMillis() + 20 * 60_000L
        if (next - first > DayPlan.LATE || next > until) return
        val pi = PendingIntent.getBroadcast(ctx, (ACTION_LATER + action).hashCode(),
            Intent(ctx, EsmReceiver::class.java).setAction(ACTION_LATER).putExtra(EXTRA_WHAT, action).putExtra(EXTRA_FIRST, first),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        arm(ctx.getSystemService(AlarmManager::class.java), next, pi)
    }

    /** Today's messages so far, of every kind. */
    private fun sentToday(ctx: Context) = ctx.app.db.notices(midnight().timeInMillis)

    /**
     * Whether a message of this kind may still come today: within the day's budget (the reminder the person set always
     * may), and not on a day silenced (the reminder again excepted).
     */
    private fun allowed(ctx: Context, kind: DayPlan.Kind): Boolean {
        if (kind == DayPlan.Kind.REMIND) return true
        val s = ctx.app.settings
        return s.esmSilencedDay != today() && sentToday(ctx).size < s.noticeBudget
    }

    /** A message shown: kept, and its id for the tap that opens it and the swipe that sends it away. */
    private fun shown(ctx: Context, kind: DayPlan.Kind): Long = ctx.app.db.insertNotice(kind.name)

    /** The open-the-app intent of a message, carrying which message it was. */
    private fun opens(ctx: Context, notice: Long): Intent = MainActivity.own(ctx).putExtra(EXTRA_NOTICE, notice)

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
        val am = ctx.getSystemService(AlarmManager::class.java)
        hopFor = Long.MAX_VALUE
        try { arrange(ctx, am) } finally { armHop(ctx, am) }
    }

    private fun arrange(ctx: Context, am: AlarmManager) {
        val s = ctx.app.settings
        val db = ctx.app.db
        // what the person set at a time of their own keeps its own alarm; the rest comes from the day's plan
        scheduleWeek(ctx, am)
        scheduleReminder(ctx, am)
        val plan = planToday(ctx)
        val now = System.currentTimeMillis()
        fun armKind(kind: DayPlan.Kind, action: String) {
            val pi = pending(ctx, action, 0)
            plan.firstOrNull { it.kind == kind && it.at > now }?.let { arm(am, it.at, pi) } ?: am.cancel(pi)
        }
        armKind(DayPlan.Kind.DAILY, ACTION_DAILY)
        armKind(DayPlan.Kind.EVENING, ACTION_EVENING)
        armKind(DayPlan.Kind.FOLLOW, ACTION_FOLLOW)
        armKind(DayPlan.Kind.FIFTY, ACTION_FIFTY)
        val next = if (s.esmEnabled) db.prompts(now).firstOrNull { it.firedAt == null } else null
        if (next != null) arm(am, next.scheduledAt, pending(ctx, ACTION_FIRE, next.id))
        else am.cancel(pending(ctx, ACTION_FIRE, 0))
        // plan tomorrow a few minutes after midnight
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight(1).timeInMillis + 5 * 60_000L, pending(ctx, ACTION_PLAN, 0))
    }

    /**
     * Today's plan of messages, all kinds within one budget (see [DayPlan]): made the first time it is asked for on a
     * day, and again when what it is made from changes during the day (then only the rest of the day, with the budget
     * less what already came). The questions of the moment it plans are written as prompts, the 50-50 as their plan.
     */
    private fun planToday(ctx: Context): List<DayPlan.Planned> {
        val s = ctx.app.settings
        val db = ctx.app.db
        val day = midnight()
        val key = today(day)
        val now = System.currentTimeMillis()
        val follow = followUpNote(ctx)?.takeIf { s.followUpAsked != it.id }
        val silenced = s.esmSilencedDay == key
        val from = listOf(s.noticeBudget, s.esmEnabled, s.esmPerDay, s.esmStartHour, s.esmEndHour, s.fiftyOn, s.fiftyPerDay, s.fiftyFrom, s.fiftyTo,
            s.dailyQuestion, s.dailyHour, s.eveningReview, s.eveningHour, s.reminderOn, s.reminderHour, s.reminderMinute, s.reminderDays,
            s.weekReminder, follow?.id, silenced).joinToString(",")
        if (s.dayPlanKey == key && s.dayPlanFrom == from) return DayPlan.decode(s.dayPlan)

        val fixed = ArrayList<DayPlan.Fixed>()
        if (s.reminderOn && reminderDay(s.reminderDays, day)) fixed += DayPlan.Fixed(DayPlan.Kind.REMIND, at(day, s.reminderHour, s.reminderMinute))
        if (s.weekReminder && day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY && s.weekReviewed != weekKey(reviewWeekStart()))
            fixed += DayPlan.Fixed(DayPlan.Kind.WEEK, at(day, 19))
        if (follow != null) fixed += DayPlan.Fixed(DayPlan.Kind.FOLLOW, com.grungert.nebo.koan.FollowUp.askAt(follow, day))
        if (s.eveningReview && !eveningDone(ctx)) fixed += DayPlan.Fixed(DayPlan.Kind.EVENING, at(day, s.eveningHour))
        if (s.dailyQuestion) fixed += DayPlan.Fixed(DayPlan.Kind.DAILY, at(day, s.dailyHour))
        // a day made quiet keeps only the reminder the person set
        if (silenced) fixed.retainAll { it.kind == DayPlan.Kind.REMIND }
        val sent = sentToday(ctx)
        val windows = ArrayList<DayPlan.Window>()
        if (s.esmEnabled && !silenced) {
            val fired = db.prompts(day.timeInMillis).count { it.firedAt != null && it.scheduledAt < midnight(1).timeInMillis }
            windows += DayPlan.Window(DayPlan.Kind.ESM, s.esmPerDay - fired, at(day, s.esmStartHour), at(day, s.esmEndHour))
        }
        if (s.fiftyOn && !silenced)
            windows += DayPlan.Window(DayPlan.Kind.FIFTY, s.fiftyPerDay - sent.count { it.kind == DayPlan.Kind.FIFTY.name }, at(day, s.fiftyFrom), at(day, s.fiftyTo))
        val plan = DayPlan.plan(fixed, windows, s.noticeBudget, sent.map { it.at }, now)

        // the questions of the moment still to come are those of this plan, and so are the 50-50
        db.deleteFuturePrompts(now)
        plan.filter { it.kind == DayPlan.Kind.ESM }.forEach { db.insertPrompt(it.at) }
        s.fiftyPlanDay = key; s.fiftyPlan = plan.filter { it.kind == DayPlan.Kind.FIFTY }.map { it.at }
        s.dayPlanKey = key; s.dayPlanFrom = from; s.dayPlan = DayPlan.encode(plan)
        return plan
    }

    /** Today's plan as it stands, for the settings to show. */
    fun planned(ctx: Context): List<DayPlan.Planned> = DayPlan.decode(ctx.app.settings.dayPlan).takeIf { ctx.app.settings.dayPlanKey == today() } ?: emptyList()

    /** Nearer than this, an alarm without exact timing is set to wake the phone even in Doze. */
    private const val NEAR = 20 * 60_000L
    /** The earliest alarm armed in this refresh that is still too far off to be set while-idle; [armHop] steps towards it. */
    private var hopFor = Long.MAX_VALUE

    /**
     * Fires at the time asked: exactly when the person allowed exact alarms. Without that permission (the default on
     * new installs) there are only inexact alarms, and they have two ways to be late:
     * - setWindow is held back while the phone lies still in Doze, until the next maintenance window, which can be
     *   hours away;
     * - setAndAllowWhileIdle fires in Doze, but Android may move it by up to three quarters of the time left until it
     *   (at least ten minutes), so set hours ahead it can also be hours late.
     * So an alarm near at hand (under twenty minutes) is set while-idle, at most about fifteen minutes late; one further
     * off is set with setWindow, which is on time while the phone is in use, and [armHop] wakes the app on the way to it
     * so it can be set while-idle once it is near.
     */
    private fun arm(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (am.canScheduleExactAlarms()) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi); return }
        if (at - System.currentTimeMillis() <= NEAR) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else { am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi); hopFor = minOf(hopFor, at) }
    }

    /**
     * A while-idle wake halfway to the nearest far alarm. Android may move it by up to three quarters of its own
     * distance (at least ten minutes), so it lands between halfway and seven eighths of the way, and never past the
     * alarm while that is more than [NEAR] away; there [refresh] runs again and steps closer. Six or so steps cover a day.
     */
    private fun armHop(ctx: Context, am: AlarmManager) {
        val pi = pending(ctx, ACTION_HOP, 0)
        if (hopFor == Long.MAX_VALUE) { am.cancel(pi); return }
        val now = System.currentTimeMillis()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + (hopFor - now) / 2, pi)
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

    /** Yesterday's hard note still waiting for its question today, if any (see [com.grungert.nebo.koan.FollowUp]). */
    fun followUpNote(ctx: Context): com.grungert.nebo.data.Entry? {
        if (!ctx.app.settings.followUp) return null
        val today = midnight()
        val from = midnight(-1).timeInMillis
        return com.grungert.nebo.koan.FollowUp.pending(ctx.app.db.entries(from), today)
    }

    /** True when today's evening look back has been written. */
    fun eveningDone(ctx: Context): Boolean = ctx.app.db.entries(midnight().timeInMillis).any { it.kind == "evening" }

    /**
     * A gentle 50-50 reminder; a touch opens the one-minute practice. Each one is kept: when it came, the minute it led
     * to, or that it was swiped away, so the overview can say how often the pause was taken when it was offered.
     */
    private fun notifyFifty(ctx: Context) {
        val call = ctx.app.db.insertFiftyCall(System.currentTimeMillis())
        val open = PendingIntent.getActivity(ctx, 11, MainActivity.own(ctx).putExtra(EXTRA_FIFTY, true).putExtra(EXTRA_FIFTY_CALL, call)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val line = FIFTY_LINES.random()
        ctx.getSystemService(NotificationManager::class.java).notify(FIFTY_NOTIFICATION_ID, builder(ctx, FIFTY_CHANNEL).setContentTitle("50-50").setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line + "\nDodirni za vježbu od jednog minuta."))
            .setContentIntent(open).setDeleteIntent(pending(ctx, ACTION_FIFTY_DISMISS, call))
            .setAutoCancel(true).setTimeoutAfter(20 * 60_000L).build())
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
        // (a swipe-away is one per message: each its own, so a later one does not take over an earlier one's id)
        PendingIntent.getBroadcast(ctx, (action + (mood ?: "") + (if (action == ACTION_NOTICE_DISMISS) id.toString() else "")).hashCode(),
            Intent(ctx, EsmReceiver::class.java).setAction(action).putExtra(EXTRA_PROMPT, id).putExtra(EXTRA_MOOD, mood),
            (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE) or PendingIntent.FLAG_UPDATE_CURRENT)

    internal fun onAlarm(ctx: Context, intent: Intent) {
        val s = ctx.app.settings
        // a moment asked for later comes back here as itself
        if (intent.action == ACTION_LATER) {
            val what = intent.getStringExtra(EXTRA_WHAT) ?: return
            return onAlarm(ctx, Intent(intent).setAction(what))
        }
        // a message swiped away: kept as such, so the settings can say which kinds are mostly turned down
        if (intent.action == ACTION_NOTICE_DISMISS) { intent.getLongExtra(EXTRA_PROMPT, 0).takeIf { it > 0 }?.let { ctx.app.db.noticeDismissed(it) }; return }
        val sitting = ctx.app.sitting
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                // in a sit the question waits, and so it does when another notification just came; but never past the end
                // of its window: then it is only missed
                if ((sitting || crowded(ctx)) && ctx.app.db.prompt(id)?.firedAt == null) {
                    val later = System.currentTimeMillis() + 20 * 60_000L
                    if (later <= at(midnight(), s.esmEndHour)) { ctx.app.db.reschedulePrompt(id, later); return refresh(ctx) }
                    ctx.app.db.markPromptFired(id, System.currentTimeMillis())
                    return refresh(ctx)
                }
                // a prompt taken away by a change in the settings, or already shown, stays quiet
                val fresh = ctx.app.db.prompt(id)?.let { it.firedAt == null } == true
                ctx.app.db.markPromptFired(id, System.currentTimeMillis())
                if (fresh && s.esmEnabled && allowed(ctx, DayPlan.Kind.ESM)) { shown(ctx, DayPlan.Kind.ESM); notify(ctx, id) }
            }
            ACTION_FIFTY_DISMISS -> {
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                if (id > 0) ctx.app.db.fiftyCallDismissed(id)
                return
            }
            ACTION_DISMISS -> {
                val id = intent.getLongExtra(EXTRA_PROMPT, 0)
                if (id > 0) ctx.app.db.markPromptDismissed(id)
                return
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
                nm.notify(NOTIFICATION_ID, builder(ctx, CHANNEL).setOnlyAlertOnce(true)
                    .setContentTitle("Zapisano").setContentText("Hvala. Vrati se onome što si radio.").setTimeoutAfter(2500).build())
                // its themes are found when the app is next opened, not here, where the meaning model would load for one note
                return
            }
            ACTION_REMIND, ACTION_SNOOZE -> {
                // a sit already under way is what the reminder asks for; and a start of a few seconds is no sit
                val doneToday = sitting || ctx.app.db.sessions(midnight().timeInMillis).any { it.actualSec >= 30 }
                ctx.getSystemService(NotificationManager::class.java).cancel(REMIND_NOTIFICATION_ID)
                if (s.reminderOn && !(s.reminderSkipIfDone && doneToday)) {
                    val notice = shown(ctx, DayPlan.Kind.REMIND)
                    val open = PendingIntent.getActivity(ctx, 4, opens(ctx, notice)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    // what is for you now, as in the app and on the widget
                    val entries = ctx.app.db.entries()
                    val now = com.grungert.nebo.ui.suggestNow(entries, com.grungert.nebo.koan.WhatHelps.find(ctx.app.db.sessions(), entries),
                        com.grungert.nebo.koan.Links.part(System.currentTimeMillis()))
                    val start = PendingIntent.getActivity(ctx, 5, MainActivity.own(ctx).putExtra(com.grungert.nebo.widget.NeboWidget.EXTRA_START_TYPE, now.type)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val name = com.grungert.nebo.ui.SESSION_TYPES.firstOrNull { it.id == now.type }?.name ?: "Kreni"
                    // or just the three-minute breathing space, for a full day
                    val short = PendingIntent.getActivity(ctx, 6, MainActivity.own(ctx).putExtra(EXTRA_HOURGLASS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(NotificationManager::class.java).notify(REMIND_NOTIFICATION_ID, builder(ctx, REMIND_CHANNEL).setContentTitle("Vrijeme za tišinu · $name")
                        .setContentText(now.why).setStyle(Notification.BigTextStyle().bigText(now.why)).setContentIntent(open).setAutoCancel(true)
                        .setDeleteIntent(pending(ctx, ACTION_NOTICE_DISMISS, notice))
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
            ACTION_DAILY, ACTION_EVENING, ACTION_FOLLOW, ACTION_WEEK ->
                if (sitting) { later(ctx, intent.action!!, intent.getLongExtra(EXTRA_FIRST, System.currentTimeMillis())); return } else dayNote(ctx, intent)
            ACTION_FIFTY -> if (s.fiftyOn) {
                // after a sit, or when another just came, a little later; never past the end of its window
                val first = intent.getLongExtra(EXTRA_FIRST, System.currentTimeMillis())
                if (sitting || crowded(ctx)) later(ctx, ACTION_FIFTY, first, at(midnight(), s.fiftyTo))
                else if (allowed(ctx, DayPlan.Kind.FIFTY)) { shown(ctx, DayPlan.Kind.FIFTY); notifyFifty(ctx) }
            }
            ACTION_SILENCE -> {
                s.esmSilencedDay = today()
                ctx.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            }
        }
        refresh(ctx)
    }

    /** The notes of the day: a question for the morning, the evening's look back, the next-day return, the week. */
    private fun dayNote(ctx: Context, intent: Intent) {
        val s = ctx.app.settings
        val kind = when (intent.action) { ACTION_DAILY -> DayPlan.Kind.DAILY; ACTION_WEEK -> DayPlan.Kind.WEEK; ACTION_FOLLOW -> DayPlan.Kind.FOLLOW; else -> DayPlan.Kind.EVENING }
        if (!allowed(ctx, kind)) return
        when (intent.action) {
            ACTION_DAILY -> {
                val lib = ctx.app.library
                // the week's koan question when one is being carried, otherwise a question from the library
                val item = lib.byId(s.activeWeekKoan()) ?: lib.items.filter { it.question != null }.randomOrNull()   // koans and readings carry a question
                val q = item?.question
                if (s.dailyQuestion && q != null) {
                    val notice = shown(ctx, kind)
                    val open = PendingIntent.getActivity(ctx, 3, opens(ctx, notice)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    ctx.getSystemService(NotificationManager::class.java).notify(DAILY_NOTIFICATION_ID, builder(ctx, REMIND_CHANNEL).setContentTitle("Pitanje za danas").setContentText(q)
                        .setStyle(Notification.BigTextStyle().bigText(q + (item.title?.let { "\n— $it" } ?: "")))
                        .setContentIntent(open).setDeleteIntent(pending(ctx, ACTION_NOTICE_DISMISS, notice)).setAutoCancel(true).build())
                }
            }
            ACTION_WEEK -> {
                val notice = if (s.weekReviewed != weekKey(reviewWeekStart())) shown(ctx, kind) else 0L
                val open = PendingIntent.getActivity(ctx, 2, opens(ctx, notice).putExtra(EXTRA_WEEK, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                if (s.weekReviewed != weekKey(reviewWeekStart())) {
                    ctx.getSystemService(NotificationManager::class.java).notify(WEEK_NOTIFICATION_ID, builder(ctx, REMIND_CHANNEL).setContentTitle("Sedmica je iza tebe")
                        .setContentText("Pogledaj šta se vraćalo, i šta ti primjećuješ.").setContentIntent(open)
                        .setDeleteIntent(pending(ctx, ACTION_NOTICE_DISMISS, notice)).setAutoCancel(true).build())
                }
            }
            ACTION_FOLLOW -> followUpNote(ctx)?.takeIf { s.followUpAsked != it.id }?.let { note ->
                s.followUpAsked = note.id
                val notice = shown(ctx, kind)
                val open = PendingIntent.getActivity(ctx, 8, opens(ctx, notice).putExtra(EXTRA_FOLLOW, note.id)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val about = com.grungert.nebo.koan.FollowUp.about(note)
                ctx.getSystemService(NotificationManager::class.java).notify(FOLLOW_NOTIFICATION_ID, builder(ctx, REMIND_CHANNEL).setContentTitle("Kako je danas?")
                    .setContentText(if (about != null) "Juče ti je bilo teško ($about)." else "Juče ti je bilo teško.")
                    .setContentIntent(open).setDeleteIntent(pending(ctx, ACTION_NOTICE_DISMISS, notice)).setAutoCancel(true).setTimeoutAfter(6 * 60 * 60_000L).build())
            }
            ACTION_EVENING -> if (s.eveningReview && !eveningDone(ctx)) {
                val notice = shown(ctx, kind)
                val open = PendingIntent.getActivity(ctx, 7, opens(ctx, notice).putExtra(EXTRA_EVENING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                ctx.getSystemService(NotificationManager::class.java).notify(EVENING_NOTIFICATION_ID, builder(ctx, REMIND_CHANNEL).setContentTitle("Kako je prošao dan?")
                    .setContentText("Šta je bilo dobro, šta teško, i šta sutra drugačije.").setContentIntent(open).setAutoCancel(true)
                    .setDeleteIntent(pending(ctx, ACTION_NOTICE_DISMISS, notice))
                    .setTimeoutAfter(4 * 60 * 60_000L).build())
            }
        }
    }

    private const val NOTIFICATION_ID = 7
    private const val WEEK_NOTIFICATION_ID = 8
    private const val DAILY_NOTIFICATION_ID = 9
    private const val REMIND_NOTIFICATION_ID = 10
    private const val FIFTY_NOTIFICATION_ID = 12
    private const val EVENING_NOTIFICATION_ID = 13
    private const val FOLLOW_NOTIFICATION_ID = 14

    private fun notify(ctx: Context, promptId: Long) {
        val open = PendingIntent.getActivity(ctx, 1, MainActivity.own(ctx).putExtra(EXTRA_PROMPT, promptId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // answering writes into the journal: not from a locked phone in someone else's hand
        val reply = Notification.Action.Builder(null, "Napiši", pending(ctx, ACTION_REPLY, promptId, mutable = true)).setAuthenticationRequired(true)
            .addRemoteInput(android.app.RemoteInput.Builder(KEY_TEXT).setLabel("Šta je u tebi?").build()).build()
        fun mood(m: String) = Notification.Action.Builder(null, m, pending(ctx, ACTION_REPLY, promptId, mood = m)).setAuthenticationRequired(true).build()
        val n = builder(ctx, CHANNEL)
            .setContentTitle("Kako si sada?")
            .setContentText("Dodirni da govoriš, ili odgovori odmah ovdje.")
            .setContentIntent(open)
            // swiped away unanswered: kept as such, so the answers can be told from the questions turned down
            .setDeleteIntent(pending(ctx, ACTION_DISMISS, promptId))
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
