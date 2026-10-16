package com.grungert.nebo.koan

/**
 * Reads the four points out of freely spoken words: what is thought, what is felt, what the body does and what
 * one is pulled to do. The words are cut into short pieces (sentences, and clauses joined by "ali", "a", "pa", commas);
 * each piece is placed by its words first, and by its meaning when the words say nothing clear.
 *
 * Body sensations and urges are named with the same short labels the person can pick by hand.
 */
object FourPointsReader {
    const val THOUGHTS = "misli"
    const val EMOTIONS = "emocije"
    const val BODY = "tijelo"
    const val URGES = "nagoni"

    /** Labels the person can pick, each with the word stems that bring it. A trailing * matches any ending. */
    val BODY_LABELS = listOf(
        "stisnut stomak" to listOf("stomak*", "želud*", "mučnin*", "utrob*"),
        "napeta ramena" to listOf("ramen*", "vrat*", "leđ*"),
        "stezanje u grudima" to listOf("grud*", "prsa", "prsima", "stez*", "steže"),
        "lupanje srca" to listOf("srce", "srca", "lupa", "lupanje", "puls*"),
        "knedla u grlu" to listOf("knedl*", "grlo", "grlu"),
        "plitak dah" to listOf("dah", "daha", "diš*", "disanje", "disati", "guš*", "vazduh*"),
        "stegnuta vilica" to listOf("vilic*", "zub*", "škrgut*"),
        "pritisak u glavi" to listOf("glavobolj*", "sljepoočnic*", "pritisak u glavi", "boli me glava"),
        "toplina" to listOf("topl*", "toplin*", "vruć*", "vrućin*"),
        "drhtanje" to listOf("drht*", "tres*"),
        "težina u tijelu" to listOf("težin*", "otežal*", "olovn*"),
        "opušteno tijelo" to listOf("opušten*", "mekan*", "olakšan*", "lagano tijelo"),
    )
    val URGE_LABELS = listOf(
        "da uzmem telefon" to listOf("telefon*", "mobil*", "skrol*", "instagram*", "društvene mreže", "poruke"),
        "da pobjegnem" to listOf("pobjeg*", "bjež*", "da odem", "sakrij*", "izbjeg*"),
        "da jedem" to listOf("da jedem", "da pojedem", "slatk*", "slatkiš*", "grickal*", "čokolad*"),
        "da zapalim" to listOf("cigaret*", "da zapalim", "pušim", "pušenj*"),
        "da se raspravljam" to listOf("viknem", "vičem", "svađ*", "raspravl*", "odbrusim"),
        "da odustanem" to listOf("odustan*", "odustaj*"),
        "da sve kontrolišem" to listOf("kontrol*", "da sredim sve", "isplaniram"),
        "da legnem" to listOf("da legnem", "da odspavam", "u krevet"),
        "da popijem" to listOf("alkohol*", "pivo", "popijem", "vino", "rakij*"),
        "da kupim nešto" to listOf("da kupim", "kupovin*", "naručim"),
    )
    /** Words that say a piece is about a pull to act, even when the act itself is not on the list. */
    private val URGE_CUES = listOf("vuče me", "dođe mi da", "hoću da", "htio bih", "htjela bih", "želim da", "imam potrebu",
        "jedva čekam da", "ne mogu da odolim", "najradije bih", "poriv*", "nagon*")
    private val THOUGHT_CUES = listOf("mislim", "misl*", "razmišlj*", "pitam se", "šta ako", "što ako", "trebalo bi", "trebao bih",
        "trebala bih", "moram", "neće", "nikad", "uvijek", "sjećam se", "sjetio", "sjetila", "planiram", "brinem da", "bojim se da",
        "ne znam da li", "kažem sebi", "u glavi", "po glavi", "zašto", "da li ću")
    private val MOOD_STEMS = mapOf(
        "mirno" to listOf("mir", "mirno", "miran", "mirna", "smiren*", "spokoj*"),
        "nemirno" to listOf("nemir*", "nervoz*", "uznemir*", "anksio*", "napet*"),
        "umorno" to listOf("umor*", "iscrplj*", "pospan*"),
        "tužno" to listOf("tuž*", "tug*", "plač*", "plak*", "suz*"),
        "ljuto" to listOf("ljut*", "bijes*", "iznervir*", "frustr*"),
        "zabrinuto" to listOf("zabrin*", "brin*", "strah*", "plaš*", "bojim se"),
        "zbunjeno" to listOf("zbunj*", "pomiješan*"),
        "zahvalno" to listOf("zahval*"),
        "radosno" to listOf("radost*", "radosn*", "sreć*", "srećan", "vesel*"),
        "radoznalo" to listOf("radozna*", "zanima*"),
    )

    private fun pattern(stem: String): Regex {
        val body = stem.trimEnd('*').split(' ').joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?<![\\p{L}])$body" + (if (stem.endsWith("*")) "\\p{L}*" else "") + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }
    private val cache = HashMap<String, Regex>()
    private fun has(text: String, stems: List<String>) = stems.any { s -> cache.getOrPut(s) { pattern(s) }.containsMatchIn(text) }

    /** What was found in the words. */
    data class Reading(val thoughts: String, val moods: List<String>, val body: List<String>, val urges: List<String>) {
        val found get() = listOfNotNull(
            THOUGHTS.takeIf { thoughts.isNotBlank() }, EMOTIONS.takeIf { moods.isNotEmpty() },
            BODY.takeIf { body.isNotEmpty() }, URGES.takeIf { urges.isNotEmpty() },
        )
    }

    fun pieces(text: String): List<String> = text
        .split(Regex("[.!?;\\n]+|,\\s*(?:ali|a|pa|jer|i)\\s+|\\s+(?:ali|pa)\\s+"))
        .map { it.trim().trim(',', ' ') }.filter { it.split(Regex("\\s+")).size >= 2 }

    /**
     * Reads the four points. [byMeaning] places a piece the words did not place (or returns null to leave it),
     * using the meaning model when it is on the phone.
     */
    fun read(text: String, byMeaning: ((String) -> String?)? = null): Reading {
        val body = BODY_LABELS.filter { has(text, it.second) }.map { it.first }.toMutableList()
        val urges = URGE_LABELS.filter { has(text, it.second) }.map { it.first }.toMutableList()
        val moods = MOOD_STEMS.filter { has(text, it.value) }.keys.toList()
        val thoughts = ArrayList<String>()
        for (p in pieces(text)) {
            val isBody = BODY_LABELS.any { has(p, it.second) }
            val isUrge = URGE_LABELS.any { has(p, it.second) } || has(p, URGE_CUES)
            val isMood = MOOD_STEMS.values.any { has(p, it) }
            val isThought = has(p, THOUGHT_CUES)
            when {
                isThought -> thoughts += p
                // a pull to act that is not on the list keeps its own words
                isUrge && URGE_LABELS.none { has(p, it.second) } -> urges += p.take(60)
                isBody || isUrge || isMood -> {}
                else -> when (byMeaning?.invoke(p)) {
                    THOUGHTS -> thoughts += p
                    BODY -> body += p.take(60)
                    URGES -> urges += p.take(60)
                    else -> {}
                }
            }
        }
        return Reading(thoughts.joinToString(". "), moods, body.distinct(), urges.distinct())
    }

    /** What each point sounds like; the meaning model compares unplaced pieces with these. */
    val DESCRIPTIONS = mapOf(
        THOUGHTS to "Misli mi se vrte u glavi, pitam se šta će biti, razmišljam, planiram, sjećam se, govorim sebi da moram.",
        EMOTIONS to "Osjećam emociju: tugu, strah, ljutnju, radost, nemir, stid, zahvalnost.",
        BODY to "U tijelu osjećam napetost, stezanje u grudima, težinu u stomaku, napeta ramena, dah, toplinu, bol.",
        URGES to "Imam poriv i jaku potrebu da nešto uradim odmah: da uzmem telefon, pobjegnem, jedem, vičem, odustanem.",
    )
}
