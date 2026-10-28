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

    /**
     * Labels the person can pick, each with the word stems that bring it. A trailing * matches any ending.
     * Only words that mean the body: "vrata" are a door, "hvala od srca" is not a racing heart, "topla kafa" and
     * "toplina šoljice" are no warmth in the body, and "dišem polako" is calm breath, not a short one: breathing counts
     * only when it is short, heavy or missing.
     */
    val BODY_LABELS = listOf(
        "stisnut stomak" to listOf("stomak*", "želud*", "mučnin*", "utrob*"),
        "napeta ramena" to listOf("ramen*", "vrat", "vratu", "vratom", "leđ*"),
        "stezanje u grudima" to listOf("grud*", "prsa", "prsima", "stez*", "steže"),
        "lupanje srca" to listOf("lupa mi srce", "srce mi lupa", "srce lupa", "lupanje srca", "srce mi tuče", "ubrzano srce", "puls*"),
        "knedla u grlu" to listOf("knedl*", "grlo", "grlu"),
        "plitak dah" to listOf("plit*", "kratak dah", "kratko dišem", "ubrzano dišem", "ubrzan dah", "teško dišem", "teško mi je da dišem", "ne mogu da dišem",
            "zadihan*", "gušim se", "guši me", "nedostaje mi vazduh*", "fali mi vazduh*", "nemam vazduh*", "zastaje mi dah", "zadržavam dah"),
        "stegnuta vilica" to listOf("vilic*", "stisnut* zub*", "stežem zube", "škrgut*"),
        "pritisak u glavi" to listOf("glavobolj*", "sljepoočnic*", "pritisak u glavi", "boli me glava", "glava mi je teška", "teška glava"),
        "toplina" to listOf("toplina u*", "toplinu u*", "osjećam toplin*", "toplo mi je", "vrućin*", "vruće mi je", "talas topline", "toplina se širi", "zagrijan*"),
        "drhtanje" to listOf("drht*", "tresem se", "tresu mi se"),
        "težina u tijelu" to listOf("težina u tijelu", "težinu u", "otežal*", "olovn*", "teško tijelo", "teške noge", "teške ruke"),
        "opušteno tijelo" to listOf("opušten*", "lagano tijelo", "olakšan*", "ramena se spuštaju", "ramena se konačno spuštaju"),
    )
    /**
     * The same for urges. Stems marked with a leading ~ say what the pull is for, but only count when the words also say
     * there is a pull ([URGE_CUES]): "pričala sam telefonom s mamom" or "slatko sam spavao" are no urge, "vuče me telefon"
     * and "dođe mi da pojedem nešto slatko" are.
     */
    val URGE_LABELS = listOf(
        "da uzmem telefon" to listOf("~telefon*", "~mobil*", "~poruke", "skrol*", "instagram*", "tiktok*", "društvene mreže", "ka telefonu"),
        "da pobjegnem" to listOf("pobjeg*", "bjež*", "da odem", "sakrij*", "izbjeg*"),
        "da jedem" to listOf("da jedem", "da pojedem", "~slatk*", "~slatkiš*", "~grickal*", "~čokolad*"),
        "da zapalim" to listOf("cigaret*", "da zapalim", "pušim", "pušenj*"),
        "da se raspravljam" to listOf("da viknem", "da odbrusim", "~viknem", "~vičem", "~svađ*", "~raspravl*", "~odbrusim"),
        "da odustanem" to listOf("odustan*", "odustaj*"),
        "da sve kontrolišem" to listOf("da sve kontroliš*", "sve pod kontrolom", "~kontroliš*", "da sredim sve", "isplaniram"),
        "da legnem" to listOf("da legnem", "da odspavam", "~u krevet"),
        "da popijem" to listOf("da popijem", "~alkohol*", "~pivo", "~popijem", "~vino", "~rakij*"),
        "da kupim nešto" to listOf("da kupim", "~kupovin*", "naručim"),
    )
    /** Words that say a piece is about a pull to act, even when the act itself is not on the list. */
    private val URGE_CUES = listOf("vuče me", "dođe mi da", "hoću da", "htio bih", "htjela bih", "želim da", "imam potrebu",
        "jedva čekam da", "ne mogu da odolim", "najradije bih", "poriv*", "nagon*", "ruka sama", "ruka ide", "dođe mi", "žudim",
        "ne mogu bez", "jedva se suzdrž*")
    private val THOUGHT_CUES = listOf("mislim", "misl*", "razmišlj*", "pitam se", "šta ako", "što ako", "trebalo bi", "trebao bih",
        "trebala bih", "moram", "neće", "nikad", "uvijek", "sjećam se", "sjetio", "sjetila", "planiram", "brinem da", "bojim se da",
        "ne znam da li", "kažem sebi", "u glavi", "po glavi", "zašto", "da li ću")
    private val MOOD_STEMS = mapOf(
        "mirno" to listOf("mir", "mirno", "miran", "mirna", "smiren*", "spokoj*"),
        // "napet" as a mood, said of oneself; "napeta ramena" are the body, read there
        "nemirno" to listOf("nemir*", "nervoz*", "uznemir*", "anksio*", "napet sam", "napeta sam", "osjećam se napet*", "sav sam napet", "sva sam napeta"),
        "umorno" to listOf("umor*", "iscrplj*", "pospan*"),
        "tužno" to listOf("tuž*", "tug*", "plač*", "plak*", "suz*"),
        "ljuto" to listOf("ljut*", "bijes*", "iznervir*", "frustr*"),
        "zabrinuto" to listOf("zabrin*", "brin*", "strah*", "plaš*", "bojim se"),
        "zbunjeno" to listOf("zbunj*", "pomiješan*"),
        "zahvalno" to listOf("zahval*"),
        "radosno" to listOf("radost*", "radosn*", "sreć*", "srećan", "vesel*"),
        "radoznalo" to listOf("radozna*", "zanima*"),
    )

    /**
     * The person's own labels, by point (emotions, body, urges), added on the note screen. Read like the others: a
     * label is found when every word of it (of three letters or more) is in the text, in any of its forms ("bol u leđima" in "leđa me bole").
     */
    @Volatile var custom: Map<String, List<String>> = emptyMap()

    /** The stems of a label's longer words: their start, so the endings of our language do not matter. */
    fun stemsOf(label: String) = label.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }.map { w -> w.take(maxOf(3, w.length - 3)) + "*" }

    private fun pattern(stem: String): Regex {
        val body = stem.trimEnd('*').split(' ').joinToString("\\s+") { Regex.escape(it) }
        return Regex("(?<![\\p{L}])$body" + (if (stem.endsWith("*")) "\\p{L}*" else "") + "(?![\\p{L}])", RegexOption.IGNORE_CASE)
    }
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    /** Words that turn what follows around: "nisam ljut", "ne brinem", "nije me strah", "bez straha". */
    private val NEGATIONS = setOf("ne", "nisam", "nije", "nismo", "niste", "nisu", "nisi", "nimalo", "bez", "ni")

    /** True when one of the two words just before [at] negates what stands there. */
    fun negated(text: String, at: Int): Boolean =
        text.substring(0, at).lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.takeLast(2).any { it in NEGATIONS }

    /**
     * True when a stem is in the words. [negatable] stems (feelings and the body) do not count after a negation;
     * a ~stem counts only when the text also speaks of a pull to act.
     */
    private fun has(text: String, stems: List<String>, negatable: Boolean = false, cued: Boolean = false) = stems.any { s0 ->
        if (s0.startsWith("~") && !cued) return@any false
        val s = s0.removePrefix("~")
        val re = cache.getOrPut(s) { pattern(s) }
        if (negatable) re.findAll(text).any { !negated(text, it.range.first) } else re.containsMatchIn(text)
    }

    private fun cueIn(text: String) = has(text, URGE_CUES)

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
        val cued = cueIn(text)
        val body = BODY_LABELS.filter { has(text, it.second, negatable = true) }.map { it.first }.toMutableList()
        val urges = URGE_LABELS.filter { has(text, it.second, cued = cued) }.map { it.first }.toMutableList()
        val moods = MOOD_STEMS.filter { has(text, it.value, negatable = true) }.keys.toMutableList()
        // the person's own labels, found by all their words
        fun own(point: String) = custom[point].orEmpty().filter { l -> stemsOf(l).let { st -> st.isNotEmpty() && st.all { has(text, listOf(it), negatable = true) } } }
        moods += own(EMOTIONS).filter { it !in moods }
        body += own(BODY).filter { it !in body }
        urges += own(URGES).filter { it !in urges }
        val thoughts = ArrayList<String>()
        for (p in pieces(text)) {
            val isBody = BODY_LABELS.any { has(p, it.second, negatable = true) }
            val labelled = URGE_LABELS.any { has(p, it.second, cued = cued) }
            val isUrge = labelled || cueIn(p)
            val isMood = MOOD_STEMS.values.any { has(p, it, negatable = true) }
            val isThought = has(p, THOUGHT_CUES)
            when {
                isThought -> thoughts += p
                // a pull to act that is not on the list keeps its own words
                isUrge && !labelled -> urges += p.take(60)
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
