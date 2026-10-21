package com.grungert.nebo.ml

/**
 * Speech models sometimes write our language in Cyrillic; Nebo keeps Latin script. Letter by letter, as the two
 * alphabets map one to one (Љ to Lj, Џ to Dž, the Montenegrin С́ and З́ to Ś and Ź), with the few Russian and
 * Ukrainian letters a model may slip in written the nearest way.
 */
object Latin {
    private val MAP: Map<Char, String> = buildMap {
        val pairs = listOf(
            "а" to "a", "б" to "b", "в" to "v", "г" to "g", "д" to "d", "ђ" to "đ", "е" to "e", "ж" to "ž", "з" to "z",
            "и" to "i", "ј" to "j", "к" to "k", "л" to "l", "љ" to "lj", "м" to "m", "н" to "n", "њ" to "nj", "о" to "o",
            "п" to "p", "р" to "r", "с" to "s", "т" to "t", "ћ" to "ć", "у" to "u", "ф" to "f", "х" to "h", "ц" to "c",
            "ч" to "č", "џ" to "dž", "ш" to "š",
            // Russian, Ukrainian and Belarusian letters, the nearest way
            "й" to "j", "я" to "ja", "ю" to "ju", "ё" to "jo", "є" to "je", "ї" to "ji", "і" to "i", "ы" to "i", "э" to "e",
            "щ" to "šč", "ъ" to "", "ь" to "", "ў" to "u", "ґ" to "g",
        )
        for ((c, l) in pairs) {
            put(c[0], l)
            put(c[0].uppercaseChar(), l.replaceFirstChar { it.uppercaseChar() })
        }
    }
    /** The acute accent that makes Montenegrin С́ and З́ (a combining mark after the letter). */
    private const val ACUTE = '́'

    fun hasCyrillic(s: String) = s.any { it in 'Ѐ'..'ӿ' }

    fun of(s: String): String {
        if (!hasCyrillic(s)) return s
        val out = StringBuilder(s.length + 8)
        for ((i, c) in s.withIndex()) {
            if (c == ACUTE && i > 0) {
                // С́ / З́ → Ś / Ź (and lowercase), the accent riding on the letter just written
                val last = out.lastOrNull()
                when (last) { 's' -> out.setCharAt(out.lastIndex, 'ś'); 'S' -> out.setCharAt(out.lastIndex, 'Ś')
                    'z' -> out.setCharAt(out.lastIndex, 'ź'); 'Z' -> out.setCharAt(out.lastIndex, 'Ź'); else -> out.append(c) }
                continue
            }
            val l = MAP[c]
            if (l == null) { out.append(c); continue }
            // a capital digraph inside a word in capitals stays all capitals: ЉУБАВ → LJUBAV, not LjUBAV
            val next = s.getOrNull(i + 1)
            if (l.length > 1 && c.isUpperCase() && next != null && next.isUpperCase()) out.append(l.uppercase()) else out.append(l)
        }
        return out.toString()
    }
}
