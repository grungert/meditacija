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
    var intervalBell: Boolean
        get() = p.getBoolean("intervalBell", false)
        set(v) = p.edit { putBoolean("intervalBell", v) }
    var checkInBefore: Boolean
        get() = p.getBoolean("checkInBefore", false)
        set(v) = p.edit { putBoolean("checkInBefore", v) }
    var checkInAfter: Boolean
        get() = p.getBoolean("checkInAfter", true)
        set(v) = p.edit { putBoolean("checkInAfter", v) }
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
        get() = p.getString("whisperModel", "small")!!
        set(v) = p.edit { putString("whisperModel", v) }
    /** Whisper language code; Croatian gives Latin-script ijekavian text closest to Montenegrin. */
    var whisperLang: String
        get() = p.getString("whisperLang", "hr")!!
        set(v) = p.edit { putString("whisperLang", v) }
    fun downloadId(name: String) = p.getLong("dl.$name", -1)
    fun setDownloadId(name: String, id: Long) = p.edit { putLong("dl.$name", id) }
}
