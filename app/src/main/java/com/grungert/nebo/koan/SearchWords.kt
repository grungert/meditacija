package com.grungert.nebo.koan

/**
 * Searching notes by their words, in all their forms: "posao" finds "poslu" and "posla", "porodica" finds "porodici",
 * "spavanje" finds "spavam" and "spavao". Each search word is cut back to a stem (a fleeting a, -ao becoming -l, and the
 * common endings), and a note matches when every stem begins one of its words.
 */
object SearchWords {
    private val STOP = setOf(
        "na", "u", "i", "a", "se", "je", "da", "mi", "me", "sam", "si", "ne", "za", "od", "do", "o", "s", "sa", "pa", "li",
        "ali", "kad", "kada", "kako", "šta", "što", "to", "ti", "te", "ga", "mu", "su", "smo", "ste", "bi", "bih", "ni", "po",
        "iz", "uz", "kod", "ka", "ko", "koji", "koja", "koje", "sve", "mnogo", "malo", "vrlo", "baš", "jer",
    )
    private val ENDINGS = listOf("ama", "ima", "om", "em", "og", "oj", "ih", "im", "u", "a", "e", "i", "o")

    fun stem(word: String): String {
        val w = word.lowercase()
        // a noun loses its fleeting a ("posao" → "posl-"), a verb's past keeps its stem ("spavao" → "spava-"): both are
        // tried, as "posl|posa"
        if (w.length > 4 && w.endsWith("ao")) return w.dropLast(2) + "l|" + w.dropLast(1)
        for (end in listOf("anje", "enje")) if (w.endsWith(end) && w.length - end.length >= 3) return w.dropLast(3)
        for (end in ENDINGS) if (w.endsWith(end) && w.length - end.length >= 3) return w.dropLast(end.length)
        return w
    }

    /** The stems of the words that carry meaning in a search; little words like "na" or "sam" are left out. */
    fun stems(query: String): List<String> =
        query.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 && it !in STOP }.map { stem(it) }.distinct()

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /**
     * True when every stem begins a word of the text. A stem that lost a fleeting a ("posl" from "posao") must be
     * followed by a vowel or end the word, so it finds "poslu" and "poslovi" but not "posljednje".
     */
    fun matches(text: String, stems: List<String>): Boolean = stems.isNotEmpty() && stems.all { st ->
        cache.getOrPut(st) {
            // ...but never "poslije" (after), which only looks like a form of "posao"
            val alts = st.split('|').joinToString("|") { a ->
                val tail = if (a.endsWith("l") && a.length >= 3 && a[a.length - 2] !in "aeiou") "(?!ije)(?:[aeiou]|(?![\\p{L}]))" else ""
                Regex.escape(a) + tail
            }
            Regex("(?<![\\p{L}])(?:$alts)", RegexOption.IGNORE_CASE)
        }.containsMatchIn(text)
    }
}
