package dev.hiarni.gimbo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

// Datenmodelle
data class JellyfinServer(val name: String, val address: String)

data class JellyfinAccount(
    val server: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val token: String,
)

data class QuickConnectCode(val code: String, val secret: String)

// Jellyfin und Emby
object Jellyfin : MediaServerClient(
    source = Source.Jellyfin,
    prefs = "jellyfin",
    discovery = "who is JellyfinServer?",
    packages = BuiltIn.jellyfin,
    startActivity = "org.jellyfin.androidtv.ui.startup.StartupActivity",
    quickConnect = true,
)

object Emby : MediaServerClient(
    source = Source.Emby,
    prefs = "emby",
    discovery = "who is EmbyServer?",
    packages = listOf("tv.emby.embyatv", "com.mb.android"),
    startActivity = null,
    quickConnect = false,
)

open class MediaServerClient(
    val source: Source,
    private val prefs: String,
    private val discovery: String,
    private val packages: List<String>,
    private val startActivity: String?,
    val quickConnect: Boolean,
) {
    private val client = "Gimbo Launcher"
    private val ticksPerMinute = 600_000_000L

    @Volatile var lastLoginCode = 0

    fun account(context: Context): JellyfinAccount? {
        val p = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        val token = p.getString("token", null) ?: return null
        return JellyfinAccount(
            server = p.getString("server", null) ?: return null,
            serverName = p.getString("serverName", "") ?: "",
            userId = p.getString("userId", null) ?: return null,
            userName = p.getString("userName", "") ?: "",
            token = token,
        )
    }

    private fun save(context: Context, account: JellyfinAccount) {
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .putString("server", account.server)
            .putString("serverName", account.serverName)
            .putString("userId", account.userId)
            .putString("userName", account.userName)
            .putString("token", account.token)
            .apply()
    }

    fun signOut(context: Context) {
        val p = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        val deviceId = p.getString("deviceId", null)
        p.edit().clear().apply()
        if (deviceId != null) p.edit().putString("deviceId", deviceId).apply()
    }

    private fun deviceId(context: Context): String {
        val p = context.getSharedPreferences(prefs, Context.MODE_PRIVATE)
        return p.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            p.edit().putString("deviceId", it).apply()
        }
    }

    suspend fun discover(context: Context): List<JellyfinServer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("gimbo")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        try {
            val found = LinkedHashMap<String, Triple<String, String, String>>()
            runCatching {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 600
                    val message = discovery.toByteArray()
                    socket.send(DatagramPacket(message, message.size, InetAddress.getByName("255.255.255.255"), 7359))
                    val end = System.currentTimeMillis() + 2_500
                    val buffer = ByteArray(4096)
                    while (System.currentTimeMillis() < end) {
                        try {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            val o = JSONObject(String(packet.data, 0, packet.length))
                            val address = o.optString("Address").trimEnd('/')
                            if (address.isNotBlank()) {
                                val port = runCatching { java.net.URI(address).port }.getOrDefault(-1).takeIf { it > 0 } ?: 8096
                                val sender = "http://${packet.address.hostAddress}:$port"
                                found[o.optString("Id", address)] = Triple(o.optString("Name", source.title), address, sender)
                            }
                        } catch (_: SocketTimeoutException) {
                        }
                    }
                }
            }
            found.values.map { (name, advertised, sender) ->
                val address = when {
                    check(advertised) != null -> advertised
                    sender != advertised && check(sender) != null -> sender
                    else -> advertised
                }
                JellyfinServer(name, address)
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    fun normalize(input: String): String {
        var a = input.trim().trimEnd('/')
        val explicit = a.startsWith("http://") || a.startsWith("https://")
        if (!explicit) a = "http://$a"
        val hostPart = a.substringAfter("://")
        if (!explicit && !hostPart.substringBefore('/').contains(':')) {
            a = a.substringBefore("://") + "://" + hostPart.substringBefore('/') + ":8096" +
                hostPart.substringAfter('/', "").let { if (it.isEmpty()) "" else "/$it" }
        }
        return a
    }

    suspend fun check(address: String): String? = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "$address/System/Info/Public")
        if (code != 200 || body == null) null else runCatching { JSONObject(body).optString("ServerName", source.title) }.getOrNull()
    }

    suspend fun quickConnectStart(context: Context, server: String): QuickConnectCode? = withContext(Dispatchers.IO) {
        if (!quickConnect) return@withContext null
        val auth = authHeader(context, null)
        var (code, body) = request("POST", "$server/QuickConnect/Initiate", auth = auth)
        if (code == 404 || code == 405) {
            val r = request("GET", "$server/QuickConnect/Initiate", auth = auth)
            code = r.first
            body = r.second
        }
        if (code != 200 || body == null) return@withContext null
        runCatching {
            val o = JSONObject(body)
            QuickConnectCode(o.getString("Code"), o.getString("Secret"))
        }.getOrNull()
    }

    suspend fun quickConnectApproved(server: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        val (code, body) = request("GET", "$server/QuickConnect/Connect?Secret=${enc(secret)}")
        code == 200 && body != null && runCatching { JSONObject(body).optBoolean("Authenticated") }.getOrDefault(false)
    }

    suspend fun quickConnectFinish(context: Context, server: String, serverName: String, secret: String): JellyfinAccount? =
        withContext(Dispatchers.IO) {
            val (code, body) = request(
                "POST",
                "$server/Users/AuthenticateWithQuickConnect",
                body = JSONObject().put("Secret", secret).toString(),
                auth = authHeader(context, null),
            )
            if (code != 200 || body == null) null else finishLogin(context, server, serverName, body)
        }

    suspend fun passwordLogin(context: Context, server: String, serverName: String, user: String, password: String): JellyfinAccount? =
        withContext(Dispatchers.IO) {
            val (code, body) = request(
                "POST",
                "$server/Users/AuthenticateByName",
                body = JSONObject().put("Username", user).put("Pw", password).put("Password", password).toString(),
                auth = authHeader(context, null),
            )
            lastLoginCode = code
            if (code != 200 || body == null) null else finishLogin(context, server, serverName, body)
        }

    private fun finishLogin(context: Context, server: String, serverName: String, body: String): JellyfinAccount? = runCatching {
        val o = JSONObject(body)
        val user = o.getJSONObject("User")
        JellyfinAccount(server, serverName, user.getString("Id"), user.optString("Name"), o.getString("AccessToken"))
            .also { save(context, it) }
    }.getOrNull()

    suspend fun fetchItems(context: Context, limit: Int = 10): List<MediaItem>? = withContext(Dispatchers.IO) {
        val account = account(context) ?: return@withContext null
        val auth = authHeader(context, account.token)
        val fields = "&Fields=PrimaryImageAspectRatio,UserData&EnableImageTypes=Primary,Backdrop,Thumb&ImageTypeLimit=1"
        val newApi = if (source == Source.Jellyfin) {
            getItems("${account.server}/UserItems/Resume?userId=${account.userId}&Limit=$limit&MediaTypes=Video$fields", auth)
        } else {
            -1 to null
        }
        if (newApi.first == 401) {
            signOut(context)
            return@withContext emptyList()
        }
        val resume = newApi.second ?: run {
            val old = getItems("${account.server}/Users/${account.userId}/Items/Resume?Limit=$limit&MediaTypes=Video$fields", auth)
            if (old.first == 401) {
                signOut(context)
                return@withContext emptyList()
            }
            old.second
        } ?: return@withContext null
        val nextUp = getItems("${account.server}/Shows/NextUp?userId=${account.userId}&UserId=${account.userId}&Limit=$limit&EnableResumable=false$fields", auth).second
            ?: JSONArray()

        val result = ArrayList<MediaItem>()
        val seenSeries = HashSet<String>()
        for (arr in listOf(resume, nextUp)) {
            for (i in 0 until arr.length()) {
                if (result.size >= limit) break
                val o = arr.optJSONObject(i) ?: continue
                val series = o.optString("SeriesId")
                if (series.isNotEmpty() && !seenSeries.add(series)) continue
                runCatching { toItem(account.server, account.token, o) }.getOrNull()?.let { result += it }
            }
        }
        if (account(context)?.token != account.token) return@withContext null
        result
    }

    private fun getItems(url: String, auth: String): Pair<Int, JSONArray?> {
        val (code, body) = request("GET", url, auth = auth)
        if (code != 200 || body == null) return code to null
        return code to runCatching { JSONObject(body).getJSONArray("Items") }.getOrNull()
    }

    private fun toItem(server: String, token: String, o: JSONObject): MediaItem {
        val id = o.getString("Id")
        val type = o.optString("Type")
        val user = o.optJSONObject("UserData")
        val runtime = o.optLong("RunTimeTicks")
        val position = user?.optLong("PlaybackPositionTicks") ?: 0L
        val progress = when {
            runtime > 0 && position > 0 -> (position.toFloat() / runtime).coerceIn(0f, 1f)
            else -> ((user?.optDouble("PlayedPercentage", 0.0) ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
        }
        val remaining = if (runtime > 0) ((runtime - position).coerceAtLeast(0) / ticksPerMinute).toInt() else 0
        val rest = remainingText(position > 0, remaining)

        val imageTags = o.optJSONObject("ImageTags")
        val backdrops = o.optJSONArray("BackdropImageTags")
        val key = if (source == Source.Emby) "&api_key=$token" else ""
        fun img(itemId: String, type: String, tag: String?, width: Int) =
            "$server/Items/$itemId/Images/$type?fillWidth=$width&maxWidth=$width&quality=85" + (tag?.let { "&tag=$it" } ?: "") + key

        return if (type == "Episode") {
            val season = o.optInt("ParentIndexNumber", 0)
            val episode = o.optInt("IndexNumber", 0)
            val sub = buildList {
                if (season > 0 && episode > 0) add(trf("S{0} · F{1}", "S{0} · E{1}", season, episode))
                add(rest ?: tr("Nächste Folge", "Next episode"))
            }.joinToString(" · ")
            val parentBackdropId = o.optString("ParentBackdropItemId").ifEmpty { o.optString("SeriesId") }
            val parentBackdropTag = o.optJSONArray("ParentBackdropImageTags")?.optString(0)
            MediaItem(
                id = id,
                title = o.optString("SeriesName").ifEmpty { o.optString("Name") },
                subtitle = sub,
                progress = progress,
                thumbUrl = imageTags?.optString("Primary")?.takeIf { it.isNotEmpty() }?.let { img(id, "Primary", it, 480) }
                    ?: o.optString("ParentThumbItemId").takeIf { it.isNotEmpty() }?.let { img(it, "Thumb", o.optString("ParentThumbImageTag"), 480) },
                backdropUrl = parentBackdropId.takeIf { it.isNotEmpty() }?.let { img(it, "Backdrop", parentBackdropTag, 1920) },
                source = source,
            )
        } else {
            val backdropTag = backdrops?.optString(0)?.takeIf { it.isNotEmpty() }
            val thumbTag = imageTags?.optString("Thumb")?.takeIf { it.isNotEmpty() }
            MediaItem(
                id = id,
                title = o.optString("Name"),
                subtitle = listOfNotNull(if (type == "Movie") tr("Film", "Movie") else null, rest).joinToString(" · ").ifEmpty { tr("Weiterschauen", "Continue watching") },
                progress = progress,
                thumbUrl = when {
                    thumbTag != null -> img(id, "Thumb", thumbTag, 480)
                    backdropTag != null -> img(id, "Backdrop", backdropTag, 480)
                    else -> img(id, "Primary", imageTags?.optString("Primary"), 480)
                },
                backdropUrl = backdropTag?.let { img(id, "Backdrop", it, 1920) },
                source = source,
            )
        }
    }

    fun installed(context: Context): String? = packages.firstOrNull { InstalledApps.isInstalled(context.packageManager, it) }

    fun open(context: Context, item: MediaItem): Boolean {
        val pkg = installed(context) ?: return false
        if (startActivity != null) {
            val intent = Intent(Intent.ACTION_MAIN)
                .setComponent(ComponentName(pkg, startActivity))
                .putExtra("ItemId", item.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (AppLauncher.tryStart(context, intent)) return true
        }
        return AppResolver.launchIntent(context.packageManager, pkg)?.let { AppLauncher.tryStart(context, it) } ?: false
    }

    private fun authHeader(context: Context, token: String?): String =
        "MediaBrowser Client=\"$client\", Device=\"Fire TV\", DeviceId=\"${deviceId(context)}\", " +
            "Version=\"${appVersion(context)}\"" + (token?.let { ", Token=\"$it\"" } ?: "")

    private fun appVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun request(method: String, url: String, body: String? = null, auth: String? = null): Pair<Int, String?> =
        runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/json")
                if (auth != null) {
                    setRequestProperty("Authorization", auth)
                    setRequestProperty("X-Emby-Authorization", auth)
                    auth.substringAfter("Token=\"", "").substringBefore('"').takeIf { it.isNotEmpty() }
                        ?.let { setRequestProperty("X-Emby-Token", it) }
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                } else if (method == "POST") {
                    doOutput = true
                    setRequestProperty("Content-Length", "0")
                }
            }
            try {
                if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
                else if (method == "POST") c.outputStream.close()
                val code = c.responseCode
                val text = if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
                code to text
            } finally {
                c.disconnect()
            }
        }.getOrDefault(-1 to null)
}

fun remainingText(started: Boolean, minutes: Int): String? = when {
    !started -> null
    minutes >= 60 -> trf("noch {0} Std {1} Min", "{0} h {1} min left", minutes / 60, minutes % 60)
    minutes > 0 -> trf("noch {0} Min", "{0} min left", minutes)
    else -> null
}
