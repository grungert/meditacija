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

    data class Summary(val created: Long, val entries: Int, val sessions: Int, val recordings: Int)

    fun fileName() = "nebo-kopija-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".zip"

    /** One copy at a time: the weekly copy and one asked for by hand never share the snapshot file. */
    fun write(ctx: Context, out: OutputStream): Summary = synchronized(writing) { writeNow(ctx, out) }

    private fun writeNow(ctx: Context, out: OutputStream): Summary {
        val app = ctx.app
        // a consistent snapshot of the live database, without closing it
        val snap = File(ctx.cacheDir, "backup-snapshot.db").also { it.delete() }
        app.db.writableDatabase.execSQL("VACUUM INTO '" + snap.absolutePath.replace("'", "''") + "'")
        val audio = File(ctx.filesDir, "audio").listFiles()?.filter { it.isFile } ?: emptyList()
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
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.name == "end") { complete = true; continue }
                    val target = File(staging, e.name)
                    // never write outside the staging folder, whatever the zip says
                    if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                    if (e.isDirectory) { target.mkdirs(); continue }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { z.copyTo(it) }
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
     */
    fun applyPending(ctx: Context) {
        val staging = staging(ctx)
        if (!File(staging, "ready").exists()) { if (staging.exists()) staging.deleteRecursively(); return }
        val dbFile = ctx.getDatabasePath("nebo.db")
        val bak = File(dbFile.path + ".bak")
        val next = File(dbFile.path + ".restore")
        try {
            File(staging, "nebo.db").inputStream().use { i -> java.io.FileOutputStream(next).use { o -> i.copyTo(o); o.fd.sync() } }
            listOf("", "-wal").forEach { File(bak.path + it).delete() }
            if (dbFile.exists()) dbFile.renameTo(bak)
            File(dbFile.path + "-wal").let { if (it.exists()) it.renameTo(File(bak.path + "-wal")) }
            listOf("-shm", "-journal").forEach { File(dbFile.path + it).delete() }
            check(next.renameTo(dbFile)) { "rename" }
        } catch (e: Exception) {
            // put the old journal back exactly as it was
            next.delete()
            if (!dbFile.exists() && bak.exists()) { bak.renameTo(dbFile); File(bak.path + "-wal").renameTo(File(dbFile.path + "-wal")) }
            staging.deleteRecursively()
            return
        }
        File(staging, "settings.xml").takeIf { it.exists() }?.let { prefs ->
            // the person's choices come back; what belonged to the other phone stays behind
            val xml = prefs.readText().replace(Regex("\\s*<(\\w+) name=\"([^\"]+)\"[^>]*?(/>|>[^<]*</\\1>)")) { m ->
                if (DEVICE_KEYS.matches(m.groupValues[2])) "" else m.value
            }
            File(ctx.dataDir, "shared_prefs").mkdirs()
            File(ctx.dataDir, "shared_prefs/nebo.xml").writeText(xml)
        }
        val audioDir = File(ctx.filesDir, "audio").also { it.mkdirs() }
        File(staging, "audio").listFiles()?.forEach { runCatching { it.copyTo(File(audioDir, it.name), overwrite = true) } }
        staging.deleteRecursively()
    }

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
