package com.grungert.nebo.koan

import android.content.Context
import com.grungert.nebo.data.Db
import com.grungert.nebo.ml.Embedder
import com.grungert.nebo.ml.ModelCatalog
import com.grungert.nebo.ml.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.exp
import kotlin.random.Random

/** A koan from the Gateless Gate, or a short message for moments a koan would not fit. */
data class KoanItem(
    val id: String,
    val isKoan: Boolean,
    val title: String?,
    val text: String,
    val comment: String?,
    val verse: String?,
    val question: String?,
    val themes: List<String>,
    val whenHelps: String,
    val source: String?,
    /** Which collection a reading comes from ("Zen priča", "Tao Te Ching", …); null for koans and the app's own messages. */
    val collection: String? = null,
    /** A haiku's season ("proljeće", "ljeto", "jesen", "zima") and weather ("kiša", "snijeg", "magla", "vedro"), when it has them. */
    val season: String? = null,
    val weather: String? = null,
)

class KoanLibrary(context: Context) {
    val items: List<KoanItem> by lazy {
        fun load(name: String, koan: Boolean): List<KoanItem> = runCatching {
            val a = JSONArray(context.assets.open(name).bufferedReader().readText())
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val t = o.optJSONArray("teme")
                KoanItem(
                    id = o.getString("id"), isKoan = koan, title = o.optString("naslov").ifBlank { null },
                    text = if (koan) o.getString("koan") else o.getString("tekst"),
                    comment = o.optString("komentar").ifBlank { null }, verse = o.optString("stih").ifBlank { null },
                    question = o.optString("pitanje").ifBlank { null },
                    themes = if (t == null) emptyList() else List(t.length()) { t.getString(it) },
                    whenHelps = o.optString("kadaPomaze"), source = o.optString("izvor").ifBlank { null },
                    collection = o.optString("zbirka").ifBlank { null },
                    season = o.optString("sezona").ifBlank { null }?.takeIf { it != "null" },
                    weather = o.optString("vrijeme").ifBlank { null }?.takeIf { it != "null" },
                )
            }
        }.getOrDefault(emptyList())
        // readings: Zen stories, Zhuangzi, the Tao Te Ching, the Dhammapada, Rumi, Kabir and haiku, chosen by meaning like the messages
        load("koani.json", true) + load("poruke.json", false) + load("price.json", false)
    }

    fun byId(id: String?) = items.firstOrNull { it.id == id }
}

/** Plain-language names of the themes, for "why this one". */
val THEME_NAMES = mapOf(
    "nemir" to "nemir", "tuga" to "tuga", "ljutnja" to "ljutnja", "umor" to "umor", "strah" to "strah",
    "sumnja" to "sumnja", "zudnja" to "žudnja", "kontrola" to "potreba za kontrolom", "samokritika" to "samokritika",
    "usamljenost" to "usamljenost", "zahvalnost" to "zahvalnost", "radost" to "radost", "radoznalost" to "radoznalost",
    "zbunjenost" to "zbunjenost", "smirenost" to "smirenost", "ego" to "slika o sebi", "prolaznost" to "prolaznost",
    "svakodnevica" to "svakodnevica", "odluka" to "odluka", "odnosi" to "odnosi s drugima", "prisutnost" to "prisutnost",
)

/** What each theme sounds like in someone's own words; the meaning model compares notes with these. */
private val THEME_DESCRIPTIONS = mapOf(
    "nemir" to "Osjećam nemir, napetost i nervozu, ne mogu da se smirim, jurim i brinem.",
    "tuga" to "Tužan sam, plače mi se, nešto mi nedostaje, osjećam gubitak i prazninu.",
    "ljutnja" to "Ljut sam, iznerviran, bijesan na nekoga ili nešto, frustrira me.",
    "umor" to "Umoran sam i iscrpljen, nemam snage, pospan sam, težak dan.",
    "strah" to "Plašim se, osjećam strah i nesigurnost, uplašen sam od onoga što dolazi.",
    "sumnja" to "Sumnjam, ne znam šta je ispravno, dvoumim se, pitam se ima li smisla.",
    "zudnja" to "Žudim za nečim, stalno želim još, vezan sam za stvari, telefon i navike.",
    "kontrola" to "Moram sve da kontrolišem i isplaniram, sve treba da bude savršeno.",
    "samokritika" to "Kritikujem sebe, osjećam se krivim, nisam dovoljno dobar, stidim se.",
    "usamljenost" to "Osjećam se usamljeno, sam sam, niko me ne razumije, odvojen od ljudi.",
    "zahvalnost" to "Zahvalan sam za ono što imam, osjećam zahvalnost.",
    "radost" to "Radostan sam i srećan, lijepo mi je, uživam, smijem se.",
    "radoznalost" to "Radoznao sam, zanima me, istražujem, pitam se kako to funkcioniše.",
    "zbunjenost" to "Zbunjen sam, sve mi je zbrkano u glavi, ne razumijem šta se dešava.",
    "smirenost" to "Miran sam, spokojan i opušten, osjećam tišinu i lakoću.",
    "ego" to "Brine me šta drugi misle o meni, slika o sebi, ponos i ugled, ko sam ja.",
    "prolaznost" to "Sve prolazi, vrijeme ide, promjene, starenje, kraj i smrt.",
    "svakodnevica" to "Obaveze, posao, kuća, rutina i sitnice svakodnevnog života.",
    "odluka" to "Moram da donesem odluku, biram između dva puta, raskršće.",
    "odnosi" to "Odnos s partnerom, porodicom, prijateljima ili kolegama, svađa ili bliskost.",
    "prisutnost" to "Prisutan sam ovdje i sada, osjećam dah i tijelo, sadašnji trenutak.",
)

/** One-tap moods on the check-in screen, and the theme each one points to. */
val MOODS = listOf(
    "mirno" to "smirenost", "nemirno" to "nemir", "umorno" to "umor", "tužno" to "tuga", "ljuto" to "ljutnja",
    "zabrinuto" to "strah", "zbunjeno" to "zbunjenost", "zahvalno" to "zahvalnost", "radosno" to "radost", "radoznalo" to "radoznalost",
)

/** Word stems that point to a theme; used when the meaning model is not on the phone yet, and as a small bonus. */
object Lexicon {
    // A word ending in * matches any word starting with it; anything else must be the whole word.
    // Several words are matched as a phrase. Matching whole words keeps "nego" from counting as ego,
    // "imam" as mama or "poslije" as posao.
    private val words = mapOf(
        "nemir" to listOf("nemir*", "nervoz*", "napet*", "uznemir*", "stres*", "žurb*", "žurim", "jurim", "juri", "anksio*", "brig*", "brin*"),
        "tuga" to listOf("tug*", "tužn*", "tužan", "plak*", "plač*", "suz*", "žalim", "žalost*", "žao mi je", "nedostaj*", "gubit*", "izgubi*", "praznin*"),
        "ljutnja" to listOf("ljut*", "bijes*", "iritir*", "nervira*", "smeta mi", "frustrir*"),
        "umor" to listOf("umor*", "iscrp*", "pospan*", "nemam snage", "težak dan", "izmoren*", "premoren*"),
        "strah" to listOf("strah*", "plaš*", "bojim", "boji se", "bojao", "bojala", "panik*", "uplaš*", "nesigur*"),
        "sumnja" to listOf("sumnj*", "ne znam da li", "ne znam šta", "ne znam kako", "nisam siguran", "nisam sigurna", "dvoum*",
            "nema smisla", "ima li smisla", "smisao života", "koji je smisao"),
        "zudnja" to listOf("žudn*", "žudim", "želim još", "hoću još", "treba mi još", "privrž*", "vuče me", "zavisn*", "skrol*", "još malo", "ne mogu bez"),
        "kontrola" to listOf("kontroliš*", "kontrolis*", "pod kontrolom", "kontrolu nad", "izgubio kontrolu", "izgubila kontrolu",
            "moram sve", "sve mora", "da sve bude", "isplanir*", "savršen*", "perfekc*"),
        "samokritika" to listOf("kriv", "kriva", "krivic*", "sram*", "stid*", "glup*", "nisam dovolj*", "ne valjam", "kritikuj*", "osuđ*", "loš sam", "loša sam"),
        "usamljenost" to listOf("usamlj*", "sam sam", "sama sam", "niko me", "me niko", "niko ne razumije", "nemam nikoga", "nikome ne", "izolov*", "odvojen*"),
        "zahvalnost" to listOf("zahval*", "hvala", "srećom"),
        "radost" to listOf("radost*", "radujem", "raduje", "srećn*", "srećan", "sretn*", "sretan", "vesel*", "lijepo mi je", "bilo je lijepo", "lijepo sam se", "uživ*", "smijeh*", "smijao", "smijala", "nasmij*"),
        "radoznalost" to listOf("radoznal*", "zanima*", "pitam se", "zanimljiv*", "istraž*"),
        "zbunjenost" to listOf("zbunj*", "zbrk*", "haos*", "ne razumijem", "pomiješ*", "pomješ*"),
        "smirenost" to listOf("miran", "mirna", "mirno", "mirnije", "smiren*", "spokoj*", "tišin*", "opušten*", "lagan*", "lakoć*"),
        "ego" to listOf("identitet*", "ko sam", "ponos*", "ego", "ega", "egu", "ugled*", "šta će drugi", "šta drugi"),
        "prolaznost" to listOf("prolazn*", "sve prolazi", "vrijeme prolazi", "smrt*", "starenj*", "promjen*", "kraj svega"),
        "svakodnevica" to listOf("posao", "posla", "poslu", "poslom", "poslov*", "kuć*", "sudov*", "obavez*", "svakodnev*", "rutin*"),
        "odluka" to listOf("odluk*", "odluč*", "izbor*", "biram", "birati", "raskršć*"),
        "odnosi" to listOf("partner*", "prijatelj*", "porodic*", "majk*", "mama", "mami", "mamu", "mamom", "otac", "oca", "ocu", "tata", "tati", "tatu",
            "brat", "brata", "bratu", "bratom", "sestr*", "djec*", "dijete", "djetet*", "odnos*", "svađ*", "ljubav*", "kolega", "kolege", "kolegom", "kolegama", "muž*", "žen*", "djevojk*", "dečk*"),
        "prisutnost" to listOf("ovdje i sada", "sada i ovdje", "upravo sada", "ovdje sam", "u ovom trenutku", "ovaj trenutak",
            "dah", "daha", "dahom", "disanj*", "dišem", "tijel*", "prisut*", "sadašnj*"),
    )

    private val patterns: Map<String, Regex> = words.mapValues { (_, list) ->
        val alts = list.joinToString("|") { w ->
            val parts = w.split(" ").map { part -> if (part.endsWith("*")) Regex.escape(part.dropLast(1)) + "\\p{L}*" else Regex.escape(part) }
            parts.joinToString("\\s+")
        }
        Regex("(?<![\\p{L}])(?:$alts)(?![\\p{L}])")
    }

    /** Themes that are feelings: after "nisam", "ne" or "bez" they are not there ("nisam ljut", "bez straha"). */
    private val FEELINGS = setOf("nemir", "tuga", "ljutnja", "umor", "strah", "usamljenost", "samokritika", "radost", "smirenost", "zbunjenost", "zahvalnost")

    fun themes(text: String): List<String> {
        val t = text.lowercase()
        return patterns.filter { (theme, re) ->
            if (theme in FEELINGS) re.findAll(t).any { !FourPointsReader.negated(t, it.range.first) } else re.containsMatchIn(t)
        }.keys.toList()
    }

    private val distress = listOf(
        "ubiti se", "ubijem se", "ubio bih se", "ubila bih se", "samoubist", "ne želim da živim", "ne želim više da živim",
        "nema smisla živjeti", "nemam zašto da živim", "povrijediti sebe", "povrijedim sebe", "sebi nauditi",
        "da me nema", "presjeći vene", "skočiti s", "skočim s",
    )

    /** True when the words sound like real danger; the app then offers people to talk to instead of a koan. */
    fun soundsLikeDistress(text: String): Boolean { val t = text.lowercase(); return distress.any { t.contains(it) } }
}

data class Pick(val item: KoanItem?, val reason: String, val distress: Boolean = false, val themes: List<String> = emptyList())

/** Picks the koan or message whose "when it helps" description is closest in meaning to what was said. */
/** Notes that keep returning to the same thing, oldest first. */
data class InsightThread(val entries: List<com.grungert.nebo.data.Entry>, val theme: String?, val title: String)

class Recommender(private val db: Db, private val models: ModelStore, private val library: KoanLibrary, private val settings: com.grungert.nebo.data.Settings) {
    private val lock = Mutex()
    private var embedder: Embedder? = null
    // the meaning model takes over a hundred megabytes: it is opened when a note needs reading and let go two minutes
    // after its last use (long enough for a look through the journal). Only touched under [lock].
    private val keep = com.grungert.nebo.ml.KeepAlive(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default), lock, 120_000) {
        embedder?.close(); embedder = null
    }

    val usesMeaning get() = models.isReady(ModelCatalog.meaning)

    private fun embedderOrNull(): Embedder? {
        if (!usesMeaning) return null
        if (embedder == null) runCatching {
            embedder = Embedder(models.file(ModelCatalog.meaning.files[0].name), models.file(ModelCatalog.meaning.files[1].name))
        }
        if (embedder != null) { keep.touch(); keep.later() }
        return embedder
    }

    private fun themeVectors(e: Embedder): Map<String, FloatArray> = THEME_DESCRIPTIONS.mapValues { (k, text) ->
        db.embedding("theme:$k", Embedder.MODEL_ID, text.hashCode()) ?: e.embed(text).also { db.putEmbedding("theme:$k", Embedder.MODEL_ID, text.hashCode(), it) }
    }

    /**
     * The theme a sentence clearly leans towards, or none. Calibrated on real sentences: the best theme must
     * stand 0.038 above the median of all themes and 0.012 ahead of the runner-up, which kept every accepted
     * theme correct while the keyword list covers the rest.
     */
    private fun semanticTheme(v: FloatArray, e: Embedder): String? {
        val sims = themeVectors(e).map { (k, tv) -> k to Embedder.cosine(v, tv) }.sortedByDescending { it.second }
        val median = sims[sims.size / 2].second
        return if (sims[0].second - median >= 0.038f && sims[0].second - sims[1].second >= 0.012f) sims[0].first else null
    }

    private fun entryVector(x: com.grungert.nebo.data.Entry, e: Embedder): FloatArray {
        val key = "entry:${x.id}"
        val h = x.words.hashCode()
        return db.embedding(key, Embedder.MODEL_ID, h) ?: e.embed(x.words).also { db.putEmbedding(key, Embedder.MODEL_ID, h, it) }
    }

    /** The same note embedded as a passage, for search; cached apart from the note-to-note vector. */
    private fun passageVector(x: com.grungert.nebo.data.Entry, e: Embedder): FloatArray {
        val key = "passage:${x.id}"
        val h = x.words.hashCode()
        return db.embedding(key, Embedder.MODEL_ID, h) ?: e.embed(x.words, "passage: ").also { db.putEmbedding(key, Embedder.MODEL_ID, h, it) }
    }

    /** Readies every note for search by meaning, once, so the first search does not wait for them. */
    suspend fun warmSearch() = withContext(Dispatchers.Default) {
        for (id in db.entries().map { it.id }) lock.withLock {
            if (!usesMeaning) return@withContext
            // read again: a note deleted or rewritten meanwhile gets no stale vector
            val x = db.entry(id) ?: return@withLock
            // the model is opened only for a note not read yet, not on every start
            if (x.words.isBlank() || db.embedding("passage:${x.id}", Embedder.MODEL_ID, x.words.hashCode()) != null) return@withLock
            passageVector(x, embedderOrNull() ?: return@withContext)
        }
    }

    /**
     * Notes closest in meaning to a search, best first, each with how far it stands above the rest (in standard
     * deviations). e5 reads a short sentence far better than a single word: on test notes the right one came first for
     * six of seven short sentences and for about half of the single words. So only notes that clearly stand out are
     * returned, and the words themselves are matched separately. [exclude] are notes already found by their words.
     */
    suspend fun searchByMeaning(query: String, all: List<com.grungert.nebo.data.Entry>, exclude: Set<Long> = emptySet(), k: Int = 6):
        List<Pair<com.grungert.nebo.data.Entry, Float>> = withContext(Dispatchers.Default) {
        lock.withLock {
            val e = embedderOrNull() ?: return@withLock emptyList()
            val notes = all.filter { it.words.isNotBlank() }
            if (query.isBlank() || notes.size < 5) return@withLock emptyList()
            val qv = e.embed(query)
            val scored = notes.map { it to Embedder.cosine(qv, passageVector(it, e)) }
            val mean = scored.map { it.second }.average().toFloat()
            // a journal whose notes all score alike has no note that stands out: the spread is taken as at least 0.01,
            // and a note must also be 0.02 above the rest outright
            val sd = kotlin.math.sqrt(scored.sumOf { ((it.second - mean) * (it.second - mean)).toDouble() } / scored.size).toFloat().coerceAtLeast(0.01f)
            val open = scored.filter { it.first.id !in exclude }.sortedByDescending { it.second }
            val best = open.firstOrNull()?.second ?: return@withLock emptyList()
            open.filter { (_, s) -> (s - mean) / sd >= 1.5f && s - mean >= 0.02f && s >= best - 0.025f }.take(k).map { (n, s) -> n to (s - mean) / sd }
        }
    }

    /** Keyword themes, the moods' themes and, with the meaning model, the theme the words lean towards. */
    private fun themesFor(x: com.grungert.nebo.data.Entry, e: Embedder?): List<String> {
        val moods = x.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }
        val semantic = if (e != null && x.words.isNotBlank()) semanticTheme(entryVector(x, e), e) else null
        return (Lexicon.themes(x.words) + moods + listOfNotNull(semantic)).distinct()
    }

    /** Gives every note its themes, once; run again for everything when the way of finding themes changes. */
    suspend fun tagNotes(force: Boolean = false) = withContext(Dispatchers.Default) {
        // the model is held one note at a time, so a save or a search waiting for it is never stuck behind the whole
        // journal; each note is read again just before, so the themes follow its words as they are now
        // a note with no theme to be found is remembered as read, so it is not read (and written) again on every start
        val seen = if (force) HashSet() else settings.themeless.toHashSet()
        val before = seen.toSet()
        for (id in db.entries().map { it.id }) lock.withLock {
            val x = db.entry(id) ?: return@withLock
            if (!force && x.themes.isNotEmpty()) return@withLock
            if (x.words.isBlank() && x.moods.isEmpty()) return@withLock
            val key = "${x.id}:${(x.words + x.moods).hashCode()}"
            if (!force && key in seen) return@withLock
            val themes = themesFor(x, embedderOrNull())
            if (themes != x.themes) db.updateEntry(x.copy(themes = themes), com.grungert.nebo.data.Col.THEMES)
            if (themes.isEmpty()) seen += key
        }
        if (seen != before) settings.themeless = seen
    }

    private fun itemText(it: KoanItem) = it.whenHelps + " " + it.themes.joinToString(" ") { t -> THEME_NAMES[t] ?: t }

    /** Embeds the whole library once and caches it, so the first pick after a session is quick. */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        for (item in library.items) lock.withLock {
            if (!usesMeaning) return@withContext
            val text = itemText(item)
            if (db.embedding(item.id, Embedder.MODEL_ID, text.hashCode()) == null) {
                val e = embedderOrNull() ?: return@withContext
                db.putEmbedding(item.id, Embedder.MODEL_ID, text.hashCode(), e.embed(text))
            }
        }
    }

    /**
     * Earlier notes closest in meaning to this one, most similar first, and only those that are close: e5 rates any two
     * notes in our language around 0.8, so a note must stand clearly above how close the others are, or there is
     * nothing to show. Without the meaning model, notes that share themes or moods (Jaccard overlap) stand in.
     */
    suspend fun similar(target: com.grungert.nebo.data.Entry, all: List<com.grungert.nebo.data.Entry>, k: Int = 4):
        List<Pair<com.grungert.nebo.data.Entry, Float>> = withContext(Dispatchers.Default) {
        lock.withLock {
            val pool = all.filter { it.id != target.id && it.words.isNotBlank() && it.createdAt < target.createdAt }
            if (target.words.isBlank() || pool.isEmpty()) return@withLock emptyList()
            val e = embedderOrNull()
            if (e != null) {
                val tv = entryVector(target, e)
                val scored = pool.map { it to Embedder.cosine(tv, entryVector(it, e)) }
                val mean = scored.map { it.second }.average().toFloat()
                val sd = kotlin.math.sqrt(scored.sumOf { ((it.second - mean) * (it.second - mean)).toDouble() } / scored.size).toFloat()
                // with few notes there is no "usual" to measure against, so they must be close outright
                val close = if (scored.size < 5) scored.filter { it.second >= 0.86f }
                    else scored.filter { (it.second - mean) >= maxOf(0.012f, 0.8f * sd) }
                close.sortedByDescending { it.second }.take(k)
            } else {
                fun tags(x: com.grungert.nebo.data.Entry) = (Lexicon.themes(x.words) + x.moods + x.themes).toSet()
                val tt = tags(target)
                pool.map { x ->
                    val xt = tags(x)
                    x to if (tt.isEmpty() || xt.isEmpty()) 0f else (tt intersect xt).size.toFloat() / (tt union xt).size
                }.filter { it.second > 0f }.sortedByDescending { it.second }.take(k)
            }
        }
    }

    /**
     * Threads of insight: notes that keep returning to the same thing, across days.
     * With the meaning model, notes are joined by average-linkage clustering on what they say: the two closest
     * groups merge while their average similarity stays above a bar set from this person's own notes
     * (e5 scores sit high and close together, so a fixed number would join everything or nothing).
     * On a test set of eleven notes on four topics this found the family, work and sleep threads.
     * Without it, notes are grouped by their main theme. A thread needs two notes on different days.
     */
    suspend fun threads(all: List<com.grungert.nebo.data.Entry>): List<InsightThread> = withContext(Dispatchers.Default) {
        val notes = all.filter { it.words.isNotBlank() }.sortedByDescending { it.createdAt }.take(300)
        if (notes.size < 2) return@withContext emptyList()
        val groups: List<List<com.grungert.nebo.data.Entry>> = lock.withLock {
            val e = embedderOrNull()
            if (e == null || notes.size < 4) {
                // each note joins the thread of its theme that most notes share (not simply its first theme)
                val counts = notes.flatMap { it.themes }.groupingBy { it }.eachCount()
                notes.groupBy { n -> n.themes.maxByOrNull { counts[it] ?: 0 } }.filterKeys { it != null }.values.toList()
            }
            else {
                val v = notes.map { entryVector(it, e) }
                val n = notes.size
                val sim = Array(n) { i -> FloatArray(n) { j -> if (i == j) 1f else Embedder.cosine(v[i], v[j]) } }
                // e5 rates every pair of notes in our language high, and some notes are close to everything;
                // so each pair is measured against how close those two notes usually are to all the others,
                // plus a little when they share a theme
                val usual = FloatArray(n) { i -> (0 until n).filter { it != i }.map { sim[i][it] }.average().toFloat() }
                for (i in 0 until n) for (j in 0 until n) if (i != j) {
                    val a = notes[i].themes.toSet(); val b = notes[j].themes.toSet()
                    val shared = if (a.isEmpty() || b.isEmpty()) 0f else (a intersect b).size.toFloat() / (a union b).size
                    sim[i][j] = sim[i][j] - (usual[i] + usual[j]) / 2 + 0.03f * shared
                }
                val pairs = ArrayList<Float>(n * (n - 1) / 2)
                for (i in 0 until n) for (j in i + 1 until n) pairs += sim[i][j]
                val mean = pairs.average().toFloat()
                val sd = kotlin.math.sqrt(pairs.sumOf { ((it - mean) * (it - mean)).toDouble() } / pairs.size).toFloat()
                val bar = maxOf(0.012f, mean + 0.8f * sd)
                val clusters = MutableList(n) { mutableListOf(it) }
                while (true) {
                    var best = -1f; var bi = -1; var bj = -1
                    for (i in clusters.indices) for (j in i + 1 until clusters.size) {
                        var sum = 0f
                        for (a in clusters[i]) for (b in clusters[j]) sum += sim[a][b]
                        val avg = sum / (clusters[i].size * clusters[j].size)
                        if (avg > best) { best = avg; bi = i; bj = j }
                    }
                    if (bi < 0 || best < bar) break
                    clusters[bi].addAll(clusters[bj]); clusters.removeAt(bj)
                }
                clusters.map { c -> c.map { notes[it] } }
            }
        }
        // the day on the person's own clock: half past midnight and ten in the evening are not the same day in UTC
        fun day(t: Long) = (t + java.util.TimeZone.getDefault().getOffset(t)) / 86_400_000L
        groups.filter { g -> g.size >= 2 && g.map { day(it.createdAt) }.toSet().size >= 2 }
            .map { g ->
                val chrono = g.sortedBy { it.createdAt }
                val theme = g.flatMap { it.themes }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                InsightThread(chrono, theme, THEME_NAMES[theme]?.replaceFirstChar { it.uppercase() }
                    ?: chrono.last().words.split(Regex("\\s+")).take(5).joinToString(" ") + "…")
            }
            .sortedWith(compareByDescending<InsightThread> { day(it.entries.last().createdAt) }.thenByDescending { it.entries.size })
    }

    /**
     * The four points in what someone said. Pieces whose words place them nowhere go by meaning: compared with a
     * description of each point, and placed only when one is clearly closer than the others.
     */
    suspend fun readFourPoints(text: String): FourPointsReader.Reading = withContext(Dispatchers.Default) {
        lock.withLock {
            val e = embedderOrNull()
            val protos = e?.let { em ->
                FourPointsReader.DESCRIPTIONS.mapValues { (k, d) ->
                    db.embedding("fp:$k", Embedder.MODEL_ID, d.hashCode()) ?: em.embed(d).also { db.putEmbedding("fp:$k", Embedder.MODEL_ID, d.hashCode(), it) }
                }
            }
            FourPointsReader.read(text) { piece ->
                if (e == null || protos == null) null else {
                    val v = e.embed(piece)
                    val ranked = protos.map { it.key to Embedder.cosine(v, it.value) }.sortedByDescending { it.second }
                    ranked[0].first.takeIf { ranked[0].second - ranked[1].second > 0.012f }
                }
            }
        }
    }

    /** Plain words for how close two notes are. */
    fun closeness(score: Float): String = when {
        !usesMeaning -> if (score >= 0.5f) "iste teme" else "slične teme"
        // e5 similarities sit high (unrelated notes still score around 0.8), so the bands are narrow
        score >= 0.915f -> "vrlo blisko"
        score >= 0.88f -> "blisko"
        else -> "u istom duhu"
    }

    /**
     * [season] and [weather] are the sky's as the person sat ("jesen", "kiša"): a haiku of the same season comes a little
     * nearer, and one of the same weather nearer still, so a rainy sit may end with a rain haiku.
     */
    suspend fun pick(words: String, moods: List<String>, afterSession: Boolean, exclude: Set<String> = emptySet(), koansOnly: Boolean = false,
                     season: String? = null, weather: String? = null): Pick = withContext(Dispatchers.Default) {
        lock.withLock {
            if (Lexicon.soundsLikeDistress(words)) return@withLock Pick(null, "", distress = true)
            val moodThemes = moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }
            val feedback = db.feedback()
            val now = System.currentTimeMillis()
            // "not now" keeps an item away for two weeks
            val resting = feedback.filter { now - it.value.second < 14 * 86_400_000L }.keys
            val recent = db.recentKoanIds(12).toSet() + exclude + resting
            val pool = if (koansOnly) library.items.filter { it.isKoan } else library.items
            val candidates = pool.filter { it.id !in recent }.ifEmpty { pool }
            if (candidates.isEmpty()) return@withLock Pick(null, "Biblioteka koana još nije učitana.")

            val e = embedderOrNull()
            val query = listOf(words, moodThemes.joinToString(", ") { THEME_NAMES[it] ?: it }).filter { it.isNotBlank() }.joinToString(". ")
            val qv = if (e != null && query.isNotBlank()) e.embed(query) else null
            val detected = (Lexicon.themes(words) + moodThemes + listOfNotNull(if (qv != null && words.isNotBlank()) semanticTheme(e!!.embed(words), e) else null)).distinct()

            val scored = candidates.map { item ->
                val overlap = item.themes.count { it in detected }
                var s = if (qv != null) {
                    val text = itemText(item)
                    val iv = db.embedding(item.id, Embedder.MODEL_ID, text.hashCode())
                        ?: e!!.embed(text).also { db.putEmbedding(item.id, Embedder.MODEL_ID, text.hashCode(), it) }
                    Embedder.cosine(qv, iv) + 0.03f * overlap
                } else overlap.toFloat() + Random.nextFloat() * 0.5f
                if (afterSession && item.isKoan) s += if (qv != null) 0.01f else 0.3f
                // koans that spoke before come round a little more often
                s += (feedback[item.id]?.first ?: 0).coerceAtMost(3) * if (qv != null) 0.006f else 0.15f
                // a haiku of this season comes a touch nearer, and one of this very rain, snow or mist clearly so
                // (a clear sky is most days, and says little)
                if (item.season != null && item.season == season) s += if (qv != null) 0.005f else 0.15f
                if (item.weather != null && item.weather == weather && weather != "vedro") s += if (qv != null) 0.02f else 0.5f
                item to s
            }.sortedByDescending { it.second }

            // a gentle draw among the closest few, so the same words do not always bring the same koan
            val top = scored.take(3)
            val temp = if (qv != null) 0.015f else 0.5f
            val weights = top.map { exp((it.second - top[0].second) / temp) }
            var r = Random.nextFloat() * weights.sum()
            var chosen = top[0].first
            for ((i, w) in weights.withIndex()) { r -= w; if (r <= 0) { chosen = top[i].first; break } }

            val shared = chosen.themes.filter { it in detected }
            val reason = when {
                chosen.weather != null && chosen.weather == weather && weather != "vedro" -> "Za ovo vrijeme: ${weather}."
                shared.isNotEmpty() -> "Prepoznato u onome što si rekao: " + shared.joinToString(", ") { THEME_NAMES[it] ?: it } + "."
                qv != null -> "Najbliže po značenju onome što si rekao."
                words.isBlank() && moods.isEmpty() -> "Za danas, bez posebnog povoda."
                else -> "Izabrano po temama: " + chosen.themes.joinToString(", ") { THEME_NAMES[it] ?: it } + "."
            }
            Pick(chosen, reason, themes = detected)
        }
    }
}
