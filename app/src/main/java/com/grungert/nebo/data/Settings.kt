package com.grungert.nebo.data

import android.content.Context
import androidx.core.content.edit

/** Small preferences that make the next session start the way the last one did. */
class Settings(context: Context) {
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
    /** A soft rush of air that follows the breath in paced sessions. */
    var breathSound: Boolean
        get() = p.getBoolean("breathSound", true)
        set(v) = p.edit { putBoolean("breathSound", v) }
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
    fun carryKoan(id: String?) { weekKoanId = id; weekKoanSince = System.currentTimeMillis() }
    /** Which day (1..7) of carrying the koan this is. */
    fun weekKoanDay(now: Long = System.currentTimeMillis()) = ((now - weekKoanSince) / 86_400_000L + 1).toInt()
    var dailyQuestion: Boolean
        get() = p.getBoolean("dailyQuestion", false)
        set(v) = p.edit { putBoolean("dailyQuestion", v) }
    var dailyHour: Int
        get() = p.getInt("dailyHour", 8)
        set(v) = p.edit { putInt("dailyHour", v) }

    /** Bumped when the way themes are found changes, so every note is tagged again once. */
    var themesVersion: Int
        get() = p.getInt("themesVersion", 0)
        set(v) = p.edit { putInt("themesVersion", v) }

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
