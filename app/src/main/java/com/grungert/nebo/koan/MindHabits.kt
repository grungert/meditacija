package com.grungert.nebo.koan

/**
 * Habits of the mind in someone's own words: "moram" and "trebalo bi", "uvijek" and "nikad", "šta ako", being one's
 * own judge, guessing what others think, and whether the mind is in the past, here, or ahead in the future.
 * No diagnoses: these are habits every mind has, and noticing them takes some of their pull away.
 *
 * Read by words only. The meaning model was tried for past, present and future, and it takes an ordinary day told in
 * the past tense ("razgovarao sam sa sestrom") for a mind that keeps going back; the words below are the ones that
 * actually go back or run ahead.
 */
object MindHabits {
    class Habit(val id: String, val name: String, val about: String, val stems: List<String>)

    val HABITS = listOf(
        Habit("moranje", "Moram, trebalo bi", "dužnost koja pritiska", listOf(
            "moram", "moraš", "moramo", "mora da", "moraću", "trebalo bi", "trebao bih", "trebala bih", "treba da",
            "obavezno", "ne smijem", "nemam izbora")),
        Habit("uvijek", "Uvijek, nikad", "jedan trenutak postane cijela priča", listOf(
            "uvijek", "nikad", "nikada", "stalno", "svaki put", "baš ništa", "baš sve", "niko me", "nikome ne")),
        Habit("staako", "Šta ako", "um trči ispred tebe", listOf(
            "šta ako", "što ako", "a ako", "bojim se da će", "plašim se da će", "brinem da", "sigurno će", "propašće",
            "najgore", "katastrof*")),
        Habit("samokritika", "Samokritika", "kad si sebi sudija", listOf(
            "glup sam", "glupa sam", "glupo od mene", "nisam dovolj*", "ne valjam", "kriv sam", "kriva sam", "moja krivica",
            "loš sam", "loša sam", "mrzim sebe", "sram me", "sramota", "nesposob*", "promašaj", "budala sam",
            "opet sam zabrlja*", "ljut na sebe", "ljuta na sebe", "razočaran u sebe", "razočarana u sebe")),
        Habit("tudjemisli", "Tuđe misli", "kad znaš šta drugi misle", listOf(
            "misli da sam", "misle da sam", "sigurno misli", "sigurno misle", "šta će reći", "šta će misliti",
            "šta drugi misle", "šta će drugi", "šta će ljudi", "ljut je na mene", "ljuta je na mene", "ljuti su na mene",
            "ne voli me", "ne vole me", "smiju mi se", "osuđuju me")),
    )

    fun habit(id: String) = HABITS.firstOrNull { it.id == id }

    /** Where the mind can wander during a sitting, as asked after a clouds session. */
    val WANDER = listOf("planiranje", "briga", "sjećanje", "prosuđivanje", "maštanje", "tijelo", "zvuci")

    enum class Time(val label: String, val about: String) {
        PAST("u prošlosti", "sjećanja i premotavanje"),
        NOW("ovdje i sada", "ono što je upravo tu"),
        FUTURE("u budućnosti", "planovi i brige"),
    }

    private val PAST = listOf(
        "sjećam se", "sjetio sam se", "sjetila sam se", "prisjeć*", "premotavam", "premotava*", "vrti mi se ono",
        "žao mi je što", "kajem se", "nedostaje mi", "nedostaju mi", "nekad", "nekada", "kad sam bio", "kad sam bila",
        "kad smo bili", "trebalo je", "trebao sam", "trebala sam", "da sam bar", "da sam znao", "da sam znala",
        "zašto sam", "nisam smio", "nisam smjela", "propustio sam", "propustila sam", "u prošlosti", "prošlost*",
    )
    private val FUTURE = listOf(
        "sutra", "prekosutra", "sljedeće", "sledeće", "narednih", "za mjesec", "za par dana", "za nekoliko dana",
        "šta ako", "što ako", "planiram", "isplanir*", "moraću", "trebaću", "biće", "hoće li", "da li ću", "kad budem",
        "čeka me", "rok", "roka", "rokovi", "bojim se da će", "plašim se da će", "brinem da", "brine me kako",
        "budućnost*", "šta će biti", "kako ću",
    )
    // only words that put the mind in this moment; "sad" and "osjećam" stand in almost every note and say nothing of where the mind is
    private val NOW = listOf(
        "upravo", "ovog trena", "u ovom trenutku", "ovdje i sada", "sada i ovdje", "sada sam", "sada osjećam",
        "ovdje sam", "dišem", "primjećujem", "čujem", "slušam", "gledam",
    )

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun pattern(stem: String) = cache.getOrPut(stem) {
        val body = stem.trimEnd('*').split(' ').joinToString("\\s+") { Regex.escape(it) }
        Regex("(?<![\\p{L}])$body" + (if (stem.endsWith("*")) "\\p{L}*" else "") + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }
    private fun count(text: String, stems: List<String>) = stems.sumOf { pattern(it).findAll(text).count() }

    /**
     * The words of a note to read the mind in: what was said, plus the thoughts point where it adds something. The
     * thoughts read from the words are pieces of those same words, and counting them twice would tip the balance.
     */
    fun mindOf(e: com.grungert.nebo.data.Entry): String {
        val extra = e.points.thoughts.split(Regex("[.\\n]+")).map { it.trim() }.filter { it.isNotEmpty() && !e.words.contains(it, ignoreCase = true) }
        return (listOf(e.words) + extra).joinToString("\n")
    }

    /** "Kao i uvijek" is a turn of phrase, not a verdict on life. */
    private fun clean(text: String) = text.replace(Regex("kao i uvijek|kao i obično", RegexOption.IGNORE_CASE), " ")

    /** The habits found in these words, by id, in the order of [HABITS]. */
    fun habits(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val t = clean(text)
        return HABITS.filter { h -> count(t, h.stems) > 0 }.map { it.id }
    }

    /** Where the mind is in these words, or null when they do not lean one way: the most cues, ahead by at least one. */
    fun time(text: String): Time? {
        if (text.isBlank()) return null
        val t = clean(text)
        val counts = listOf(Time.PAST to count(t, PAST), Time.NOW to count(t, NOW), Time.FUTURE to count(t, FUTURE)).sortedByDescending { it.second }
        return counts[0].first.takeIf { counts[0].second > 0 && counts[0].second > counts[1].second }
    }
}
