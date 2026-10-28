package com.grungert.nebo.data

import android.content.Context
import kotlinx.coroutines.launch
import androidx.core.content.edit

/** Small preferences that make the next session start the way the last one did. */
class Settings(private val context: Context) {
    private val p = context.getSharedPreferences("nebo", Context.MODE_PRIVATE)

    var sessionType: String
        get() = p.getString("sessionType", "breath")!!
        set(v) = p.edit { putString("sessionType", v) }
    var minutes: Int
        get() = p.getInt("minutes", 10)
        set(v) = p.edit { putInt("minutes", v) }
    var pattern: String
        get() = p.getString("pattern", "46")!!
        set(v) = p.edit { putString("pattern", v) }
    /** "clock", "cycle" or a fixed hour such as "18.9". */
    var timeMode: String
        get() = p.getString("timeMode", "clock")!!
        set(v) = p.edit { putString("timeMode", v) }
    /** "auto" follows the calendar, or a season name. */
    var seasonMode: String
        get() = p.getString("seasonMode", "auto")!!
        set(v) = p.edit { putString("seasonMode", v) }
    /** "auto" lets the day decide, or a weather name. */
    var weatherMode: String
        get() = p.getString("weatherMode", "auto")!!
        set(v) = p.edit { putString("weatherMode", v) }
    /** Minutes between soft bells during a session: 0 none, -1 at random moments (every 2–6 minutes). */
    var intervalMinutes: Int
        get() = if (p.contains("intervalMinutes")) p.getInt("intervalMinutes", 0) else if (p.getBoolean("intervalBell", false)) 5 else 0
        set(v) = p.edit { putInt("intervalMinutes", v) }

    // a daily reminder to sit
    var reminderOn: Boolean
        get() = p.getBoolean("reminderOn", false)
        set(v) = p.edit { putBoolean("reminderOn", v) }
    var reminderHour: Int
        get() = p.getInt("reminderHour", 19)
        set(v) = p.edit { putInt("reminderHour", v) }
    var reminderMinute: Int
        get() = p.getInt("reminderMinute", 0)
        set(v) = p.edit { putInt("reminderMinute", v) }
    /** "all", "weekdays" or "weekend". */
    var reminderDays: String
        get() = p.getString("reminderDays", "all")!!
        set(v) = p.edit { putString("reminderDays", v) }
    /** Stay quiet on days that already had a session. */
    var reminderSkipIfDone: Boolean
        get() = p.getBoolean("reminderSkipIfDone", true)
        set(v) = p.edit { putBoolean("reminderSkipIfDone", v) }
    var checkInBefore: Boolean
        get() = p.getBoolean("checkInBefore", false)
        set(v) = p.edit { putBoolean("checkInBefore", v) }
    var checkInAfter: Boolean
        get() = p.getBoolean("checkInAfter", true)
        set(v) = p.edit { putBoolean("checkInAfter", v) }
    /** A few large dark blades along the bottom edge of the meadow. */
    var meadowFront: Boolean
        get() = p.getBoolean("meadowFront", true)
        set(v) = p.edit { putBoolean("meadowFront", v) }
    /** Sit by the still lake instead of in the meadow (every session but the walk, which goes through the meadow). */
    var lakeScene: Boolean
        get() = p.getBoolean("lakeScene", false)
        set(v) = p.edit { putBoolean("lakeScene", v) }
    /** A soft rush of air that follows the breath in paced sessions. */
    var breathSound: Boolean
        get() = p.getBoolean("breathSound", true)
        set(v) = p.edit { putBoolean("breathSound", v) }
    /** How long the phone took to load a speech model, and seconds of work per second of audio, as last measured. */
    fun speechLoadSeconds(model: String) = p.getFloat("speechLoad_$model", if (model == "parakeet") 8f else 3f)
    fun speechRate(model: String) = p.getFloat("speechRate_$model", if (model == "parakeet") 0.3f else 1.2f)
    fun measuredSpeech(model: String, loadSeconds: Float? = null, rate: Float? = null) = p.edit {
        loadSeconds?.let { putFloat("speechLoad_$model", it) }
        rate?.let { putFloat("speechRate_$model", it) }
    }
    /** The person's own labels for a point (feelings, the body, urges), one per line, as they added them. */
    fun customLabels(point: String): List<String> = p.getString("custom_$point", "")!!.split("\n").filter { it.isNotBlank() }
    fun setCustomLabels(point: String, labels: List<String>) = p.edit { putString("custom_$point", labels.distinct().joinToString("\n")) }
    var ambient: Boolean
        get() = p.getBoolean("ambient", true)
        set(v) = p.edit { putBoolean("ambient", v) }
    var ambientVolume: Float
        get() = p.getFloat("ambientVolume", 0.6f)
        set(v) = p.edit { putFloat("ambientVolume", v) }
    var eyesClosed: Boolean
        get() = p.getBoolean("eyesClosed", false)
        set(v) = p.edit { putBoolean("eyesClosed", v) }
    /** yyyyww of the last week whose review was done. */
    var weekReviewed: Int
        get() = p.getInt("weekReviewed", 0)
        set(v) = p.edit { putInt("weekReviewed", v) }
    var weekReminder: Boolean
        get() = p.getBoolean("weekReminder", true)
        set(v) = p.edit { putBoolean("weekReminder", v) }
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = p.edit { putBoolean("haptics", v) }

    // experience sampling
    /** 50-50: a few times a day, half the attention in the body and half on the world outside. */
    var fiftyOn: Boolean
        get() = p.getBoolean("fiftyOn", false)
        set(v) = p.edit { putBoolean("fiftyOn", v) }
    var fiftyPerDay: Int
        get() = p.getInt("fiftyPerDay", 4)
        set(v) = p.edit { putInt("fiftyPerDay", v) }
    var fiftyFrom: Int
        get() = p.getInt("fiftyFrom", 9)
        set(v) = p.edit { putInt("fiftyFrom", v) }
    var fiftyTo: Int
        get() = p.getInt("fiftyTo", 20)
        set(v) = p.edit { putInt("fiftyTo", v) }
    /** The planned moments of one day: the day (yyyymmdd) and the times. Planned anew when the settings change. */
    var fiftyPlanDay: Int
        get() = p.getInt("fiftyPlanDay", 0)
        set(v) = p.edit { putInt("fiftyPlanDay", v) }
    var fiftyPlan: List<Long>
        get() = p.getString("fiftyPlan", "")!!.split(',').mapNotNull { it.toLongOrNull() }
        set(v) = p.edit { putString("fiftyPlan", v.joinToString(",")) }
    var esmEnabled: Boolean
        get() = p.getBoolean("esmEnabled", false)
        set(v) = p.edit { putBoolean("esmEnabled", v) }
    var esmStartHour: Int
        get() = p.getInt("esmStart", 9)
        set(v) = p.edit { putInt("esmStart", v) }
    var esmEndHour: Int
        get() = p.getInt("esmEnd", 20)
        set(v) = p.edit { putInt("esmEnd", v) }
    var esmPerDay: Int
        get() = p.getInt("esmPerDay", 3)
        set(v) = p.edit { putInt("esmPerDay", v) }
    /** yyyyMMdd of a day the prompts are silenced, or 0. */
    /** A few words the person keeps in sight on the home screen, as a reminder to themself ("50-50"). */
    var selfNote: String
        get() = p.getString("selfNote", "")!!
        set(v) = p.edit { putString("selfNote", v) }

    var esmSilencedDay: Int
        get() = p.getInt("esmSilenced", 0)
        set(v) = p.edit { putInt("esmSilenced", v) }

    // the koan carried through a week
    var weekKoanId: String?
        get() = p.getString("weekKoanId", null)
        set(v) = p.edit { putString("weekKoanId", v) }
    var weekKoanSince: Long
        get() = p.getLong("weekKoanSince", 0)
        set(v) = p.edit { putLong("weekKoanSince", v) }
    /** The koan being carried this week, or null once seven days have passed. */
    fun activeWeekKoan(now: Long = System.currentTimeMillis()): String? =
        weekKoanId?.takeIf { now - weekKoanSince in 0 until 7 * 86_400_000L }
    fun carryKoan(id: String?) {
        weekKoanId = id; weekKoanSince = System.currentTimeMillis()
        // drawing the widget's sky takes a moment; not on the thread that answers the tap
        (context.applicationContext as com.grungert.nebo.NeboApp).scope.launch { com.grungert.nebo.widget.NeboWidget.refresh(context) }
    }
    /** Which day (1..7) of carrying the koan this is. */
    fun weekKoanDay(now: Long = System.currentTimeMillis()) = ((now - weekKoanSince) / 86_400_000L + 1).toInt()
    var dailyQuestion: Boolean
        get() = p.getBoolean("dailyQuestion", false)
        set(v) = p.edit { putBoolean("dailyQuestion", v) }
    var dailyHour: Int
        get() = p.getInt("dailyHour", 8)
        set(v) = p.edit { putInt("dailyHour", v) }
    /** Whether transcripts written in Cyrillic before Nebo kept Latin script have been rewritten. */
    var transcriptsLatin: Boolean
        get() = p.getBoolean("transcriptsLatin", false)
        set(v) = p.edit { putBoolean("transcriptsLatin", v) }
    /** Whether the first look at Nebo (the intro) has been seen. */
    var introSeen: Boolean
        get() = p.getBoolean("introSeen", false)
        set(v) = p.edit { putBoolean("introSeen", v) }
    /** Whether the person has started a session by holding its icon; until then Home says how. */
    var holdLearned: Boolean
        get() = p.getBoolean("holdLearned", false)
        set(v) = p.edit { putBoolean("holdLearned", v) }
    /** The day after a hard note, a question: how is it now? */
    var followUp: Boolean
        get() = p.getBoolean("followUp", true)
        set(v) = p.edit { putBoolean("followUp", v) }
    /** The note last asked about, so it is asked about once. */
    var followUpAsked: Long
        get() = p.getLong("followUpAsked", 0)
        set(v) = p.edit { putLong("followUpAsked", v) }
    /** An evening look back at the day, at the chosen hour. */
    var eveningReview: Boolean
        get() = p.getBoolean("eveningReview", false)
        set(v) = p.edit { putBoolean("eveningReview", v) }
    var eveningHour: Int
        get() = p.getInt("eveningHour", 21)
        set(v) = p.edit { putInt("eveningHour", v) }

    /** Bumped when the way themes are found changes, so every note is tagged again once. */
    var themesVersion: Int
        get() = p.getInt("themesVersion", 0)
        set(v) = p.edit { putInt("themesVersion", v) }
    /** Whether the notes' themes were found with the meaning model; once it arrives, they are found again with it. */
    var themesByMeaning: Boolean
        get() = p.getBoolean("themesByMeaning", false)
        set(v) = p.edit { putBoolean("themesByMeaning", v) }

    // backups
    /** A folder (document tree) chosen for the weekly automatic copy, or null. */
    var backupTree: String?
        get() = p.getString("backupTree", null)
        set(v) = p.edit { putString("backupTree", v) }
    var lastBackup: Long
        get() = p.getLong("lastBackup", 0)
        set(v) = p.edit { putLong("lastBackup", v) }

    // models
    var whisperModel: String
        get() = p.getString("whisperModel", "parakeet")!!
        set(v) = p.edit { putString("whisperModel", v) }
    /** Whisper language code; Croatian gives Latin-script ijekavian text closest to Montenegrin. */
    var whisperLang: String
        get() = p.getString("whisperLang", "hr")!!
        set(v) = p.edit { putString("whisperLang", v) }
    fun downloadId(name: String) = p.getLong("dl.$name", -1)
    fun setDownloadId(name: String, id: Long) = p.edit { putLong("dl.$name", id) }
}
