package dev.hiarni.gimbo.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Kacheln und Einstellungen
enum class TileStyle(private val de: String, private val en: String) {
    Gimbo("Gimbo", "Gimbo"),
    Banner("Banner", "Banner"),
    Icon("Icon", "Icon"),
    Glas("Glas", "Glass"),
    Image("Bild", "Image");

    val label: String get() = tr(de, en)
}

val TileScaleRange = 0.5f..1.5f
val HeaderScaleRange = 0.6f..1.4f
val ShelfScaleRange = 0.6f..1.4f

val TilePresets = listOf("XS" to 0.55f, "S" to 0.7f, "M" to 0.9f, "L" to 1.05f, "XL" to 1.25f, "XXL" to 1.45f)

enum class TileSize(private val de: String, private val en: String, val scale: Float) {
    Small("Klein", "Small", 0.7f),
    Medium("Mittel", "Medium", 0.9f),
    Large("Groß", "Large", 1.05f),
    XLarge("Sehr groß", "Extra large", 1.25f);

    val label: String get() = tr(de, en)
}

data class Tile(
    val id: String,
    val packageName: String,
    val label: String,
    val style: TileStyle,
    val color: Long,
    val showLabel: Boolean = false,
    val imagePath: String? = null,
)

enum class Backdrop(private val de: String, private val en: String, val a: Long = 0, val b: Long = 0) {
    Follow("Folgt der Kachel", "Follows tile"),
    Tageszeit("Tageszeit", "Time of day"),
    Nordlicht("Nordlicht", "Aurora", 0xFF13A3B5, 0xFF2E5BD8),
    Abendrot("Abendrot", "Afterglow", 0xFFD2458C, 0xFF6B3FD0),
    Glut("Glut", "Ember", 0xFFE8672C, 0xFFB3122E),
    Wald("Wald", "Forest", 0xFF2E9E63, 0xFF0E5B6B),
    Schlicht("Schlicht", "Plain"),
    Bild("Eigenes Bild", "Own image");

    val label: String get() = tr(de, en)
}

enum class PanelStyle(private val de: String, private val en: String) {
    Glas("Glas", "Glass"), Transparent("Transparent", "Transparent"), Aus("Aus", "Off");

    val label: String get() = tr(de, en)
}

enum class ClockStyle(private val de: String, private val en: String) {
    Klassisch("Klassisch", "Classic"), Kontrast("Kontrast", "Contrast"), Gestapelt("Gestapelt", "Stacked"), Analog("Analog", "Analog"),
    Editorial("Editorial", "Editorial"), Wortuhr("Wortuhr", "Word clock"), Neon("Neon", "Neon"), Kontur("Kontur", "Outline"), Kompakt("Kompakt", "Compact"),
    Sonnenbogen("Sonnenbogen", "Sun arc"), Klappanzeige("Klappanzeige", "Flip display"), Verlauf("Verlauf", "Gradient"), Zeile("Zeile", "Line"),
    Ring("Ring", "Ring"), Kalenderblatt("Kalenderblatt", "Calendar page");

    val label: String get() = tr(de, en)
}

enum class WeatherStyle(private val de: String, private val en: String, private val hintDe: String, private val hintEn: String) {
    Dezent("Dezent", "Subtle", "Jetzt + 4 Tage", "Now + 4 days"), GlasKarte("Glas-Karte", "Glass card", "Details + 3 Tage", "Details + 3 days"),
    Kennzahlen("Kennzahlen", "Key figures", "Wind, Feuchte, Regen, Stunden", "Wind, humidity, rain, hours"), Stundenkurve("Stundenkurve", "Hourly curve", "nächste 8 Stunden", "next 8 hours"),
    Chips("Chips", "Chips", "Details als Kärtchen", "Details as cards"), Gross("Groß", "Large", "großes Symbol", "large icon"), Woche("Woche", "Week", "7 Tage", "7 days"),
    Regenradar("Regen", "Rain", "Niederschlag der nächsten Stunden", "Rainfall over the next hours"), Satz("Satz", "Sentence", "in Worten", "in words"), Minimal("Minimal", "Minimal", "nur das Nötigste", "just the essentials"),
    FuenfTage("5 Tage Leiste", "5-day bar", "Jetzt + 5 Tage", "Now + 5 days"), Tageskarten("Tageskarten", "Day cards", "5 Tage mit Regen", "5 days with rain"),
    Temperaturband("Temperaturband", "Temperature band", "5 Tage als Verlauf", "5 days as a gradient"), JetztListe("Jetzt + Liste", "Now + list", "5 Tage als Liste", "5 days as a list"),
    ZeileFuenf("Zeile 5 Tage", "5-day line", "eine Zeile", "one line"), HeuteMorgen("Heute & Morgen", "Today & tomorrow", "+ 3 Tage", "+ 3 days"),
    Farbverlauf("Farbverlauf", "Color gradient", "5 Tage nach Temperatur gefärbt", "5 days colored by temperature");

    val label: String get() = tr(de, en)
    val hint: String get() = tr(hintDe, hintEn)
}

data class LauncherConfig(
    val tiles: List<Tile>,
    val tileScale: Float = 0.9f,
    val clockScale: Float = 1f,
    val weatherScale: Float = 1f,
    val shelfScale: Float = 1f,
    val backdrop: Backdrop = Backdrop.Follow,
    val backdropImage: String? = null,
    val backdropBlur: Boolean = true,
    val weatherEnabled: Boolean = true,
    val weatherPlace: WeatherPlace = WeatherPlace.Default,
    val fahrenheit: Boolean = false,
    val sounds: Boolean = true,
    val ambientMinutes: Int = 10,
    val rowOrder: List<Source> = Source.DefaultOrder,
    val rowOff: Set<Source> = emptySet(),
    val jellyfinBackdrop: Boolean = true,
    val appOrder: List<String> = emptyList(),
    val hiddenApps: List<String> = emptyList(),
    val clockStyle: ClockStyle = ClockStyle.Klassisch,
    val weatherStyle: WeatherStyle = WeatherStyle.Dezent,
    val colorIcons: Boolean = true,
    val panelStyle: PanelStyle = PanelStyle.Glas,
    val language: AppLanguage = AppLanguage.System,
    val autoUpdate: Boolean = true,
    val homeToFirst: Boolean = false,
) {
    fun rowOn(source: Source): Boolean = source !in rowOff

    fun withRowOn(source: Source, on: Boolean) = copy(rowOff = if (on) rowOff - source else rowOff + source)

    fun moveRow(source: Source, delta: Int): LauncherConfig {
        val list = rowOrder.toMutableList()
        val from = list.indexOf(source)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return this
        list.add(to, list.removeAt(from))
        return copy(rowOrder = list)
    }
}

// Farben und mitgelieferte Apps
val TileColors = listOf(
    0xFF8E4FD6, 0xFF3D6FE8, 0xFF13A3B5, 0xFF2E9E63, 0xFFE0A21B,
    0xFFE8672C, 0xFFE5443A, 0xFFD2458C, 0xFF596079,
)

object BuiltIn {
    val jellyfin = listOf("org.jellyfin.androidtv", "org.jellyfin.androidtv.debug")
    val smarttube = listOf(
        "org.smarttube.stable",
        "org.smarttube.beta",
        "com.teamsmart.videomanager.tv",
        "com.liskovsoft.smarttubetv.beta",
        "com.liskovsoft.smarttubetv",
    )

    fun artKey(pkg: String): String? = when (pkg) {
        in jellyfin -> "jellyfin"
        in smarttube -> "smarttube"
        else -> null
    }

    fun accent(key: String): Pair<Long, Long> = when (key) {
        "jellyfin" -> 0xFF8E4FD6 to 0xFF0A8FD8
        else -> 0xFFF0453A to 0xFF9E1035
    }
}

// Speichern und Laden
object TileStore {
    private const val PREFS = "gimbo"
    private const val KEY_CONFIG = "launcher_config"

    fun load(context: Context): LauncherConfig {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CONFIG, null)
            ?: return defaults(context).also { I18n.apply(it.language) }
        return runCatching { parse(json) }.getOrElse { defaults(context) }.also { I18n.apply(it.language) }
    }

    fun save(context: Context, config: LauncherConfig) {
        I18n.apply(config.language)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_CONFIG, toJson(config))
            .apply()
    }

    fun toJson(config: LauncherConfig): String {
        val tiles = JSONArray()
        config.tiles.forEach { t ->
            tiles.put(
                JSONObject()
                    .put("id", t.id)
                    .put("pkg", t.packageName)
                    .put("label", t.label)
                    .put("style", t.style.name)
                    .put("color", t.color)
                    .put("showLabel", t.showLabel)
                    .put("image", t.imagePath ?: ""),
            )
        }
        val root = JSONObject()
            .put("tileScale", config.tileScale.toDouble())
            .put("clockScale", config.clockScale.toDouble())
            .put("weatherScale", config.weatherScale.toDouble())
            .put("shelfScale", config.shelfScale.toDouble())
            .put("backdrop", config.backdrop.name)
            .put("backdropImage", config.backdropImage ?: "")
            .put("backdropBlur", config.backdropBlur)
            .put("weatherEnabled", config.weatherEnabled)
            .put("fahrenheit", config.fahrenheit)
            .put(
                "weatherPlace",
                JSONObject()
                    .put("name", config.weatherPlace.name)
                    .put("lat", config.weatherPlace.lat)
                    .put("lon", config.weatherPlace.lon)
                    .put("region", config.weatherPlace.region),
            )
            .put("sounds", config.sounds)
            .put("ambientMinutes", config.ambientMinutes)
            .put("jellyfinEnabled", config.rowOn(Source.Jellyfin))
            .put("rowOrder", JSONArray(config.rowOrder.map { it.name }))
            .put("rowOff", JSONArray(config.rowOff.map { it.name }))
            .put("jellyfinBackdrop", config.jellyfinBackdrop)
            .put("appOrder", JSONArray(config.appOrder))
            .put("hiddenApps", JSONArray(config.hiddenApps))
            .put("clockStyle", config.clockStyle.name)
            .put("weatherStyle", config.weatherStyle.name)
            .put("colorIcons", config.colorIcons)
            .put("panelStyle", config.panelStyle.name)
            .put("language", config.language.name)
            .put("autoUpdate", config.autoUpdate)
            .put("homeToFirst", config.homeToFirst)
            .put("tiles", tiles)
        return root.toString()
    }

    fun fromJson(json: String): LauncherConfig = parse(json)

    private fun scale(root: JSONObject, key: String, range: ClosedFloatingPointRange<Float>): Float {
        val value = root.optDouble(key).takeIf { !it.isNaN() }
            ?: root.optDouble("headerScale").takeIf { !it.isNaN() && key != "shelfScale" }
            ?: 1.0
        return value.toFloat().coerceIn(range.start, range.endInclusive)
    }

    private fun parse(json: String): LauncherConfig {
        val root = JSONObject(json)
        val arr = root.getJSONArray("tiles")
        val tiles = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val pkg = o.optString("pkg").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Tile(
                id = id,
                packageName = pkg,
                label = o.optString("label").ifBlank { pkg },
                style = runCatching { TileStyle.valueOf(o.getString("style")) }.getOrDefault(TileStyle.Icon),
                color = o.optLong("color", TileColors.first()),
                showLabel = o.optBoolean("showLabel", false),
                imagePath = o.optString("image").takeIf { it.isNotBlank() },
            )
        }.distinctBy { it.id }
        val oldSize = runCatching { TileSize.valueOf(root.getString("size")) }.getOrNull()
        val tileScale = root.optDouble("tileScale").takeIf { !it.isNaN() }?.toFloat()
            ?: oldSize?.scale ?: 0.9f
        val backdrop = runCatching { Backdrop.valueOf(root.getString("backdrop")) }.getOrDefault(Backdrop.Follow)
        return LauncherConfig(
            tiles = tiles,
            tileScale = tileScale.coerceIn(TileScaleRange.start, TileScaleRange.endInclusive),
            clockScale = scale(root, "clockScale", HeaderScaleRange),
            weatherScale = scale(root, "weatherScale", HeaderScaleRange),
            shelfScale = scale(root, "shelfScale", ShelfScaleRange),
            backdrop = backdrop,
            backdropImage = root.optString("backdropImage").takeIf { it.isNotBlank() },
            backdropBlur = root.optBoolean("backdropBlur", true),
            weatherEnabled = root.optBoolean("weatherEnabled", true),
            fahrenheit = root.optBoolean("fahrenheit", false),
            sounds = root.optBoolean("sounds", true),
            ambientMinutes = root.optInt("ambientMinutes", 10),
            rowOrder = root.optJSONArray("rowOrder")?.let { a ->
                val known = (0 until a.length()).mapNotNull { runCatching { Source.valueOf(a.getString(it)) }.getOrNull() }.distinct()
                known + Source.DefaultOrder.filter { it !in known }
            } ?: Source.DefaultOrder,
            rowOff = root.optJSONArray("rowOff")?.let { a ->
                (0 until a.length()).mapNotNull { runCatching { Source.valueOf(a.getString(it)) }.getOrNull() }.toSet()
            } ?: if (root.optBoolean("jellyfinEnabled", true)) emptySet() else setOf(Source.Jellyfin),
            jellyfinBackdrop = root.optBoolean("jellyfinBackdrop", true),
            clockStyle = runCatching { ClockStyle.valueOf(root.getString("clockStyle")) }.getOrDefault(ClockStyle.Klassisch),
            weatherStyle = runCatching { WeatherStyle.valueOf(root.getString("weatherStyle")) }.getOrDefault(WeatherStyle.Dezent),
            colorIcons = root.optBoolean("colorIcons", true),
            language = runCatching { AppLanguage.valueOf(root.getString("language")) }.getOrDefault(AppLanguage.System),
            autoUpdate = root.optBoolean("autoUpdate", true),
            homeToFirst = root.optBoolean("homeToFirst", false),
            panelStyle = runCatching { PanelStyle.valueOf(root.getString("panelStyle")) }.getOrElse {
                if (root.optBoolean("panels", true)) PanelStyle.Glas else PanelStyle.Aus
            },
            hiddenApps = root.optJSONArray("hiddenApps")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }.distinct()
            } ?: emptyList(),
            appOrder = root.optJSONArray("appOrder")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }.distinct()
            } ?: emptyList(),
            weatherPlace = root.optJSONObject("weatherPlace")?.let {
                runCatching { WeatherPlace(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"), it.optString("region")) }.getOrNull()
            } ?: WeatherPlace.Default,
        )
    }

    private fun defaults(context: Context): LauncherConfig {
        val pm = context.packageManager
        fun pick(list: List<String>) = list.firstOrNull { AppResolver.launchIntent(pm, it) != null } ?: list.first()
        return LauncherConfig(
            tiles = listOf(
                Tile("jellyfin", pick(BuiltIn.jellyfin), "Jellyfin", TileStyle.Gimbo, 0xFF8E4FD6),
                Tile("smarttube", pick(BuiltIn.smarttube), "SmartTube", TileStyle.Gimbo, 0xFFE5443A),
            ),
        )
    }

    fun newTile(context: Context, app: InstalledApp): Tile {
        val pm = context.packageManager
        val style = when {
            BuiltIn.artKey(app.packageName) != null -> TileStyle.Gimbo
            InstalledApps.banner(pm, app.packageName) != null -> TileStyle.Banner
            else -> TileStyle.Icon
        }
        val color = InstalledApps.icon(pm, app.packageName)?.let(InstalledApps::guessColor) ?: TileColors.first()
        return Tile(UUID.randomUUID().toString(), app.packageName, app.label, style, color)
    }

    fun fitStyle(context: Context, current: TileStyle, pkg: String): TileStyle = when (current) {
        TileStyle.Gimbo -> if (BuiltIn.artKey(pkg) != null) TileStyle.Gimbo
        else if (InstalledApps.banner(context.packageManager, pkg) != null) TileStyle.Banner
        else TileStyle.Icon
        else -> current
    }

    fun availableStyles(context: Context, pkg: String): List<TileStyle> = buildList {
        if (BuiltIn.artKey(pkg) != null) add(TileStyle.Gimbo)
        if (InstalledApps.banner(context.packageManager, pkg) != null) add(TileStyle.Banner)
        add(TileStyle.Icon)
        add(TileStyle.Glas)
        add(TileStyle.Image)
    }
}

// Installierte Apps und Bilder
data class InstalledApp(val packageName: String, val label: String)

object ImageCache {
    private val cache = object : LruCache<String, Bitmap>(
        minOf(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10).toInt(),
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    val generation = androidx.compose.runtime.mutableIntStateOf(0)

    fun peek(key: String): Bitmap? = cache.get(key)

    inline fun get(key: String, load: () -> Bitmap?): Bitmap? {
        peek(key)?.let { return it }
        if (isMissing(key)) return null
        val bitmap = load()
        put(key, bitmap)
        return bitmap
    }

    fun isMissing(key: String) = key in missing

    fun put(key: String, bitmap: Bitmap?) {
        if (bitmap == null) {
            missing += key
        } else {
            bitmap.prepareToDraw()
            cache.put(key, bitmap)
        }
    }

    fun retryMissing() = missing.clear()

    fun forgetPackage(pkg: String) {
        cache.remove("icon:$pkg")
        cache.remove("banner:$pkg")
        missing.remove("icon:$pkg")
        missing.remove("banner:$pkg")
        generation.intValue++
    }
}

object InstalledApps {

    fun list(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, InstalledApp>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(query, 0).forEach { info ->
                val pkg = info.activityInfo.packageName
                if (pkg != context.packageName && pkg !in seen) {
                    seen[pkg] = InstalledApp(pkg, info.loadLabel(pm).toString())
                }
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun ordered(apps: List<InstalledApp>, order: List<String>): List<InstalledApp> {
        if (order.isEmpty()) return apps
        val rank = HashMap<String, Int>(order.size)
        order.forEachIndexed { i, pkg -> if (pkg !in rank) rank[pkg] = i }
        val (known, rest) = apps.partition { it.packageName in rank }
        return known.sortedBy { rank.getValue(it.packageName) } + rest
    }

    fun isInstalled(pm: PackageManager, pkg: String): Boolean = AppResolver.launchIntent(pm, pkg) != null

    fun label(pm: PackageManager, pkg: String): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    fun iconKey(pkg: String) = "icon:$pkg"
    fun bannerKey(pkg: String) = "banner:$pkg"

    fun icon(pm: PackageManager, pkg: String): Bitmap? = ImageCache.get(iconKey(pkg)) {
        try {
            pm.getApplicationIcon(pkg).toFitBitmap(192, 192, fill = false)
        } catch (_: Exception) {
            null
        }
    }

    fun banner(pm: PackageManager, pkg: String): Bitmap? = ImageCache.get(bannerKey(pkg)) {
        try {
            val drawable = pm.getApplicationBanner(pkg)
                ?: AppResolver.launchIntent(pm, pkg)?.let { pm.getActivityBanner(it) }
            drawable?.toFitBitmap(640, 360, fill = true)
        } catch (_: Exception) {
            null
        }
    }

    // Ohne Verzerren zeichnen. fill = false: mittig in w × h einpassen, Rest transparent (Icons).
    // fill = true: eigenes Seitenverhältnis behalten, höchstens w × h groß; die Kachel schneidet beim Anzeigen zu (Banner).
    private fun Drawable.toFitBitmap(w: Int, h: Int, fill: Boolean): Bitmap {
        val iw = intrinsicWidth
        val ih = intrinsicHeight
        if (iw <= 0 || ih <= 0 || iw.toLong() * h == ih.toLong() * w) return toBitmap(w, h)
        val scale = min(w.toFloat() / iw, h.toFloat() / ih)
        val dw = (iw * scale).roundToInt().coerceIn(1, w)
        val dh = (ih * scale).roundToInt().coerceIn(1, h)
        if (fill) return toBitmap(dw, dh)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val old = copyBounds()
        setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
        draw(Canvas(out))
        bounds = old
        return out
    }

    fun guessColor(icon: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(icon, 24, 24, true)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        val hsv = FloatArray(3)
        for (y in 0 until 24) for (x in 0 until 24) {
            val c = small.getPixel(x, y)
            if (Color.alpha(c) < 200) continue
            Color.colorToHSV(c, hsv)
            if (hsv[1] < 0.35f || hsv[2] < 0.25f) continue
            r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        if (n == 0L) return TileColors.last()
        val ar = r / n
        val ag = g / n
        val ab = b / n
        return TileColors.minBy { c ->
            val dr = ((c shr 16) and 0xFFL) - ar
            val dg = ((c shr 8) and 0xFFL) - ag
            val db = (c and 0xFFL) - ab
            dr * dr + dg * dg + db * db
        }
    }
}

object TileImages {

    fun recent(context: Context, limit: Int = 60): List<Uri> {
        val result = ArrayList<Uri>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        runCatching {
            context.contentResolver.query(
                base,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && result.size < limit) {
                    result += Uri.withAppendedPath(base, c.getLong(idCol).toString())
                }
            }
        }
        return result
    }

    fun thumbnail(context: Context, uri: Uri, width: Int = 320): Bitmap? = decodeSampled(context, uri, width)

    fun import(context: Context, uri: Uri, tileId: String, width: Int = 960, height: Int = 540): String? {
        val src = decodeSampled(context, uri, width + width / 3) ?: return null
        val targetRatio = 16f / 9f
        val srcRatio = src.width.toFloat() / src.height
        val crop = if (srcRatio > targetRatio) {
            val w = (src.height * targetRatio).toInt()
            Rect((src.width - w) / 2, 0, (src.width + w) / 2, src.height)
        } else {
            val h = (src.width / targetRatio).toInt()
            Rect(0, (src.height - h) / 2, src.width, (src.height + h) / 2)
        }
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, crop, Rect(0, 0, width, height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val dir = File(context.filesDir, "tiles").apply { mkdirs() }
        dir.listFiles { f -> f.name.startsWith("$tileId-") }?.forEach { it.delete() }
        val file = File(dir, "$tileId-${System.currentTimeMillis()}.jpg")
        return runCatching {
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            file.absolutePath
        }.getOrNull()
    }

    fun key(path: String) = "file:$path"

    fun decode(path: String, sample: Int = 1): Bitmap? = runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    fun load(path: String): Bitmap? = ImageCache.get(key(path)) {
        runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
    }

    fun soften(src: Bitmap, width: Int = 480, radius: Int = 9, passes: Int = 3): Bitmap {
        val w = width
        val h = max(1, (src.height * w / src.width.toFloat()).toInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(passes) { boxBlur(px, w, h, radius) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val c = px[row + i.coerceIn(0, w - 1)]
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = px[row + (x - r).coerceIn(0, w - 1)]
                val inn = px[row + (x + r + 1).coerceIn(0, w - 1)]
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
        for (x in 0 until w) {
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val c = tmp[i.coerceIn(0, h - 1) * w + x]
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF
            }
            for (y in 0 until h) {
                px[y * w + x] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rs += ((inn shr 16) and 0xFF) - ((out shr 16) and 0xFF)
                gs += ((inn shr 8) and 0xFF) - ((out shr 8) and 0xFF)
                bs += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }

    fun delete(path: String?) {
        if (path != null) runCatching { File(path).delete() }
    }

    private fun decodeSampled(context: Context, uri: Uri, width: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, 1) / (sample * 2) >= width) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
