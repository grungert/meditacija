package com.grungert.nebo.ml

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * Minimal SentencePiece unigram tokenizer for the XLM-RoBERTa family (multilingual-e5).
 * Reads the .model protobuf directly and reproduces the Hugging Face ids, including the
 * fairseq offset (<s>=0, <pad>=1, </s>=2, <unk>=3, every other piece shifted by one).
 */
class SentencePieceUnigram(bytes: ByteArray) {
    private val pieceToId = HashMap<String, Int>()
    private val scores: FloatArray
    private val types: IntArray
    private var maxPieceLen = 1
    private val unkScore: Float

    init {
        val pieces = ArrayList<Triple<String, Float, Int>>()
        val r = ProtoReader(bytes)
        while (r.hasMore()) {
            val (field, wire) = r.tag()
            if (field == 1 && wire == 2) {
                val sub = ProtoReader(r.bytes())
                var piece = ""; var score = 0f; var type = 1
                while (sub.hasMore()) {
                    val (f, w) = sub.tag()
                    when {
                        f == 1 && w == 2 -> piece = String(sub.bytes(), Charsets.UTF_8)
                        f == 2 && w == 5 -> score = sub.fixed32()
                        f == 3 && w == 0 -> type = sub.varint().toInt()
                        else -> sub.skip(w)
                    }
                }
                pieces += Triple(piece, score, type)
            } else r.skip(wire)
        }
        scores = FloatArray(pieces.size) { pieces[it].second }
        types = IntArray(pieces.size) { pieces[it].third }
        var min = Float.MAX_VALUE
        pieces.forEachIndexed { i, (p, s, t) ->
            if (t == NORMAL || t == USER_DEFINED) {
                pieceToId[p] = i
                maxPieceLen = maxOf(maxPieceLen, p.codePointCount(0, p.length))
                if (s < min) min = s
            }
        }
        unkScore = min - 10f
    }

    /** Hugging Face ids with <s> and </s>, truncated to maxLen. */
    fun encode(text: String, maxLen: Int = 256): LongArray {
        val ids = ArrayList<Long>()
        ids += BOS
        for (word in pretokenize(text)) {
            for (spm in viterbi(word)) {
                ids += if (spm < 0 || types[spm] == UNKNOWN) UNK else (spm + 1).toLong()
                if (ids.size >= maxLen - 1) break
            }
            if (ids.size >= maxLen - 1) break
        }
        ids += EOS
        return ids.toLongArray()
    }

    /** NFKC, whitespace cleanup, then split into words that each start with the ▁ marker. */
    private fun pretokenize(text: String): List<String> {
        val sb = StringBuilder()
        for (ch in Normalizer.normalize(text, Normalizer.Form.NFKC)) {
            when {
                ch.isWhitespace() || ch == ' ' -> sb.append(' ')
                Character.getType(ch) == Character.CONTROL.toInt() -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim().split(Regex(" +")).filter { it.isNotEmpty() }.map { "▁$it" }
    }

    /** Best segmentation of one word under the unigram scores; -1 marks an unknown character. */
    private fun viterbi(word: String): List<Int> {
        val cps = word.codePoints().toArray()
        val n = cps.size
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1)
        val piece = IntArray(n + 1) { -1 }
        best[0] = 0f
        for (end in 1..n) {
            for (start in maxOf(0, end - maxPieceLen) until end) {
                if (best[start] == Float.NEGATIVE_INFINITY) continue
                val id = pieceToId[String(cps, start, end - start)] ?: continue
                val s = best[start] + scores[id]
                if (s > best[end]) { best[end] = s; from[end] = start; piece[end] = id }
            }
            if (best[end] == Float.NEGATIVE_INFINITY && best[end - 1] != Float.NEGATIVE_INFINITY) {
                best[end] = best[end - 1] + unkScore; from[end] = end - 1; piece[end] = -1
            }
        }
        val out = ArrayList<Int>()
        var i = n
        while (i > 0) {
            val id = piece[i]
            // consecutive unknown characters collapse into one <unk>, as SentencePiece does
            if (!(id == -1 && out.isNotEmpty() && out.last() == -1)) out += id
            i = from[i]
        }
        out.reverse()
        return out
    }

    private class ProtoReader(private val b: ByteArray) {
        private var pos = 0
        fun hasMore() = pos < b.size
        fun varint(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val x = b[pos++].toInt() and 0xff
                result = result or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return result
                shift += 7
            }
        }
        fun tag(): Pair<Int, Int> { val t = varint().toInt(); return (t ushr 3) to (t and 7) }
        fun bytes(): ByteArray { val len = varint().toInt(); val out = b.copyOfRange(pos, pos + len); pos += len; return out }
        fun fixed32(): Float { val v = ByteBuffer.wrap(b, pos, 4).order(ByteOrder.LITTLE_ENDIAN).float; pos += 4; return v }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> { val len = varint().toInt(); pos += len } // read the length first: pos += varint() would add to the stale pos
                5 -> pos += 4
                else -> error("unsupported wire type $wire")
            }
        }
    }

    companion object {
        private const val NORMAL = 1
        private const val UNKNOWN = 2
        private const val USER_DEFINED = 4
        const val BOS = 0L
        const val EOS = 2L
        const val UNK = 3L
        fun load(file: File) = SentencePieceUnigram(file.readBytes())
    }
}
