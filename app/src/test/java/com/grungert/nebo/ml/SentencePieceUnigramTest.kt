package com.grungert.nebo.ml

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Compares the Kotlin tokenizer with ids produced by the Hugging Face tokenizer
 * (fixtures in tokenizer_cases.json). Needs the model file: pass -Pspm=/path/sentencepiece.bpe.model.
 */
class SentencePieceUnigramTest {
    @Test
    fun matchesHuggingFaceIds() {
        val path = System.getProperty("spm") ?: ""
        assumeTrue("set -Pspm to run", path.isNotEmpty() && File(path).exists())
        val tok = SentencePieceUnigram.load(File(path))
        val text = javaClass.classLoader!!.getResource("tokenizer_cases.json").readText()
        val cases = JSONArray(text)
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val expected = c.getJSONArray("ids").let { a -> LongArray(a.length()) { a.getLong(it) } }
            assertEquals(c.getString("text"), expected.toList(), tok.encode(c.getString("text")).toList())
        }
    }
}
