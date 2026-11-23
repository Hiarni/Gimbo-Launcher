package dev.hiarni.gimbo.launcher

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Kodi
data class KodiAccount(val host: String, val port: Int, val user: String, val password: String) {
    val base: String get() = "http://$host:$port"
    val auth: String? get() = if (user.isEmpty() && password.isEmpty()) null
    else "Basic " + Base64.encodeToString("$user:$password".toByteArray(), Base64.NO_WRAP)
}

object Kodi {
    private const val PREFS = "kodi"
    private val packages = listOf("org.xbmc.kodi", "org.xbmc.kodi.beta")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun account(context: Context): KodiAccount? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.getBoolean("connected", false)) return null
        return KodiAccount(
            p.getString("host", "127.0.0.1") ?: "127.0.0.1",
            p.getInt("port", 8080),
            p.getString("user", "kodi") ?: "",
            p.getString("password", "") ?: "",
        ).also { MediaImages.authorize(it.base, it.auth) }
    }

    fun draft(context: Context): KodiAccount {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return KodiAccount(
            p.getString("host", "127.0.0.1") ?: "127.0.0.1",
            p.getInt("port", 8080),
            p.getString("user", "kodi") ?: "",
            p.getString("password", "") ?: "",
        )
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("connected", false).apply()
    }

    fun installed(context: Context): String? = packages.firstOrNull { InstalledApps.isInstalled(context.packageManager, it) }

    suspend fun check(account: KodiAccount): Int = withContext(Dispatchers.IO) {
        val (code, body) = rpc(account, "JSONRPC.Ping", JSONObject())
        when {
            code == 200 && body?.optString("result") == "pong" -> 200
            else -> code
        }
    }

    fun save(context: Context, account: KodiAccount) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("host", account.host)
            .putInt("port", account.port)
            .putString("user", account.user)
            .putString("password", account.password)
            .putBoolean("connected", true)
            .apply()
        MediaImages.authorize(account.base, account.auth)
    }

    suspend fun fetchItems(context: Context, limit: Int = 10): List<MediaItem>? = withContext(Dispatchers.IO) {
        val account = account(context) ?: return@withContext null
        val filter = JSONObject().put("field", "inprogress").put("operator", "true").put("value", "")
        val sort = JSONObject().put("method", "lastplayed").put("order", "descending")
        val limits = JSONObject().put("start", 0).put("end", limit)
        val movies = rpc(
            account, "VideoLibrary.GetMovies",
            JSONObject().put("filter", filter).put("sort", sort).put("limits", limits)
                .put("properties", JSONArray(listOf("title", "resume", "art", "lastplayed", "runtime"))),
        )
        if (movies.first != 200) return@withContext null
        val episodes = rpc(
            account, "VideoLibrary.GetEpisodes",
            JSONObject().put("filter", filter).put("sort", sort).put("limits", limits)
                .put("properties", JSONArray(listOf("title", "showtitle", "season", "episode", "resume", "art", "lastplayed"))),
        )
        val items = ArrayList<Pair<String, MediaItem>>()
        movies.second?.optJSONObject("result")?.optJSONArray("movies")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items += o.optString("lastplayed") to toItem(account, o, "movie")
            }
        }
        episodes.second?.optJSONObject("result")?.optJSONArray("episodes")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items += o.optString("lastplayed") to toItem(account, o, "episode")
            }
        }
        items.sortedByDescending { it.first }.map { it.second }.take(limit)
    }

    private fun toItem(account: KodiAccount, o: JSONObject, type: String): MediaItem {
        val resume = o.optJSONObject("resume")
        val position = resume?.optDouble("position", 0.0) ?: 0.0
        val total = resume?.optDouble("total", 0.0) ?: 0.0
        val progress = if (total > 0) (position / total).toFloat().coerceIn(0f, 1f) else 0f
        val rest = remainingText(position > 0, if (total > 0) ((total - position) / 60).toInt() else 0)
        val art = o.optJSONObject("art")
        fun img(vararg keys: String) = keys.firstNotNullOfOrNull { k -> art?.optString(k)?.takeIf { it.isNotBlank() } }
            ?.let { "${account.base}/image/${URLEncoder.encode(it, "UTF-8")}" }
        return if (type == "episode") {
            val season = o.optInt("season")
            val episode = o.optInt("episode")
            MediaItem(
                id = "e" + o.getInt("episodeid"),
                title = o.optString("showtitle").ifEmpty { o.optString("title") },
                subtitle = buildList {
                    if (season > 0 && episode > 0) add(trf("S{0} · F{1}", "S{0} · E{1}", season, episode))
                    rest?.let { add(it) }
                }.joinToString(" · ").ifEmpty { tr("Weiterschauen", "Continue watching") },
                progress = progress,
                thumbUrl = img("thumb", "tvshow.fanart", "tvshow.landscape"),
                backdropUrl = img("tvshow.fanart", "fanart"),
                source = Source.Kodi,
                ref = "episodeid:" + o.getInt("episodeid"),
            )
        } else {
            MediaItem(
                id = "m" + o.getInt("movieid"),
                title = o.optString("title"),
                subtitle = listOfNotNull(tr("Film", "Movie"), rest).joinToString(" · "),
                progress = progress,
                thumbUrl = img("fanart", "landscape", "thumb", "poster"),
                backdropUrl = img("fanart"),
                source = Source.Kodi,
                ref = "movieid:" + o.getInt("movieid"),
            )
        }
    }

    fun open(context: Context, item: MediaItem): Boolean {
        val pkg = installed(context) ?: return false
        val account = account(context) ?: return false
        val intent = AppResolver.launchIntent(context.packageManager, pkg) ?: return false
        if (!AppLauncher.tryStart(context, intent)) return false
        val (field, id) = item.ref.split(':').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: return true) }
        scope.launch {
            val deadline = System.currentTimeMillis() + 30_000
            while (System.currentTimeMillis() < deadline) {
                if (check(account) == 200) {
                    delay(1_500)
                    rpc(
                        account, "Player.Open",
                        JSONObject().put("item", JSONObject().put(field, id)).put("options", JSONObject().put("resume", true)),
                    )
                    return@launch
                }
                delay(1_000)
            }
        }
        return true
    }

    private fun rpc(account: KodiAccount, method: String, params: JSONObject): Pair<Int, JSONObject?> = runCatching {
        val c = (URL("${account.base}/jsonrpc").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            account.auth?.let { setRequestProperty("Authorization", it) }
        }
        try {
            val payload = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params)
            c.outputStream.use { it.write(payload.toString().toByteArray()) }
            val code = c.responseCode
            code to (if (code == 200) JSONObject(c.inputStream.bufferedReader().use { it.readText() }) else null)
        } finally {
            c.disconnect()
        }
    }.getOrDefault(-1 to null)
}
