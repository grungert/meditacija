package com.grungert.nebo.ml

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.grungert.nebo.data.Settings
import java.io.File

/** A model file the app can download once and keep on the phone. */
data class ModelFile(val name: String, val url: String, val bytes: Long)

data class ModelChoice(val id: String, val label: String, val note: String, val files: List<ModelFile>, val engine: String = "whisper") {
    val bytes get() = files.sumOf { it.bytes }
}

object ModelCatalog {
    private const val WHISPER = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    private const val PARAKEET = "https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/resolve/main/"

    /**
     * Speech to text. Parakeet (NVIDIA, 25 European languages, finds the language itself) was as accurate as
     * Whisper large-v3-turbo on Croatian and Serbian test speech and about 35 times faster, so it comes first.
     */
    val whisper = listOf(
        ModelChoice("parakeet", "Parakeet v3 (NVIDIA)", "najtačniji i najbrži; sam prepoznaje jezik",
            listOf(
                ModelFile("parakeet-v3-encoder.int8.onnx", PARAKEET + "encoder.int8.onnx", 652_184_281),
                ModelFile("parakeet-v3-decoder.int8.onnx", PARAKEET + "decoder.int8.onnx", 11_845_275),
                ModelFile("parakeet-v3-joiner.int8.onnx", PARAKEET + "joiner.int8.onnx", 6_355_277),
                ModelFile("parakeet-v3-tokens.txt", PARAKEET + "tokens.txt", 93_939),
            ), engine = "parakeet"),
        ModelChoice("base", "Whisper base", "brz, ali grub za naš jezik",
            listOf(ModelFile("ggml-base-q5_1.bin", WHISPER + "ggml-base-q5_1.bin", 59_707_625))),
        ModelChoice("small", "Whisper small", "dobra ravnoteža brzine i tačnosti",
            listOf(ModelFile("ggml-small-q5_1.bin", WHISPER + "ggml-small-q5_1.bin", 190_085_487))),
        ModelChoice("turbo", "Whisper large-v3-turbo", "tačan, ali spor na telefonu",
            listOf(ModelFile("ggml-large-v3-turbo-q5_0.bin", WHISPER + "ggml-large-v3-turbo-q5_0.bin", 574_041_195))),
    )

    /** multilingual-e5-small, int8: understands what a sentence means, in our language too. */
    val meaning = ModelChoice("e5", "Model za značenje (e5-small)", "bira koan po onome što kažeš",
        listOf(
            ModelFile("e5-small-int8.onnx", "https://huggingface.co/Xenova/multilingual-e5-small/resolve/main/onnx/model_int8.onnx", 118_100_000),
            ModelFile("e5-sentencepiece.model", "https://huggingface.co/intfloat/multilingual-e5-small/resolve/main/sentencepiece.bpe.model", 5_069_051),
        ))
}

/** Downloads models through the system download manager into the app's own folder. */
class ModelStore(private val context: Context, private val settings: Settings) {
    val dir: File get() = context.getExternalFilesDir("models")!!.also { it.mkdirs() }

    fun file(name: String) = File(dir, name)

    fun isReady(choice: ModelChoice) = choice.files.all { f -> file(f.name).let { it.exists() && it.length() > f.bytes * 0.9 } }

    fun whisperChoice() = ModelCatalog.whisper.firstOrNull { it.id == settings.whisperModel } ?: ModelCatalog.whisper[0]

    /** The speech model that will transcribe: the chosen one if it is on the phone, otherwise the best one that is. */
    fun readyWhisper(): ModelChoice? =
        whisperChoice().takeIf { isReady(it) } ?: listOf("parakeet", "turbo", "small", "base")
            .mapNotNull { id -> ModelCatalog.whisper.firstOrNull { it.id == id } }.firstOrNull { isReady(it) }

    fun download(choice: ModelChoice) {
        val dm = context.getSystemService(DownloadManager::class.java)
        for (f in choice.files) {
            if (file(f.name).let { it.exists() && it.length() > f.bytes * 0.9 }) continue
            file(f.name).delete()
            val req = DownloadManager.Request(Uri.parse(f.url))
                .setTitle("Nebo: ${choice.label}")
                .setDestinationInExternalFilesDir(context, "models", f.name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            settings.setDownloadId(f.name, dm.enqueue(req))
        }
    }

    /** Download progress 0..1, or null when nothing is downloading for this choice. */
    fun progress(choice: ModelChoice): Float? {
        if (isReady(choice)) return 1f
        val dm = context.getSystemService(DownloadManager::class.java)
        var done = 0L
        var active = false
        for (f in choice.files) {
            val id = settings.downloadId(f.name)
            if (id < 0) { done += if (file(f.name).exists()) file(f.name).length() else 0; continue }
            dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (c.moveToFirst()) {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) active = true
                    done += c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                }
            }
        }
        return if (active) done.toFloat() / choice.bytes else null
    }

    fun delete(choice: ModelChoice) = choice.files.forEach { file(it.name).delete() }
}
