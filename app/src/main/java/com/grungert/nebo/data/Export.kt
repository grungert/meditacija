package com.grungert.nebo.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.grungert.nebo.koan.InsightThread
import com.grungert.nebo.koan.KoanLibrary
import com.grungert.nebo.koan.THEME_NAMES
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val KIND_LABEL = mapOf("before" to "prije sesije", "after" to "poslije sesije", "esm" to "zapažanje na poziv", "note" to "zapažanje", "week" to "sedmični pregled", "rain" to "RAIN")
val TYPE_LABEL = mapOf(
    "breath" to "Disanje", "walk" to "Šetnja", "thoughts" to "Oblaci misli", "silence" to "Tišina", "points" to "4 tačke",
    "hourglass" to "Pješčani sat", "bodyscan" to "Skeniranje tijela", "urge" to "Talas", "rain" to "RAIN",
)

/** Plain text, oldest first, easy to read or to send for a look at the patterns. */
object Export {
    fun text(db: Db, library: KoanLibrary, threads: List<InsightThread> = emptyList()): String {
        val day = SimpleDateFormat("yyyy-MM-dd, EEEE", Locale.forLanguageTag("sr-Latn-ME"))
        val time = SimpleDateFormat("HH:mm", Locale.ROOT)
        val events = db.sessions().map { it.startedAt to it } + db.entries().map { it.createdAt to it }
        val sb = StringBuilder()
        sb.appendLine("Nebo — zapisi")
        sb.appendLine("Izvezeno: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()))
        val entries = db.entries()
        val starred = entries.filter { it.starred && it.words.isNotBlank() }.sortedBy { it.createdAt }
        sb.appendLine("Sesija: ${db.sessions().size} · bilježaka: ${entries.size} · uvida: ${starred.size} · niti: ${threads.size}")
        sb.appendLine("Oznake: [UVID] je bilješka označena zvjezdicom; teme su ono što je aplikacija prepoznala u riječima.")

        if (starred.isNotEmpty()) {
            sb.appendLine().appendLine("=== UVIDI ===")
            for (e in starred) sb.appendLine("${day.format(Date(e.createdAt))} ${time.format(Date(e.createdAt))}: ${e.words.replace("\n", " ")}")
        }
        if (threads.isNotEmpty()) {
            sb.appendLine().appendLine("=== NITI (ono što se vraća kroz bilješke) ===")
            for ((i, th) in threads.withIndex()) {
                sb.appendLine().appendLine("${i + 1}. ${th.title} · ${th.entries.size} zapisa, ${day.format(Date(th.entries.first().createdAt))} → ${day.format(Date(th.entries.last().createdAt))}")
                for (e in th.entries) sb.appendLine("   - ${day.format(Date(e.createdAt))}" +
                    (if (e.moods.isNotEmpty()) " [${e.moods.joinToString(", ")}]" else "") + ": ${e.words.replace("\n", " ")}")
            }
        }
        sb.appendLine().appendLine("=== DNEVNIK, OD NAJSTARIJEG ===")
        var lastDay = ""
        for ((t, ev) in events.sortedBy { it.first }) {
            val d = day.format(Date(t))
            if (d != lastDay) { sb.appendLine().appendLine("=== $d ==="); lastDay = d }
            when (ev) {
                is Session -> {
                    val mins = ev.actualSec / 60
                    sb.appendLine("${time.format(Date(t))} · sesija: ${TYPE_LABEL[ev.type] ?: ev.type}, $mins min" +
                        (if (ev.type == "breath" || ev.type == "walk") ", ritam ${ev.pattern}" else "") +
                        (if (ev.walk) ", šetnja" else "") + (if (ev.thoughts > 0) ", oblaka misli: ${ev.thoughts}" else "") +
                        (if (ev.marks.isNotEmpty()) ", napetost: " + ev.marks.map { it.region }.distinct().joinToString(", ") else "") +
                        (if (ev.wander.isNotEmpty()) ", um je odlazio u: " + ev.wander.joinToString(", ") else "") +
                        (if (ev.urge != null) ", poriv: " + ev.urge.removePrefix("da ") + (ev.urgeBefore?.let { " $it" } ?: "") + (ev.urgeAfter?.let { " → $it" } ?: "") else ""))
                    library.byId(ev.koanId)?.let { sb.appendLine("  koan: ${it.title ?: it.text.take(60)}") }
                }
                is Entry -> {
                    sb.appendLine("${time.format(Date(t))} · ${KIND_LABEL[ev.kind] ?: ev.kind}" + if (ev.starred) " [UVID]" else "")
                    if (ev.moods.isNotEmpty()) sb.appendLine("  raspoloženje: ${ev.moods.joinToString(", ")}")
                    ev.text?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  napisano: $it") }
                    ev.transcript?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  izgovoreno: $it") }
                    if (ev.audioPath != null && ev.transcript == null) sb.appendLine("  (snimak čeka transkripciju)")
                    if (ev.points.thoughts.isNotBlank()) sb.appendLine("  misli: ${ev.points.thoughts}")
                    if (ev.points.body.isNotEmpty()) sb.appendLine("  tijelo: ${ev.points.body.joinToString(", ")}")
                    if (ev.points.urges.isNotEmpty()) sb.appendLine("  nagoni: ${ev.points.urges.joinToString(", ")}")
                    if (ev.themes.isNotEmpty()) sb.appendLine("  teme: ${ev.themes.joinToString(", ") { THEME_NAMES[it] ?: it }}")
                    library.byId(ev.koanId)?.let { sb.appendLine("  koan: ${it.title ?: it.text.take(60)}") }
                }
            }
        }
        return sb.toString()
    }

    /** Writes the export (threads included, which takes a moment) and opens the share sheet. */
    suspend fun share(context: Context, db: Db, library: KoanLibrary, threads: List<InsightThread>) {
        // the journal in plain text: only the latest export is kept on the phone
        val dir = File(context.cacheDir, "exports").also { d -> d.deleteRecursively(); d.mkdirs() }
        val f = File(dir, "nebo-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".txt")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { f.writeText(text(db, library, threads)) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Izvezi zapise").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
