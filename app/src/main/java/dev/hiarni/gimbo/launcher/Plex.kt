package dev.hiarni.gimbo.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

// Plex
data class PlexAccount(val token: String, val server: String, val serverName: String, val machineId: String, val serverToken: String)

data class PlexPin(val id: Long, val code: String)

data class PlexServer(val name: String, val machineId: String, val token: String, val connections: List<String>)

object Plex {
    private const val PREFS = "plex"
    private const val PRODUCT = "Gimbo Launcher"
    private val packages = listOf("com.plexapp.android")

    fun account(context: Context): PlexAccount? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PlexAccount(
            token = p.getString("token", null) ?: return null,
            server = p.getString("server", null) ?: return null,
            serverName = p.getString("serverName", "") ?: "",
            machineId = p.getString("machineId", "") ?: "",
            serverToken = p.getString("serverToken", null) ?: return null,
        )
    }

    fun signOut(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("clientId", null)
        p.edit().clear().apply()
        if (id != null) p.edit().putString("clientId", id).apply()
    }

    private fun clientId(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString("clientId", null) ?: UUID.randomUUID().toString().also { p.edit().putString("clientId", it).apply() }
    }

    suspend fun startPin(context: Context): PlexPin? = withContext(Dispatchers.IO) {
        val (code, body) = request(context, "POST", "https://plex.tv/api/v2/pins?strong=false")
        if (code !in 200..299 || body == null) return@withContext null
        runCatching { JSONObject(body).let { PlexPin(it.getLong("id"), it.getString("code")) } }.getOrNull()
    }

    suspend fun pinToken(context: Context, pin: PlexPin): String? = withContext(Dispatchers.IO) {
        val (code, body) = request(context, "GET", "https://plex.tv/api/v2/pins/${pin.id}")
        if (code != 200 || body == null) return@withContext null
        runCatching { JSONObject(body).optString("authToken").takeIf { it.isNotBlank() && it != "null" } }.getOrNull()
    }

    suspend fun servers(context: Context, token: String): List<PlexServer> = withContext(Dispatchers.IO) {
        val (code, body) = request(context, "GET", "https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", token)
        if (code != 200 || body == null) return@withContext emptyList()
        runCatching {
            val a = JSONArray(body)
            (0 until a.length()).map { a.getJSONObject(it) }
                .filter { it.optString("provides").contains("server") }
                .map { o ->
                    val conns = o.optJSONArray("connections") ?: JSONArray()
                    val list = (0 until conns.length()).map { conns.getJSONObject(it) }
                        .sortedBy { c -> (if (c.optBoolean("local")) 0 else 2) + (if (c.optBoolean("relay")) 4 else 0) }
                        .map { it.optString("uri").trimEnd('/') }
                        .filter { it.isNotBlank() }
                    PlexServer(o.optString("name", "Plex"), o.optString("clientIdentifier"), o.optString("accessToken", token), list)
                }
                .filter { it.connections.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    suspend fun connect(context: Context, token: String, server: PlexServer): Boolean = withContext(Dispatchers.IO) {
        val uri = server.connections.firstOrNull { reachable(context, it, server.token) } ?: return@withContext false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token)
            .putString("server", uri)
            .putString("serverName", server.name)
            .putString("machineId", server.machineId)
            .putString("serverToken", server.token)
            .apply()
        true
    }

    private fun reachable(context: Context, uri: String, token: String): Boolean =
        request(context, "GET", "$uri/identity", token, timeout = 3_000).first == 200

    suspend fun fetchItems(context: Context, limit: Int = 10): List<MediaItem>? = withContext(Dispatchers.IO) {
        val account = account(context) ?: return@withContext null
        var server = account.server
        var result = request(context, "GET", "$server/hubs/continueWatching/items?count=$limit", account.serverToken)
        if (result.first == -1) {
            val other = servers(context, account.token).firstOrNull { it.machineId == account.machineId }
            val uri = other?.connections?.firstOrNull { it != server && reachable(context, it, other.token) }
            if (uri != null) {
                server = uri
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("server", uri).apply()
                result = request(context, "GET", "$server/hubs/continueWatching/items?count=$limit", account.serverToken)
            }
        }
        if (result.first == 401) {
            signOut(context)
            return@withContext emptyList()
        }
        if (result.first == 404) result = request(context, "GET", "$server/library/onDeck", account.serverToken)
        val body = result.second ?: return@withContext null
        val meta = runCatching { JSONObject(body).getJSONObject("MediaContainer").optJSONArray("Metadata") }.getOrNull()
            ?: return@withContext emptyList()
        (0 until meta.length()).asSequence()
            .mapNotNull { runCatching { toItem(server, account.serverToken, meta.getJSONObject(it)) }.getOrNull() }
            .take(limit)
            .toList()
    }

    private fun toItem(server: String, token: String, o: JSONObject): MediaItem {
        val key = o.getString("ratingKey")
        val type = o.optString("type")
        val duration = o.optLong("duration")
        val offset = o.optLong("viewOffset")
        val progress = if (duration > 0) (offset.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val rest = remainingText(offset > 0, if (duration > 0) ((duration - offset).coerceAtLeast(0) / 60_000L).toInt() else 0)
        fun img(path: String?, w: Int, h: Int) = path?.takeIf { it.isNotBlank() }?.let {
            "$server/photo/:/transcode?width=$w&height=$h&minSize=1&upscale=1&url=${URLEncoder.encode(it, "UTF-8")}&X-Plex-Token=$token"
        }
        return if (type == "episode") {
            val season = o.optInt("parentIndex")
            val episode = o.optInt("index")
            MediaItem(
                id = key,
                title = o.optString("grandparentTitle").ifEmpty { o.optString("title") },
                subtitle = buildList {
                    if (season > 0 && episode > 0) add(trf("S{0} · F{1}", "S{0} · E{1}", season, episode))
                    add(rest ?: tr("Nächste Folge", "Next episode"))
                }.joinToString(" · "),
                progress = progress,
                thumbUrl = img(o.optString("thumb").ifEmpty { o.optString("grandparentArt") }, 480, 270),
                backdropUrl = img(o.optString("grandparentArt").ifEmpty { o.optString("art") }, 1920, 1080),
                source = Source.Plex,
            )
        } else {
            MediaItem(
                id = key,
                title = o.optString("title"),
                subtitle = listOfNotNull(if (type == "movie") tr("Film", "Movie") else null, rest).joinToString(" · ")
                    .ifEmpty { tr("Weiterschauen", "Continue watching") },
                progress = progress,
                thumbUrl = img(o.optString("art").ifEmpty { o.optString("thumb") }, 480, 270),
                backdropUrl = img(o.optString("art"), 1920, 1080),
                source = Source.Plex,
            )
        }
    }

    fun installed(context: Context): String? = packages.firstOrNull { InstalledApps.isInstalled(context.packageManager, it) }

    fun open(context: Context, item: MediaItem): Boolean {
        val pkg = installed(context) ?: return false
        val machine = account(context)?.machineId.orEmpty()
        if (machine.isNotEmpty()) {
            val key = URLEncoder.encode("/library/metadata/${item.id}", "UTF-8")
            val link = Intent(Intent.ACTION_VIEW, Uri.parse("https://app.plex.tv/desktop/#!/server/$machine/details?key=$key"))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (AppLauncher.tryStart(context, link)) return true
        }
        return AppResolver.launchIntent(context.packageManager, pkg)?.let { AppLauncher.tryStart(context, it) } ?: false
    }

    private fun request(context: Context, method: String, url: String, token: String? = null, timeout: Int = 8_000): Pair<Int, String?> =
        runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = timeout
                readTimeout = timeout + 4_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Plex-Product", PRODUCT)
                setRequestProperty("X-Plex-Client-Identifier", clientId(context))
                setRequestProperty("X-Plex-Device-Name", "Fire TV")
                setRequestProperty("X-Plex-Platform", "Android")
                if (token != null) setRequestProperty("X-Plex-Token", token)
                if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Length", "0")
                }
            }
            try {
                if (method == "POST") c.outputStream.close()
                val code = c.responseCode
                code to (if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null)
            } finally {
                c.disconnect()
            }
        }.getOrDefault(-1 to null)
}
