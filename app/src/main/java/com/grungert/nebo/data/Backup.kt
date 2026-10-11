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

    data class Summary(val created: Long, val entries: Int, val sessions: Int, val recordings: Int)

    fun fileName() = "nebo-kopija-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date()) + ".zip"

    fun write(ctx: Context, out: OutputStream): Summary {
        val app = ctx.app
        // a consistent snapshot of the live database, without closing it
        val snap = File(ctx.cacheDir, "backup-snapshot.db").also { it.delete() }
        app.db.writableDatabase.execSQL("VACUUM INTO '" + snap.absolutePath.replace("'", "''") + "'")
        val audio = File(ctx.filesDir, "audio").listFiles()?.filter { it.isFile } ?: emptyList()
        val summary = Summary(System.currentTimeMillis(), app.db.entries().size, app.db.sessions().size, audio.size)
        ZipOutputStream(out.buffered()).use { z ->
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(JSONObject().put("format", FORMAT).put("version", VERSION).put("created", summary.created)
                .put("entries", summary.entries).put("sessions", summary.sessions).put("recordings", summary.recordings)
                .toString(1).toByteArray())
            z.closeEntry()
            fun add(name: String, f: File) { if (f.exists()) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() } }
            add("nebo.db", snap)
            add("settings.xml", File(ctx.dataDir, "shared_prefs/nebo.xml"))
            audio.forEach { add("audio/" + it.name, it) }
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

    /** Replaces the current notes with the backup's, then restarts the app so everything reopens cleanly. */
    fun restore(ctx: Context, input: InputStream) {
        val staging = File(ctx.cacheDir, "restore").also { it.deleteRecursively(); it.mkdirs() }
        ZipInputStream(input.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val target = File(staging, e.name)
                // never write outside the staging folder, whatever the zip says
                if (!target.canonicalPath.startsWith(staging.canonicalPath + File.separator)) continue
                if (e.isDirectory) { target.mkdirs(); continue }
                target.parentFile?.mkdirs()
                target.outputStream().use { z.copyTo(it) }
            }
        }
        require(File(staging, "nebo.db").exists()) { "U kopiji nema baze." }
        ctx.app.db.close()
        val dbFile = ctx.getDatabasePath("nebo.db")
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.path + it).delete() }
        File(staging, "nebo.db").copyTo(dbFile, overwrite = true)
        File(staging, "settings.xml").takeIf { it.exists() }?.copyTo(File(ctx.dataDir, "shared_prefs/nebo.xml"), overwrite = true)
        val audioDir = File(ctx.filesDir, "audio").also { it.mkdirs() }
        File(staging, "audio").listFiles()?.forEach { it.copyTo(File(audioDir, it.name), overwrite = true) }
        staging.deleteRecursively()
        restart(ctx)
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
            r.openOutputStream(doc)!!.use { write(ctx, it) }
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
