package com.grungert.nebo.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.grungert.nebo.MainActivity
import com.grungert.nebo.app
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything worth keeping in one zip: the database, every recording and the preferences.
 * Models are left out; they can always be downloaded again.
 */
object Backup {
    private const val FORMAT = "nebo-backup"
    private const val VERSION = 1
    /** Preferences that describe this phone rather than the person: a folder permission, downloads, measured speeds. */
    private val DEVICE_KEYS = Regex("^(backupTree|lastBackup|dl\\..*|speechLoad_.*|speechRate_.*)$")
    private val writing = Any()
    /** The names a Nebo copy holds; anything else in the zip is passed over. */
    private val KNOWN = Regex("^(manifest\\.json|nebo\\.db|settings\\.xml|audio/[^/\\\\]+)$")

    data class Summary(val created: Long, val entries: Int, val sessions: Int, val recordings: Int)

    fun fileName() = "nebo-kopija-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".zip"

    /** One copy at a time: the weekly copy and one asked for by hand never share the snapshot file. */
    fun write(ctx: Context, out: OutputStream): Summary = synchronized(writing) { writeNow(ctx, out) }

    private fun writeNow(ctx: Context, out: OutputStream): Summary {
        val app = ctx.app
        // a consistent snapshot of the live database, without closing it
        val snap = File(ctx.cacheDir, "backup-snapshot.db").also { it.delete() }
        app.db.writableDatabase.execSQL("VACUUM INTO '" + snap.absolutePath.replace("'", "''") + "'")
        // only the recordings a note still refers to: one whose note was deleted or never saved stays behind
        val used = app.db.entries().mapNotNull { e -> e.audioPath?.let { File(it).name } }.toSet()
        val audio = File(ctx.filesDir, "audio").listFiles()?.filter { it.isFile && it.name in used } ?: emptyList()
        val summary = Summary(System.currentTimeMillis(), app.db.entries().size, app.db.sessions().size, audio.size)
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(JSONObject().put("format", FORMAT).put("version", VERSION).put("schema", SCHEMA).put("created", summary.created)
                .put("entries", summary.entries).put("sessions", summary.sessions).put("recordings", summary.recordings)
                .toString(1).toByteArray())
            z.closeEntry()
            fun add(name: String, f: File) { if (f.exists()) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
            add("nebo.db", snap)
            add("settings.xml", File(ctx.dataDir, "shared_prefs/nebo.xml"))
            audio.forEach { add("audio/" + it.name, it) }
            // last, so a copy cut short anywhere is recognised as incomplete
            z.putNextEntry(ZipEntry("end")); z.closeEntry()
        }
        snap.delete()
        return summary
    }

    /** Reads only the manifest, to show what a backup holds before anything is replaced. */
    fun inspect(input: InputStream): Summary? = runCatching {
        ZipInputStream(input.buffered()).use { z ->
            generateSequence { z.nextEntry }.firstOrNull { it.name == "manifest.json" } ?: return null
            val m = JSONObject(z.readBytes().decodeToString())
            if (m.optString("format") != FORMAT) return null
            Summary(m.getLong("created"), m.optInt("entries"), m.optInt("sessions"), m.optInt("recordings"))
        }
    }.getOrNull()

    private fun staging(ctx: Context) = File(ctx.filesDir, "restore-pending")

    /**
     * Unpacks and checks the backup, then restarts the app; the swap itself happens at the next start, in
     * [applyPending], before anything has opened the database. Nothing on the phone is touched until the backup has
     * proved whole and readable, so a bad or cut-short file leaves the journal as it was.
     */
    fun restore(ctx: Context, input: InputStream) {
        val staging = staging(ctx).also { it.deleteRecursively(); it.mkdirs() }
        try {
            var complete = false
            // the swap needs about twice the copy's size again: what is unpacked must leave room for that
            val room = ctx.filesDir.usableSpace / 3
            var written = 0L
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name == "end") { complete = true; continue }
                    // only what a Nebo copy holds: never "ready" or anything else that would steer the swap
                    if (!KNOWN.matches(e.name) || e.isDirectory) continue
                    val target = File(staging, e.name)
                    // never write outside the staging folder, whatever the zip says
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    target.parentFile?.mkdirs()
                    target.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = z.read(buf); if (n < 0) break
                            written += n
                            require(written < room) { "Na telefonu nema dovoljno mjesta za ovu kopiju." }
                            o.write(buf, 0, n)
                        }
                    }
                }
            }
            val manifest = File(staging, "manifest.json").takeIf { it.exists() }?.let { JSONObject(it.readText()) }
            require(manifest?.optString("format") == FORMAT) { "Ovo nije Nebo kopija." }
            // copies made before the end marker existed are taken as they are; newer ones must reach it
            require(complete || !manifest!!.has("schema")) { "Kopija nije cijela." }
            val db = File(staging, "nebo.db")
            require(db.exists()) { "U kopiji nema baze." }
            verify(db)
            File(staging, "ready").createNewFile()
        } catch (e: Exception) {
            staging.deleteRecursively()
            // a zip that breaks off while being read says so in the zip library's own words; say it plainly instead
            throw if (e is java.io.IOException) IllegalArgumentException("Kopija nije cijela ili je oštećena.", e) else e
        }
        restart(ctx)
    }

    /** The backup's database must open, hold Nebo's tables, and not come from a newer Nebo than this one. */
    private fun verify(db: File) {
        val sql = android.database.sqlite.SQLiteDatabase.openDatabase(db.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
        try {
            val version = sql.version
            require(version in 1..SCHEMA) { "Kopija je iz novije verzije Neba; prvo ažuriraj aplikaciju." }
            val tables = sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0) else null }.toSet()
            }
            require(tables.containsAll(listOf("entries", "sessions", "prompts"))) { "U kopiji nema Nebo zapisa." }
            sql.rawQuery("PRAGMA quick_check", null).use { c -> require(c.moveToNext() && c.getString(0) == "ok") { "Baza u kopiji je oštećena." } }
        } finally { sql.close() }
    }

    /**
     * Swaps in a checked backup, if one waits. Runs first thing at start, before the database or the preferences are
     * opened. The journal it replaces is kept beside it as nebo.db.bak until the next restore.
     *
     * It goes in two steps, each marked in the staging folder, so a restore cut short (the phone full, the app closed)
     * picks up where it stopped at the next start instead of starting over: "ready" swaps the database, "swapped" brings
     * the settings and the recordings. It never throws: a start that failed here would fail at every start after it.
     * After three failed tries it gives up, leaves the journal as it then is and says so in Settings.
     */
    fun applyPending(ctx: Context) {
        val staging = staging(ctx)
        val ready = File(staging, "ready"); val swapped = File(staging, "swapped")
        if (!ready.exists() && !swapped.exists()) { if (staging.exists()) staging.deleteRecursively(); return }
        val tries = File(staging, "tries")
        val n = (runCatching { tries.readText().trim().toInt() }.getOrDefault(0)) + 1
        runCatching { tries.writeText(n.toString()) }
        if (n > 3) { failed(ctx); runCatching { staging.deleteRecursively() }; return }
        try {
            if (!swapped.exists()) {
                if (!swapDatabase(ctx, staging)) { failed(ctx); staging.deleteRecursively(); return }
                swapped.createNewFile(); ready.delete()
            }
            restoreSettings(ctx, staging)
            restoreRecordings(ctx, staging)
            staging.deleteRecursively()
            File(ctx.filesDir, FAILED).delete()
        } catch (e: Exception) {
            android.util.Log.w("Nebo", "restore step failed, will try again at the next start", e)
        }
    }

    /** True when the backup's database now stands in the journal's place; false (and the journal as it was) otherwise. */
    private fun swapDatabase(ctx: Context, staging: File): Boolean {
        val dbFile = ctx.getDatabasePath("nebo.db")
        val bak = File(dbFile.path + ".bak")
        val next = File(dbFile.path + ".restore")
        return try {
            File(staging, "nebo.db").inputStream().use { i -> java.io.FileOutputStream(next).use { o -> i.copyTo(o); o.fd.sync() } }
            listOf("", "-wal").forEach { File(bak.path + it).delete() }
            if (dbFile.exists()) check(dbFile.renameTo(bak)) { "keep" }
            File(dbFile.path + "-wal").let { if (it.exists()) it.renameTo(File(bak.path + "-wal")) }
            listOf("-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            check(next.renameTo(dbFile)) { "rename" }
            true
        } catch (e: Exception) {
            // put the old journal back exactly as it was
            next.delete()
            if (!dbFile.exists() && bak.exists()) { bak.renameTo(dbFile); File(bak.path + "-wal").renameTo(File(dbFile.path + "-wal")) }
            false
        }
    }

    private fun restoreSettings(ctx: Context, staging: File) {
        val prefs = File(staging, "settings.xml").takeIf { it.exists() } ?: return
        // the person's choices come back; what belonged to the other phone stays behind
        val xml = prefs.readText().replace(Regex("\\s*<(\\w+) name=\"([^\"]+)\"[^>]*?(/>|>[^<]*</\\1>)")) { m ->
            if (DEVICE_KEYS.matches(m.groupValues[2])) "" else m.value
        }
        File(ctx.dataDir, "shared_prefs").mkdirs()
        val target = File(ctx.dataDir, "shared_prefs/nebo.xml")
        val tmp = File(target.path + ".restore")
        tmp.writeText(xml)
        check(tmp.renameTo(target)) { "prefs" }
        prefs.delete()
    }

    /**
     * The backup's recordings take their place (moved, not copied: no second copy of every recording is needed, and a
     * step cut short leaves only what is still to move). Recordings no note of the restored journal refers to go.
     */
    private fun restoreRecordings(ctx: Context, staging: File) {
        val audioDir = File(ctx.filesDir, "audio").also { it.mkdirs() }
        File(staging, "audio").listFiles()?.forEach { f ->
            val to = File(audioDir, f.name)
            if (!f.renameTo(to)) { f.copyTo(to, overwrite = true); f.delete() }
        }
        val used = runCatching {
            val sql = android.database.sqlite.SQLiteDatabase.openDatabase(ctx.getDatabasePath("nebo.db").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY)
            try {
                sql.rawQuery("SELECT audio_path FROM entries WHERE audio_path IS NOT NULL", null).use { c ->
                    generateSequence { if (c.moveToNext()) File(c.getString(0)).name else null }.toSet()
                }
            } finally { sql.close() }
        }.getOrNull() ?: return
        audioDir.listFiles()?.filter { it.isFile && it.name !in used }?.forEach { it.delete() }
    }

    private const val FAILED = "restore-failed"
    private fun failed(ctx: Context) { runCatching { File(ctx.filesDir, FAILED).createNewFile() } }

    /** A restore that could not be finished: the journal stayed as it was (or as far as it got). Said once in Settings. */
    fun lastRestoreFailed(ctx: Context): Boolean = File(ctx.filesDir, FAILED).let { f -> f.exists().also { if (it) f.delete() } }

    private fun restart(ctx: Context) {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ctx.startActivity(i)
        Runtime.getRuntime().exit(0)
    }

    /**
     * Weekly automatic copy into a folder the person chose (kept across app reinstalls), keeping the last four.
     * Returns true when a copy was written.
     */
    fun autoIfDue(ctx: Context, force: Boolean = false): Boolean {
        val s = ctx.app.settings
        val tree = s.backupTree?.let(Uri::parse) ?: return false
        if (!force && System.currentTimeMillis() - s.lastBackup < 7 * 86_400_000L) return false
        return runCatching {
            val r = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val doc = DocumentsContract.createDocument(r, parent, "application/zip", fileName()) ?: return false
            // a copy that fails halfway is removed, never left to be counted among the four kept
            try { r.openOutputStream(doc)!!.use { write(ctx, it) } }
            catch (e: Exception) { runCatching { DocumentsContract.deleteDocument(r, doc) }; throw e }
            s.lastBackup = System.currentTimeMillis()
            prune(ctx, tree)
            true
        }.getOrDefault(false)
    }

    private fun prune(ctx: Context, tree: Uri) {
        val r = ctx.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, String>>()
        r.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) found += c.getString(0) to c.getString(1)
        }
        found.filter { it.second.startsWith("nebo-kopija-") }.sortedByDescending { it.second }.drop(4).forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(r, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }
}
