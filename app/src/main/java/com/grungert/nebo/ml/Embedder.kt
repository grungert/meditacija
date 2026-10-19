package com.grungert.nebo.ml

import java.io.File
import kotlin.math.sqrt

/** The meaning model on the ONNX Runtime that comes with sherpa-onnx (see ort_jni.cpp). */
internal object OrtNative {
    init { System.loadLibrary("nebo_ort") }
    /** A handle to the open model, or 0 when it could not be opened. */
    external fun open(path: String, threads: Int): Long
    /** The model's output for these token ids, flattened (tokens × 384), or null on an error. */
    external fun run(handle: Long, ids: LongArray): FloatArray?
    external fun close(handle: Long)
}

/**
 * multilingual-e5-small on ONNX Runtime: a sentence in, a 384-number "meaning" out.
 * Comparing two notes, both sides use the "query: " prefix, which e5 recommends for symmetric similarity;
 * searching, the notes are embedded as "passage: ", which ranked the right notes higher on our test set.
 */
class Embedder(modelFile: File, tokenizerFile: File) : AutoCloseable {
    private val handle: Long = OrtNative.open(modelFile.absolutePath, 4).also { check(it != 0L) { "the meaning model did not open" } }
    private val tokenizer = SentencePieceUnigram.load(tokenizerFile)

    fun embed(text: String, prefix: String = "query: "): FloatArray {
        val ids = tokenizer.encode(prefix + text.trim(), maxLen = 256)
        // one row of numbers per token; their average, made unit length, is the meaning
        val hidden = OrtNative.run(handle, ids) ?: error("the meaning model could not read this")
        val n = ids.size
        val dim = hidden.size / n
        val v = FloatArray(dim)
        for (t in 0 until n) for (i in 0 until dim) v[i] += hidden[t * dim + i]
        var norm = 0f
        for (i in 0 until dim) { v[i] /= n; norm += v[i] * v[i] }
        norm = sqrt(norm).coerceAtLeast(1e-6f)
        for (i in 0 until dim) v[i] /= norm
        return v
    }

    override fun close() = OrtNative.close(handle)

    companion object {
        const val MODEL_ID = "e5-small-int8"
        fun cosine(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }
    }
}
