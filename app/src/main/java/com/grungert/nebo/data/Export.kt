package com.grungert.nebo.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.grungert.nebo.koan.KoanLibrary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val KIND_LABEL = mapOf("before" to "prije sesije", "after" to "poslije sesije", "esm" to "zapažanje na poziv", "note" to "zapažanje", "week" to "sedmični pregled")
val TYPE_LABEL = mapOf("breath" to "Disanje", "walk" to "Šetnja", "thoughts" to "Oblaci misli", "silence" to "Tišina")

/** Plain text, oldest first, easy to read or to send for a look at the patterns. */
object Export {
    fun text(db: Db, library: KoanLibrary): String {
        val day = SimpleDateFormat("yyyy-MM-dd, EEEE", Locale.forLanguageTag("sr-Latn-ME"))
        val time = SimpleDateFormat("HH:mm", Locale.ROOT)
        val events = db.sessions().map { it.startedAt to it } + db.entries().map { it.createdAt to it }
        val sb = StringBuilder()
        sb.appendLine("Nebo — zapisi")
        sb.appendLine("Izvezeno: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date()))
        var lastDay = ""
        for ((t, ev) in events.sortedBy { it.first }) {
            val d = day.format(Date(t))
            if (d != lastDay) { sb.appendLine().appendLine("=== $d ==="); lastDay = d }
            when (ev) {
                is Session -> {
                    val mins = ev.actualSec / 60
                    sb.appendLine("${time.format(Date(t))} · sesija: ${TYPE_LABEL[ev.type] ?: ev.type}, $mins min" +
                        (if (ev.type == "breath" || ev.type == "walk") ", ritam ${ev.pattern}" else "") +
                        (if (ev.walk) ", šetnja" else "") + (if (ev.thoughts > 0) ", oblaka misli: ${ev.thoughts}" else ""))
                    library.byId(ev.koanId)?.let { sb.appendLine("  koan: ${it.title ?: it.text.take(60)}") }
                }
                is Entry -> {
                    sb.appendLine("${time.format(Date(t))} · ${KIND_LABEL[ev.kind] ?: ev.kind}")
                    if (ev.moods.isNotEmpty()) sb.appendLine("  raspoloženje: ${ev.moods.joinToString(", ")}")
                    ev.text?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  napisano: $it") }
                    ev.transcript?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  izgovoreno: $it") }
                    if (ev.audioPath != null && ev.transcript == null) sb.appendLine("  (snimak čeka transkripciju)")
                }
            }
        }
        return sb.toString()
    }

    fun share(context: Context, db: Db, library: KoanLibrary) {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val f = File(dir, "nebo-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".txt")
        f.writeText(text(db, library))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Izvezi zapise").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
