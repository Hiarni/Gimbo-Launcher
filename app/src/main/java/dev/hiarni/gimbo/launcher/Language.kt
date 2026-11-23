package dev.hiarni.gimbo.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.util.Locale

// Sprachen
enum class AppLanguage(val code: String?, private val native: String) {
    System(null, ""),
    Deutsch("de", "Deutsch"),
    English("en", "English"),
    Francais("fr", "Français"),
    Espanol("es", "Español"),
    Italiano("it", "Italiano"),
    Portugues("pt", "Português"),
    Nederlands("nl", "Nederlands"),
    Polski("pl", "Polski"),
    Russkiy("ru", "Русский"),
    Japanese("ja", "日本語");

    val label: String get() = if (code == null) tr("System", "System") else native
}

// Wortuhr
class WordClock(
    val prefix: String,
    private val hours: List<String>,
    private val hoursAlt: List<String>,
    private val patterns: Map<Int, String>,
) {
    fun parts(hour24: Int, minute: Int): List<Pair<String, Boolean>> {
        val rounded = ((hour24 * 60 + minute + 2) / 5) * 5
        val m = rounded % 60
        val h = (rounded / 60) % 24
        val text = (patterns[m] ?: return emptyList())
            .replace("{h2}", hoursAlt.getOrElse(h % 12) { hours[h % 12] })
            .replace("{H2}", hoursAlt.getOrElse((h + 1) % 12) { hours[(h + 1) % 12] })
            .replace("{h}", hours[h % 12])
            .replace("{H}", hours[(h + 1) % 12])
        val out = ArrayList<Pair<String, Boolean>>()
        var bright = false
        val buf = StringBuilder()
        for (c in text) {
            if (c == '[' || c == ']') {
                if (buf.isNotEmpty()) out += buf.toString() to bright
                buf.clear()
                bright = c == '['
            } else {
                buf.append(c)
            }
        }
        if (buf.isNotEmpty()) out += buf.toString() to bright
        return out
    }
}

// Übersetzungen
class LanguagePack(val strings: Map<String, String>, val wordClock: WordClock?)

// Aktuelle Sprache
object I18n {
    private val supported = AppLanguage.entries.mapNotNull { it.code }
    private val packs = HashMap<String, LanguagePack?>()

    var code by mutableStateOf(systemCode())
        private set

    val locale: Locale get() = Locale(code)

    fun apply(language: AppLanguage) {
        code = language.code ?: systemCode()
    }

    fun pack(): LanguagePack? {
        val c = code
        if (c == "de" || c == "en") return null
        return synchronized(packs) { packs.getOrPut(c) { load(c) } }
    }

    private fun systemCode(): String = Locale.getDefault().language.let { if (it in supported) it else "en" }

    private fun load(code: String): LanguagePack? = runCatching {
        val text = I18n::class.java.getResourceAsStream("/i18n/$code.json")!!.bufferedReader().use { it.readText() }
        val root = JSONObject(text)
        val s = root.getJSONObject("strings")
        val strings = HashMap<String, String>(s.length() * 2)
        s.keys().forEach { k -> s.optString(k).takeIf { it.isNotBlank() }?.let { strings[k] = it } }
        val wc = root.optJSONObject("wordclock")?.let { w ->
            fun list(name: String) = w.optJSONArray(name)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val hours = list("hours")
            val p = w.getJSONObject("patterns")
            val patterns = p.keys().asSequence().associate { it.toInt() to p.getString(it) }
            if (hours.size == 12 && patterns.size == 12) WordClock(w.optString("prefix"), hours, list("hoursAlt"), patterns) else null
        }
        LanguagePack(strings, wc)
    }.getOrNull()
}

fun tr(de: String, en: String): String = when (I18n.code) {
    "de" -> de
    "en" -> en
    else -> I18n.pack()?.strings?.get(en) ?: en
}

fun trf(de: String, en: String, vararg args: Any?): String {
    var s = tr(de, en)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a?.toString() ?: "") }
    return s
}
