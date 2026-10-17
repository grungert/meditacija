package com.grungert.nebo.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * multilingual-e5-small on ONNX Runtime: a sentence in, a 384-number "meaning" out.
 * Comparing two notes, both sides use the "query: " prefix, which e5 recommends for symmetric similarity;
 * searching, the notes are embedded as "passage: ", which ranked the right notes higher on our test set.
 */
class Embedder(modelFile: File, tokenizerFile: File) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelFile.absolutePath, OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(4)
    })
    private val tokenizer = SentencePieceUnigram.load(tokenizerFile)

    fun embed(text: String, prefix: String = "query: "): FloatArray {
        val ids = tokenizer.encode(prefix + text.trim(), maxLen = 256)
        val shape = longArrayOf(1, ids.size.toLong())
        val mask = LongArray(ids.size) { 1 }
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { tIds ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape).use { tMask ->
                OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size)), shape).use { tType ->
                    session.run(mapOf("input_ids" to tIds, "attention_mask" to tMask, "token_type_ids" to tType)).use { out ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = (out[0].value as Array<Array<FloatArray>>)[0]
                        val dim = hidden[0].size
                        val v = FloatArray(dim)
                        for (row in hidden) for (i in 0 until dim) v[i] += row[i]
                        var norm = 0f
                        for (i in 0 until dim) { v[i] /= hidden.size; norm += v[i] * v[i] }
                        norm = sqrt(norm).coerceAtLeast(1e-6f)
                        for (i in 0 until dim) v[i] /= norm
                        return v
                    }
                }
            }
        }
    }

    override fun close() { session.close() }

    companion object {
        const val MODEL_ID = "e5-small-int8"
        fun cosine(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }
    }
}
