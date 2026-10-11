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
                )
            }
        }.getOrDefault(emptyList())
        load("koani.json", true) + load("poruke.json", false)
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

/** One-tap moods on the check-in screen, and the theme each one points to. */
val MOODS = listOf(
    "mirno" to "smirenost", "nemirno" to "nemir", "umorno" to "umor", "tužno" to "tuga", "ljuto" to "ljutnja",
    "zabrinuto" to "strah", "zbunjeno" to "zbunjenost", "zahvalno" to "zahvalnost", "radosno" to "radost", "radoznalo" to "radoznalost",
)

/** Word stems that point to a theme; used when the meaning model is not on the phone yet, and as a small bonus. */
object Lexicon {
    private val stems = mapOf(
        "nemir" to listOf("nemir", "nervoz", "napet", "uznemir", "stres", "žurb", "jurim", "anksio", "brig", "brin"),
        "tuga" to listOf("tug", "tužn", "plak", "suz", "žal", "nedostaj", "gubit", "izgubi", "praznin"),
        "ljutnja" to listOf("ljut", "bijes", "iritir", "nervira", "smeta mi", "frustrir"),
        "umor" to listOf("umor", "iscrp", "pospan", "nemam snage", "težak dan", "izmoren"),
        "strah" to listOf("strah", "plaš", "bojim", "panik", "uplaš", "nesigur"),
        "sumnja" to listOf("sumnj", "ne znam", "nisam siguran", "nisam sigurna", "dvoum", "smisao"),
        "zudnja" to listOf("žudn", "želim", "hoću", "treba mi", "vezan", "privrž", "telefon", "još malo"),
        "kontrola" to listOf("kontrol", "plan", "moram", "trebalo bi", "savrš", "perfekc"),
        "samokritika" to listOf("kriv", "sram", "glup", "nisam dovolj", "ne valjam", "kritik", "osuđ", "loš sam", "loša sam"),
        "usamljenost" to listOf("usamlj", "sam sam", "sama sam", "niko", "izolov", "odvojen"),
        "zahvalnost" to listOf("zahval", "hvala", "srećom"),
        "radost" to listOf("radost", "radujem", "srećn", "sretn", "vesel", "lijepo", "uživ", "smij"),
        "radoznalost" to listOf("radoznal", "zanima", "pitam se", "zanimljiv", "istraž"),
        "zbunjenost" to listOf("zbunj", "zbrk", "haos", "ne razumijem", "pomiješ", "pomješ"),
        "smirenost" to listOf("miran", "mirno", "smiren", "spokoj", "tišin", "opušten", "lagan"),
        "ego" to listOf("identitet", "ko sam", "ponos", "ego", "ugled", "šta će drugi", "šta drugi"),
        "prolaznost" to listOf("prolaz", "smrt", "starenj", "vrijeme prolazi", "promjen", "kraj"),
        "svakodnevica" to listOf("posao", "posl", "kuć", "sudov", "obavez", "svakodnev", "rutin"),
        "odluka" to listOf("odluk", "odluč", "izbor", "biram", "raskršć"),
        "odnosi" to listOf("partner", "prijatelj", "porodic", "majk", "mam", "otac", "tat", "brat", "sestr", "djec", "dijete", "odnos", "svađ", "ljubav", "kolega"),
        "prisutnost" to listOf("sada", "ovdje", "dah", "disanj", "tijel", "prisut", "sadašnj"),
    )

    fun themes(text: String): List<String> {
        val t = text.lowercase()
        return stems.filter { (_, words) -> words.any { t.contains(it) } }.keys.toList()
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
                fun vec(x: com.grungert.nebo.data.Entry): FloatArray {
                    val key = "entry:${x.id}"
                    val h = x.words.hashCode()
                    return db.embedding(key, Embedder.MODEL_ID, h) ?: e.embed(x.words).also { db.putEmbedding(key, Embedder.MODEL_ID, h, it) }
                }
                val tv = vec(target)
                pool.map { it to Embedder.cosine(tv, vec(it)) }.sortedByDescending { it.second }.take(k)
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
            val detected = (Lexicon.themes(words) + moodThemes).distinct()
            val recent = db.recentKoanIds(12).toSet() + exclude
            val pool = if (koansOnly) library.items.filter { it.isKoan } else library.items
            val candidates = pool.filter { it.id !in recent }.ifEmpty { pool }
            if (candidates.isEmpty()) return@withLock Pick(null, "Biblioteka koana još nije učitana.")

            val e = embedderOrNull()
            val query = listOf(words, moodThemes.joinToString(", ") { THEME_NAMES[it] ?: it }).filter { it.isNotBlank() }.joinToString(". ")
            val qv = if (e != null && query.isNotBlank()) e.embed(query) else null

            val scored = candidates.map { item ->
                val overlap = item.themes.count { it in detected }
                var s = if (qv != null) {
                    val text = itemText(item)
                    val iv = db.embedding(item.id, Embedder.MODEL_ID, text.hashCode())
                        ?: e!!.embed(text).also { db.putEmbedding(item.id, Embedder.MODEL_ID, text.hashCode(), it) }
                    Embedder.cosine(qv, iv) + 0.03f * overlap
                } else overlap.toFloat() + Random.nextFloat() * 0.5f
                if (afterSession && item.isKoan) s += if (qv != null) 0.01f else 0.3f
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
