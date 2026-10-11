package com.grungert.nebo.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Session(
    val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val type: String,
    val plannedSec: Int,
    val actualSec: Int = 0,
    val pattern: String,
    val walk: Boolean,
    val thoughts: Int = 0,
    val koanId: String? = null,
)

/** One spoken or written moment: before or after a session, an answer to a random prompt, or a free note. */
data class Entry(
    val id: Long = 0,
    val createdAt: Long,
    val kind: String,               // before | after | esm | note
    val sessionId: Long? = null,
    val promptId: Long? = null,
    val audioPath: String? = null,
    val text: String? = null,
    val transcript: String? = null,
    val transcriptModel: String? = null,
    val moods: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val koanId: String? = null,
    val starred: Boolean = false,
) {
    /** What the person said, preferring their own typed words, then the transcript. */
    val words: String get() = listOfNotNull(text?.takeIf { it.isNotBlank() }, transcript?.takeIf { it.isNotBlank() }).joinToString("\n")
}

data class Prompt(val id: Long = 0, val scheduledAt: Long, val firedAt: Long? = null, val answeredEntryId: Long? = null, val dismissed: Boolean = false)

/** Everything stays on the phone in one SQLite file. */
class Db(context: Context) : SQLiteOpenHelper(context, "nebo.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions(id INTEGER PRIMARY KEY AUTOINCREMENT, started_at INTEGER NOT NULL, ended_at INTEGER,
            type TEXT NOT NULL, planned_sec INTEGER NOT NULL, actual_sec INTEGER NOT NULL DEFAULT 0, pattern TEXT NOT NULL,
            walk INTEGER NOT NULL DEFAULT 0, thoughts INTEGER NOT NULL DEFAULT 0, koan_id TEXT)""")
        db.execSQL("""CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, kind TEXT NOT NULL,
            session_id INTEGER, prompt_id INTEGER, audio_path TEXT, text TEXT, transcript TEXT, transcript_model TEXT,
            moods TEXT, themes TEXT, koan_id TEXT, starred INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE prompts(id INTEGER PRIMARY KEY AUTOINCREMENT, scheduled_at INTEGER NOT NULL, fired_at INTEGER,
            answered_entry_id INTEGER, dismissed INTEGER NOT NULL DEFAULT 0)""")
        db.execSQL("""CREATE TABLE embeddings(item_id TEXT PRIMARY KEY, model TEXT NOT NULL, text_hash INTEGER NOT NULL, vec BLOB NOT NULL)""")
        db.execSQL("CREATE INDEX entries_created ON entries(created_at)")
        db.execSQL("CREATE INDEX sessions_started ON sessions(started_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE entries ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
    }

    // ---------- sessions ----------
    fun insertSession(s: Session): Long = writableDatabase.insert("sessions", null, ContentValues().apply {
        put("started_at", s.startedAt); put("type", s.type); put("planned_sec", s.plannedSec)
        put("pattern", s.pattern); put("walk", if (s.walk) 1 else 0)
    })

    fun finishSession(id: Long, endedAt: Long, actualSec: Int, thoughts: Int) {
        writableDatabase.update("sessions", ContentValues().apply {
            put("ended_at", endedAt); put("actual_sec", actualSec); put("thoughts", thoughts)
        }, "id=?", arrayOf(id.toString()))
    }

    fun setSessionKoan(id: Long, koanId: String) {
        writableDatabase.update("sessions", ContentValues().apply { put("koan_id", koanId) }, "id=?", arrayOf(id.toString()))
    }

    fun sessions(since: Long = 0): List<Session> =
        readableDatabase.rawQuery("SELECT * FROM sessions WHERE started_at>=? ORDER BY started_at DESC", arrayOf(since.toString())).use { c ->
            generateSequence { if (c.moveToNext()) c.toSession() else null }.toList()
        }

    private fun Cursor.toSession() = Session(
        id = long("id"), startedAt = long("started_at"), endedAt = longOrNull("ended_at"), type = str("type")!!,
        plannedSec = int("planned_sec"), actualSec = int("actual_sec"), pattern = str("pattern")!!,
        walk = int("walk") == 1, thoughts = int("thoughts"), koanId = str("koan_id"),
    )

    // ---------- entries ----------
    fun insertEntry(e: Entry): Long = writableDatabase.insert("entries", null, e.values())

    fun updateEntry(e: Entry) {
        writableDatabase.update("entries", e.values(), "id=?", arrayOf(e.id.toString()))
    }

    fun entry(id: Long): Entry? =
        readableDatabase.rawQuery("SELECT * FROM entries WHERE id=?", arrayOf(id.toString())).use { c -> if (c.moveToNext()) c.toEntry() else null }

    fun entries(since: Long = 0): List<Entry> =
        readableDatabase.rawQuery("SELECT * FROM entries WHERE created_at>=? ORDER BY created_at DESC", arrayOf(since.toString())).use { c ->
            generateSequence { if (c.moveToNext()) c.toEntry() else null }.toList()
        }

    fun entriesForKoan(koanId: String): List<Entry> =
        readableDatabase.rawQuery("SELECT * FROM entries WHERE koan_id=? ORDER BY created_at DESC", arrayOf(koanId)).use { c ->
            generateSequence { if (c.moveToNext()) c.toEntry() else null }.toList()
        }

    fun pendingTranscriptions(): List<Entry> =
        readableDatabase.rawQuery("SELECT * FROM entries WHERE audio_path IS NOT NULL AND transcript IS NULL ORDER BY created_at", null).use { c ->
            generateSequence { if (c.moveToNext()) c.toEntry() else null }.toList()
        }

    fun recentKoanIds(limit: Int): List<String> =
        readableDatabase.rawQuery(
            "SELECT koan_id FROM (SELECT koan_id, started_at AS t FROM sessions WHERE koan_id IS NOT NULL " +
                "UNION ALL SELECT koan_id, created_at AS t FROM entries WHERE koan_id IS NOT NULL) ORDER BY t DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

    private fun Entry.values() = ContentValues().apply {
        put("created_at", createdAt); put("kind", kind); put("session_id", sessionId); put("prompt_id", promptId)
        put("audio_path", audioPath); put("text", text); put("transcript", transcript); put("transcript_model", transcriptModel)
        put("moods", moods.joinToString(",")); put("themes", themes.joinToString(",")); put("koan_id", koanId)
        put("starred", if (starred) 1 else 0)
    }

    private fun Cursor.toEntry() = Entry(
        id = long("id"), createdAt = long("created_at"), kind = str("kind")!!, sessionId = longOrNull("session_id"),
        promptId = longOrNull("prompt_id"), audioPath = str("audio_path"), text = str("text"), transcript = str("transcript"),
        transcriptModel = str("transcript_model"), moods = csv(str("moods")), themes = csv(str("themes")), koanId = str("koan_id"),
        starred = int("starred") == 1,
    )

    // ---------- prompts ----------
    fun insertPrompt(scheduledAt: Long): Long =
        writableDatabase.insert("prompts", null, ContentValues().apply { put("scheduled_at", scheduledAt) })

    fun markPromptFired(id: Long, at: Long) {
        writableDatabase.update("prompts", ContentValues().apply { put("fired_at", at) }, "id=?", arrayOf(id.toString()))
    }

    fun markPromptAnswered(id: Long, entryId: Long) {
        writableDatabase.update("prompts", ContentValues().apply { put("answered_entry_id", entryId) }, "id=?", arrayOf(id.toString()))
    }

    fun prompts(since: Long): List<Prompt> =
        readableDatabase.rawQuery("SELECT * FROM prompts WHERE scheduled_at>=? ORDER BY scheduled_at", arrayOf(since.toString())).use { c ->
            generateSequence {
                if (c.moveToNext()) Prompt(c.long("id"), c.long("scheduled_at"), c.longOrNull("fired_at"),
                    c.longOrNull("answered_entry_id"), c.int("dismissed") == 1) else null
            }.toList()
        }

    fun deleteFuturePrompts(after: Long) {
        writableDatabase.delete("prompts", "scheduled_at>? AND fired_at IS NULL", arrayOf(after.toString()))
    }

    // ---------- embeddings cache ----------
    fun embedding(itemId: String, model: String, textHash: Int): FloatArray? =
        readableDatabase.rawQuery("SELECT vec FROM embeddings WHERE item_id=? AND model=? AND text_hash=?",
            arrayOf(itemId, model, textHash.toString())).use { c ->
            if (!c.moveToNext()) null else {
                val bb = ByteBuffer.wrap(c.getBlob(0)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                FloatArray(bb.remaining()).also { bb.get(it) }
            }
        }

    fun putEmbedding(itemId: String, model: String, textHash: Int, vec: FloatArray) {
        val bb = ByteBuffer.allocate(vec.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vec.forEach { bb.putFloat(it) }
        writableDatabase.insertWithOnConflict("embeddings", null, ContentValues().apply {
            put("item_id", itemId); put("model", model); put("text_hash", textHash); put("vec", bb.array())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Cursor.long(n: String) = getLong(getColumnIndexOrThrow(n))
    private fun Cursor.int(n: String) = getInt(getColumnIndexOrThrow(n))
    private fun Cursor.str(n: String): String? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.longOrNull(n: String): Long? = getColumnIndexOrThrow(n).let { if (isNull(it)) null else getLong(it) }
    private fun csv(s: String?) = s?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
