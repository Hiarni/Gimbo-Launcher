package dev.hiarni.gimbo.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Wetterdaten
data class Weather(
    val temperature: Int,
    val code: Int,
    val isDay: Boolean,
    val allDays: List<DayForecast>,
    val fetchedAt: Long,
    val id: String = "",
    val apparent: Int? = null,
    val humidity: Int? = null,
    val wind: Int? = null,
    val windUnit: String = "km/h",
    val allHours: List<HourForecast> = emptyList(),
    val utcOffsetMs: Long? = null,
) {
    fun minuteOfDay(now: Long = System.currentTimeMillis()): Int? =
        utcOffsetMs?.let { (((now + it) % 86_400_000L + 86_400_000L) % 86_400_000L / 60_000L).toInt() }

    val days: List<DayForecast> get() {
        val today = isoToday(utcOffsetMs)
        return allDays.filter { it.date.isEmpty() || it.date >= today }
            .map { d ->
                when {
                    d.date == today -> d.copy(label = tr("Heute", "Today"))
                    d.date.isNotEmpty() -> runCatching {
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date)!!
                        d.copy(label = SimpleDateFormat("EE", I18n.locale).format(date).trimEnd('.'))
                    }.getOrDefault(d)
                    else -> d
                }
            }
    }

    val hours: List<HourForecast> get() {
        val now = System.currentTimeMillis()
        return allHours.filter { it.time == 0L || it.time + 3_600_000L > now }
            .mapIndexed { i, h -> if (i == 0) h.copy(label = tr("Jetzt", "Now")) else h }
    }

    val windText: String? get() = wind?.let { "$it " + if (windUnit == "mph") tr("mph", "mph") else tr("km/h", "km/h") }

    val sunrise: String? get() = today?.sunrise
    val sunset: String? get() = today?.sunset

    val kind: WeatherKind get() = WeatherKind.fromWmo(code)
    val description: String get() = WeatherKind.describe(code, isDay)
    val today: DayForecast? get() = days.firstOrNull()

    val rainFrom: HourForecast? get() =
        if (kind.wet) null else hours.drop(1).firstOrNull { it.wet }

    val dryFrom: HourForecast? get() =
        if (!kind.wet) null else hours.drop(1).firstOrNull { !it.wet }
}

data class DayForecast(
    val label: String,
    val code: Int,
    val max: Int,
    val min: Int,
    val rainChance: Int? = null,
    val date: String = "",
    val sunrise: String? = null,
    val sunset: String? = null,
) {
    val kind: WeatherKind get() = WeatherKind.fromWmo(code)
    val description: String get() = WeatherKind.describe(code, true)
}

data class HourForecast(
    val label: String,
    val code: Int,
    val temperature: Int,
    val rainChance: Int,
    val precipitation: Float,
    val isDay: Boolean,
    val time: Long = 0L,
) {
    val kind: WeatherKind get() = WeatherKind.fromWmo(code)
    val wet: Boolean get() = rainChance >= 50 || precipitation >= 0.3f
}

private fun isoToday(utcOffsetMs: Long?): String {
    if (utcOffsetMs != null) {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(Date(System.currentTimeMillis() + utcOffsetMs))
    }
    val c = java.util.Calendar.getInstance()
    return "%04d-%02d-%02d".format(Locale.US, c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
}

// Wetterlage
enum class WeatherKind {
    Clear, PartlyCloudy, Cloudy, Fog, Rain, Snow, Thunder;

    val wet: Boolean get() = this == Rain || this == Snow || this == Thunder

    companion object {
        fun describe(code: Int, isDay: Boolean): String = when (code) {
            0 -> if (isDay) tr("Sonnig", "Sunny") else tr("Klar", "Clear")
            1 -> if (isDay) tr("Überwiegend sonnig", "Mostly sunny") else tr("Überwiegend klar", "Mostly clear")
            2 -> tr("Leicht bewölkt", "Partly cloudy")
            3 -> tr("Bewölkt", "Cloudy")
            45, 48 -> tr("Nebel", "Fog")
            51, 53, 55 -> tr("Nieselregen", "Drizzle")
            56, 57, 66, 67 -> tr("Eisregen", "Freezing rain")
            61, 63 -> tr("Regen", "Rain")
            65 -> tr("Starker Regen", "Heavy rain")
            71, 73, 75, 77 -> tr("Schnee", "Snow")
            80, 81, 82 -> tr("Regenschauer", "Rain showers")
            85, 86 -> tr("Schneeschauer", "Snow showers")
            in 95..99 -> tr("Gewitter", "Thunderstorm")
            else -> tr("Bewölkt", "Cloudy")
        }

        fun fromWmo(code: Int): WeatherKind = when (code) {
            0 -> Clear
            1, 2 -> PartlyCloudy
            3 -> Cloudy
            45, 48 -> Fog
            in 51..67, in 80..82 -> Rain
            in 71..77, 85, 86 -> Snow
            in 95..99 -> Thunder
            else -> Cloudy
        }
    }
}

// Ort und Abruf
data class WeatherPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val region: String = "",
) {
    companion object {
        val Default = WeatherPlace("Hartkirchen", 48.365, 14.003, "Oberösterreich, Österreich")
    }
}

object WeatherRepository {
    private const val PREFS = "weather"
    private const val KEY_JSON = "json"
    private const val KEY_TIME = "time"
    private const val KEY_ID = "id"
    const val REFRESH_MS = 30 * 60 * 1000L

    fun idOf(place: WeatherPlace, fahrenheit: Boolean) = "${place.lat},${place.lon},$fahrenheit"

    private fun url(place: WeatherPlace, fahrenheit: Boolean) = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=${place.lat}&longitude=${place.lon}" +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
        "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
        "&forecast_days=7&forecast_hours=13&timezone=auto" +
        (if (fahrenheit) "&temperature_unit=fahrenheit&wind_speed_unit=mph" else "")

    fun cached(context: Context, place: WeatherPlace, fahrenheit: Boolean): Weather? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = idOf(place, fahrenheit)
        if (prefs.getString(KEY_ID, null) != id) return null
        val json = prefs.getString(KEY_JSON, null) ?: return null
        val time = prefs.getLong(KEY_TIME, 0L)
        if (System.currentTimeMillis() - time > 6 * 60 * 60 * 1000L) return null
        return runCatching { parse(json, time, id) }.getOrNull()
    }

    suspend fun fetch(context: Context, place: WeatherPlace, fahrenheit: Boolean): Weather? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(url(place, fahrenheit)) ?: return@runCatching null
            val now = System.currentTimeMillis()
            val id = idOf(place, fahrenheit)
            val weather = parse(body, now, id)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_JSON, body)
                .putLong(KEY_TIME, now)
                .putString(KEY_ID, id)
                .apply()
            weather
        }.getOrNull()
    }

    suspend fun search(query: String): List<WeatherPlace>? = withContext(Dispatchers.IO) {
        runCatching {
            val q = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=10&language=${I18n.locale.language}&format=json")
                ?: return@runCatching null
            val results = JSONObject(body).optJSONArray("results") ?: return@runCatching emptyList()
            (0 until results.length()).map { i ->
                val o = results.getJSONObject(i)
                WeatherPlace(
                    name = o.getString("name"),
                    lat = o.getDouble("latitude"),
                    lon = o.getDouble("longitude"),
                    region = listOf(o.optString("admin1"), o.optString("country"))
                        .filter { it.isNotBlank() }
                        .joinToString(", "),
                )
            }
        }.getOrNull()
    }

    private fun get(url: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != 200) null
            else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String, time: Long, id: String): Weather {
        val root = JSONObject(body)
        val current = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val rainMax = daily.optJSONArray("precipitation_probability_max")
        val sunrises = daily.optJSONArray("sunrise")
        val sunsets = daily.optJSONArray("sunset")

        val locale = I18n.locale
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EE", locale)

        fun clock(arr: org.json.JSONArray?, i: Int): String? =
            arr?.optString(i)?.substringAfter('T', "")?.take(5)?.takeIf { it.length == 5 }
        val days = (0 until dates.length()).mapNotNull { i ->
            val iso = dates.getString(i)
            if (maxs.optDouble(i).isNaN() || mins.optDouble(i).isNaN()) return@mapNotNull null
            DayForecast(
                label = runCatching { dayFormat.format(isoFormat.parse(iso)!!).trimEnd('.') }.getOrDefault(iso),
                code = codes.optInt(i, 3),
                max = maxs.getDouble(i).roundToInt(),
                min = mins.getDouble(i).roundToInt(),
                rainChance = rainMax?.let { if (it.isNull(i)) null else it.optInt(i) },
                date = iso,
                sunrise = clock(sunrises, i),
                sunset = clock(sunsets, i),
            )
        }
        val hasOffset = root.has("utc_offset_seconds")
        val offsetMs = root.optInt("utc_offset_seconds", 0) * 1000L
        val hourFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

        val hours = root.optJSONObject("hourly")?.let { h ->
            val times = h.optJSONArray("time") ?: return@let emptyList()
            val temps = h.optJSONArray("temperature_2m")
            val hCodes = h.optJSONArray("weather_code")
            val chances = h.optJSONArray("precipitation_probability")
            val amounts = h.optJSONArray("precipitation")
            val dayFlags = h.optJSONArray("is_day")
            (0 until times.length()).mapNotNull { i ->
                val t = temps?.optDouble(i) ?: return@mapNotNull null
                if (t.isNaN()) return@mapNotNull null
                val stamp = times.getString(i)
                HourForecast(
                    label = stamp.substringAfter('T').take(2).trimStart('0').ifEmpty { "0" },
                    time = runCatching { hourFormat.parse(stamp)!!.time - offsetMs }.getOrDefault(0L),
                    code = hCodes?.optInt(i, 3) ?: 3,
                    temperature = t.roundToInt(),
                    rainChance = chances?.optInt(i, 0) ?: 0,
                    precipitation = (amounts?.optDouble(i, 0.0) ?: 0.0).toFloat(),
                    isDay = (dayFlags?.optInt(i, 1) ?: 1) == 1,
                )
            }
        } ?: emptyList()

        return Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            allDays = days,
            fetchedAt = time,
            id = id,
            apparent = current.optDouble("apparent_temperature").takeIf { !it.isNaN() }?.roundToInt(),
            humidity = current.optDouble("relative_humidity_2m").takeIf { !it.isNaN() }?.roundToInt(),
            wind = current.optDouble("wind_speed_10m").takeIf { !it.isNaN() }?.roundToInt(),
            windUnit = if (root.optJSONObject("current_units")?.optString("wind_speed_10m")?.contains("mp") == true) "mph" else "km/h",
            allHours = hours,
            utcOffsetMs = if (hasOffset) offsetMs else null,
        )
    }
}
