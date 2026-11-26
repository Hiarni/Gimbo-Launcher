package dev.hiarni.gimbo.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Quellen für Weiterschauen
enum class Source(val title: String) {
    Jellyfin("Jellyfin"),
    Plex("Plex"),
    Emby("Emby"),
    Kodi("Kodi"),
    Recent("Recent");

    val label: String get() = if (this == Recent) tr("Zuletzt genutzt", "Recently used") else title

    // Reihen-Überschrift und daneben klein die Quelle
    val heading: String
        get() = if (this == Recent) tr("Zuletzt genutzt", "Recently used") else tr("Weiterschauen", "Continue watching")
    val tag: String? get() = if (this == Recent) null else title

    companion object {
        val DefaultOrder = listOf(Jellyfin, Plex, Emby, Kodi, Recent)
    }
}

data class MediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val progress: Float,
    val thumbUrl: String?,
    val backdropUrl: String?,
    val source: Source = Source.Jellyfin,
    val ref: String = id,
) {
    val key: String get() = "${source.name}:$id"
}

data class MediaRow(val source: Source, val items: List<MediaItem>)

// Bilder der Einträge
object MediaImages {
    private val headers = HashMap<String, String>()

    private val cache = object : LruCache<String, Bitmap>(
        minOf(32L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 5).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun authorize(prefix: String, header: String?) = synchronized(headers) {
        if (header == null) headers.remove(prefix) else headers[prefix] = header
    }

    fun peek(url: String): Bitmap? = cache.get(url)

    fun peekBackdrop(url: String): Bitmap? = cache.get("soft:$url")

    fun put(url: String, bitmap: Bitmap) {
        cache.put(url, bitmap)
    }

    suspend fun image(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: load(context, url)?.also {
            it.prepareToDraw()
            cache.put(url, it)
        }
    }

    suspend fun backdrop(context: Context, url: String): Bitmap? {
        val key = "soft:$url"
        cache.get(key)?.let { return it }
        val sharp = cache.get(url) ?: withContext(Dispatchers.IO) { load(context, url, sample = 2) } ?: return null
        return withContext(Dispatchers.Default) {
            runCatching { TileImages.soften(sharp, width = 960, radius = 3, passes = 2) }.getOrDefault(sharp)
        }.also {
            it.prepareToDraw()
            cache.put(key, it)
        }
    }

    private fun load(context: Context, url: String, sample: Int = 1): Bitmap? {
        if (url.startsWith(RecentApps.SCHEME)) return RecentApps.banner(context, url.removePrefix(RecentApps.SCHEME))
        return runCatching {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 10_000
                synchronized(headers) { headers.entries.firstOrNull { url.startsWith(it.key) }?.value }
                    ?.let { setRequestProperty("Authorization", it) }
            }
            try {
                if (c.responseCode != 200) {
                    null
                } else {
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    c.inputStream.use { BitmapFactory.decodeStream(it, null, opts) }
                }
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }
}

// Reihen: Laden, Zwischenspeicher, Öffnen
object Shelves {
    private const val PREFS = "shelves"
    private const val LIMIT = 10

    var items by mutableStateOf<Map<Source, List<MediaItem>>>(emptyMap())
        private set

    var revision by mutableIntStateOf(0)
        private set

    private var restored = false

    fun restore(context: Context) {
        if (restored) return
        restored = true
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        items = Source.entries.associateWith { s ->
            if (!connected(context, s)) emptyList() else runCatching { decode(s, p.getString(s.name, null)) }.getOrDefault(emptyList())
        }.filterValues { it.isNotEmpty() }
    }

    fun connected(context: Context, source: Source): Boolean = when (source) {
        Source.Jellyfin -> Jellyfin.account(context) != null
        Source.Emby -> Emby.account(context) != null
        Source.Plex -> Plex.account(context) != null
        Source.Kodi -> Kodi.account(context) != null
        Source.Recent -> RecentApps.granted(context)
    }

    fun serverName(context: Context, source: Source): String? = when (source) {
        Source.Jellyfin -> Jellyfin.account(context)?.let { it.serverName.ifEmpty { it.server } }
        Source.Emby -> Emby.account(context)?.let { it.serverName.ifEmpty { it.server } }
        Source.Plex -> Plex.account(context)?.serverName
        Source.Kodi -> Kodi.account(context)?.let { "${it.host}:${it.port}" }
        Source.Recent -> null
    }

    fun rows(context: Context, config: LauncherConfig, all: Map<Source, List<MediaItem>> = items): List<MediaRow> =
        config.rowOrder.filter { config.rowOn(it) && connected(context, it) }
            .mapNotNull { s -> all[s]?.takeIf { it.isNotEmpty() }?.let { MediaRow(s, it) } }

    fun changed() {
        revision++
    }

    fun signOut(context: Context, source: Source) {
        when (source) {
            Source.Jellyfin -> Jellyfin.signOut(context)
            Source.Emby -> Emby.signOut(context)
            Source.Plex -> Plex.signOut(context)
            Source.Kodi -> Kodi.signOut(context)
            Source.Recent -> Unit
        }
        set(context, source, emptyList())
        changed()
    }

    suspend fun refresh(context: Context, config: LauncherConfig) = coroutineScope {
        val app = context.applicationContext
        val wanted = Source.entries.filter { config.rowOn(it) && connected(app, it) }
        wanted.map { s ->
            async {
                val fresh = withTimeoutOrNull(25_000) { runCatching { fetch(app, s) }.getOrNull() }
                if (fresh != null) set(app, s, fresh)
                if (!connected(app, s)) {
                    set(app, s, emptyList())
                    changed()
                }
            }
        }.awaitAll()
    }

    private suspend fun fetch(context: Context, source: Source): List<MediaItem>? = when (source) {
        Source.Jellyfin -> Jellyfin.fetchItems(context, LIMIT)
        Source.Emby -> Emby.fetchItems(context, LIMIT)
        Source.Plex -> Plex.fetchItems(context, LIMIT)
        Source.Kodi -> Kodi.fetchItems(context, LIMIT)
        Source.Recent -> RecentApps.fetchItems(context, LIMIT)
    }

    @Synchronized
    private fun set(context: Context, source: Source, list: List<MediaItem>) {
        if (items[source] == list) return
        items = items.toMutableMap().apply { if (list.isEmpty()) remove(source) else put(source, list) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (list.isEmpty()) remove(source.name) else putString(source.name, encode(list))
        }.apply()
    }

    fun open(context: Context, item: MediaItem): Boolean = when (item.source) {
        Source.Jellyfin -> Jellyfin.open(context, item)
        Source.Emby -> Emby.open(context, item)
        Source.Plex -> Plex.open(context, item)
        Source.Kodi -> Kodi.open(context, item)
        Source.Recent -> RecentApps.open(context, item)
    }

    private fun encode(list: List<MediaItem>): String = JSONArray().apply {
        list.forEach { i ->
            put(
                JSONObject().put("id", i.id).put("title", i.title).put("subtitle", i.subtitle).put("progress", i.progress.toDouble())
                    .put("thumb", i.thumbUrl ?: "").put("backdrop", i.backdropUrl ?: "").put("ref", i.ref),
            )
        }
    }.toString()

    private fun decode(source: Source, json: String?): List<MediaItem> {
        if (json == null || source == Source.Recent) return emptyList()
        val a = JSONArray(json)
        return (0 until a.length()).map { n ->
            val o = a.getJSONObject(n)
            MediaItem(
                id = o.getString("id"),
                title = o.optString("title"),
                subtitle = o.optString("subtitle"),
                progress = o.optDouble("progress", 0.0).toFloat(),
                thumbUrl = o.optString("thumb").ifEmpty { null },
                backdropUrl = o.optString("backdrop").ifEmpty { null },
                source = source,
                ref = o.optString("ref").ifEmpty { o.getString("id") },
            )
        }
    }
}
