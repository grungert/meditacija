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
                )
            }
        }.getOrDefault(emptyList())
        // readings: Zen stories, Zhuangzi, the Tao Te Ching and the Dhammapada, chosen by meaning like the messages
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
        "tuga" to listOf("tug*", "tužn*", "tužan", "plak*", "plač*", "suz*", "žal*", "nedostaj*", "gubit*", "izgubi*", "praznin*"),
        "ljutnja" to listOf("ljut*", "bijes*", "iritir*", "nervira*", "smeta mi", "frustrir*"),
        "umor" to listOf("umor*", "iscrp*", "pospan*", "nemam snage", "težak dan", "izmoren*", "premoren*"),
        "strah" to listOf("strah*", "plaš*", "bojim", "boji", "panik*", "uplaš*", "nesigur*"),
        "sumnja" to listOf("sumnj*", "ne znam", "nisam siguran", "nisam sigurna", "dvoum*", "smisao", "smisla"),
        "zudnja" to listOf("žudn*", "žudim", "želim", "hoću", "treba mi", "vezan*", "privrž*", "telefon*", "još malo", "ne mogu bez"),
        "kontrola" to listOf("kontrol*", "plan", "plana", "planu", "planov*", "planir*", "moram", "trebalo bi", "savrš*", "perfekc*"),
        "samokritika" to listOf("kriv", "kriva", "krivic*", "sram*", "stid*", "glup*", "nisam dovolj*", "ne valjam", "kritikuj*", "osuđ*", "loš sam", "loša sam"),
        "usamljenost" to listOf("usamlj*", "sam sam", "sama sam", "niko", "nikoga", "nikome", "izolov*", "odvojen*"),
        "zahvalnost" to listOf("zahval*", "hvala", "srećom"),
        "radost" to listOf("radost*", "radujem", "raduje", "srećn*", "srećan", "sretn*", "sretan", "vesel*", "lijepo", "uživ*", "smijeh*", "smijao", "smijala", "nasmij*"),
        "radoznalost" to listOf("radoznal*", "zanima*", "pitam se", "zanimljiv*", "istraž*"),
        "zbunjenost" to listOf("zbunj*", "zbrk*", "haos*", "ne razumijem", "pomiješ*", "pomješ*"),
        "smirenost" to listOf("miran", "mirna", "mirno", "mirnije", "smiren*", "spokoj*", "tišin*", "opušten*", "lagan*", "lakoć*"),
        "ego" to listOf("identitet*", "ko sam", "ponos*", "ego", "ega", "egu", "ugled*", "šta će drugi", "šta drugi"),
        "prolaznost" to listOf("prolaz*", "smrt*", "starenj*", "vrijeme prolazi", "promjen*", "kraj", "kraja", "kraju"),
        "svakodnevica" to listOf("posao", "posla", "poslu", "poslom", "poslov*", "kuć*", "sudov*", "obavez*", "svakodnev*", "rutin*"),
        "odluka" to listOf("odluk*", "odluč*", "izbor*", "biram", "birati", "raskršć*"),
        "odnosi" to listOf("partner*", "prijatelj*", "porodic*", "majk*", "mama", "mami", "mamu", "mamom", "otac", "oca", "ocu", "tata", "tati", "tatu",
            "brat", "brata", "bratu", "bratom", "sestr*", "djec*", "dijete", "djetet*", "odnos*", "svađ*", "ljubav*", "kolega", "kolege", "kolegom", "kolegama", "muž*", "žen*", "djevojk*", "dečk*"),
        "prisutnost" to listOf("sada", "ovdje", "dah", "daha", "dahom", "disanj*", "dišem", "tijel*", "prisut*", "sadašnj*"),
    )

    private val patterns: Map<String, Regex> = words.mapValues { (_, list) ->
        val alts = list.joinToString("|") { w ->
            val parts = w.split(" ").map { part -> if (part.endsWith("*")) Regex.escape(part.dropLast(1)) + "\\p{L}*" else Regex.escape(part) }
            parts.joinToString("\\s+")
        }
        Regex("(?<![\\p{L}])(?:$alts)(?![\\p{L}])")
    }

    fun themes(text: String): List<String> {
        val t = text.lowercase()
        return patterns.filter { (_, re) -> re.containsMatchIn(t) }.keys.toList()
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

class Recommender(private val db: Db, private val models: ModelStore, private val library: KoanLibrary) {
    private val lock = Mutex()
    private var embedder: Embedder? = null

    val usesMeaning get() = models.isReady(ModelCatalog.meaning)

    private fun embedderOrNull(): Embedder? {
        if (!usesMeaning) return null
        if (embedder == null) runCatching {
            embedder = Embedder(models.file(ModelCatalog.meaning.files[0].name), models.file(ModelCatalog.meaning.files[1].name))
        }
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

    /** Keyword themes, the moods' themes and, with the meaning model, the theme the words lean towards. */
    private fun themesFor(x: com.grungert.nebo.data.Entry, e: Embedder?): List<String> {
        val moods = x.moods.mapNotNull { m -> MOODS.firstOrNull { it.first == m }?.second }
        val semantic = if (e != null && x.words.isNotBlank()) semanticTheme(entryVector(x, e), e) else null
        return (Lexicon.themes(x.words) + moods + listOfNotNull(semantic)).distinct()
    }

    /** Gives every note its themes, once; run again for everything when the way of finding themes changes. */
    suspend fun tagNotes(force: Boolean = false) = withContext(Dispatchers.Default) {
        lock.withLock {
            val e = embedderOrNull()
            for (x in db.entries()) {
                if (!force && x.themes.isNotEmpty()) continue
                if (x.words.isBlank() && x.moods.isEmpty()) continue
                db.updateEntry(x.copy(themes = themesFor(x, e)))
            }
        }
    }

    private fun itemText(it: KoanItem) = it.whenHelps + " " + it.themes.joinToString(" ") { t -> THEME_NAMES[t] ?: t }

    /** Embeds the whole library once and caches it, so the first pick after a session is quick. */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        lock.withLock {
            val e = embedderOrNull() ?: return@withLock
            for (item in library.items) {
                val text = itemText(item)
                if (db.embedding(item.id, Embedder.MODEL_ID, text.hashCode()) == null) db.putEmbedding(item.id, Embedder.MODEL_ID, text.hashCode(), e.embed(text))
            }
        }
    }

    /**
     * Earlier notes closest in meaning to this one, most similar first. Without the meaning model,
     * notes that share themes or moods (Jaccard overlap) stand in.
     */
    suspend fun similar(target: com.grungert.nebo.data.Entry, all: List<com.grungert.nebo.data.Entry>, k: Int = 4):
        List<Pair<com.grungert.nebo.data.Entry, Float>> = withContext(Dispatchers.Default) {
        lock.withLock {
            val pool = all.filter { it.id != target.id && it.words.isNotBlank() }
            if (target.words.isBlank() || pool.isEmpty()) return@withLock emptyList()
            val e = embedderOrNull()
            if (e != null) {
                val tv = entryVector(target, e)
                pool.map { it to Embedder.cosine(tv, entryVector(it, e)) }.sortedByDescending { it.second }.take(k)
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
            if (e == null || notes.size < 4) notes.groupBy { it.themes.firstOrNull() }.filterKeys { it != null }.values.toList()
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
        fun day(t: Long) = t / 86_400_000L
        groups.filter { g -> g.size >= 2 && g.map { day(it.createdAt) }.toSet().size >= 2 }
            .map { g ->
                val chrono = g.sortedBy { it.createdAt }
                val theme = g.flatMap { it.themes }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                InsightThread(chrono, theme, THEME_NAMES[theme]?.replaceFirstChar { it.uppercase() }
                    ?: chrono.last().words.split(Regex("\\s+")).take(5).joinToString(" ") + "…")
            }
            .sortedWith(compareByDescending<InsightThread> { it.entries.last().createdAt / 86_400_000L }.thenByDescending { it.entries.size })
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

    suspend fun pick(words: String, moods: List<String>, afterSession: Boolean, exclude: Set<String> = emptySet(), koansOnly: Boolean = false): Pick = withContext(Dispatchers.Default) {
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
                shared.isNotEmpty() -> "Prepoznato u onome što si rekao: " + shared.joinToString(", ") { THEME_NAMES[it] ?: it } + "."
                qv != null -> "Najbliže po značenju onome što si rekao."
                words.isBlank() && moods.isEmpty() -> "Za danas, bez posebnog povoda."
                else -> "Izabrano po temama: " + chosen.themes.joinToString(", ") { THEME_NAMES[it] ?: it } + "."
            }
            Pick(chosen, reason, themes = detected)
        }
    }
}
