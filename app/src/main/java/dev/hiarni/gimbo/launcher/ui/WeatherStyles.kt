package dev.hiarni.gimbo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.DayForecast
import dev.hiarni.gimbo.launcher.Weather
import dev.hiarni.gimbo.launcher.WeatherKind
import dev.hiarni.gimbo.launcher.WeatherStyle
import dev.hiarni.gimbo.launcher.I18n
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf

// Farben und Bausteine
private val Rain = Color(0xFF7CC4FF)
private val Warm = Color(0xFFFFD36B)
private val Cold = Color(0xFF5CC8FF)

private fun ws(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Palette.Text,
    family: FontFamily = Inter,
    spacing: TextUnit = 0.sp,
) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, letterSpacing = spacing, color = color, textAlign = TextAlign.Center)

@Composable
private fun T(text: String, style: TextStyle, modifier: Modifier = Modifier) =
    BasicText(text, style = style, modifier = modifier, maxLines = 2)

@Composable
private fun Icon(kind: WeatherKind, size: Dp, colored: Boolean, isDay: Boolean = true) =
    WeatherIcon(kind, isDay, Palette.Text, Modifier.size(size), colored)

private val Glass = RoundedCornerShape(20.dp)

@Composable
private fun Divider(height: Dp? = null) = Box(
    Modifier.width(1.dp).then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())
        .background(Color.White.copy(alpha = 0.09f)),
)

@Composable
private fun RangeBar(min: Int, max: Int, lo: Int, hi: Int, width: Dp = 46.dp) {
    Canvas(Modifier.size(width, 4.dp)) {
        val span = (hi - lo).coerceAtLeast(1).toFloat()
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = 0.1f), cornerRadius = r)
        val x1 = (min - lo) / span * size.width
        val x2 = ((max - lo) / span * size.width).coerceAtLeast(x1 + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(Cold, Warm), startX = 0f, endX = size.width),
            topLeft = Offset(x1, 0f), size = Size(x2 - x1, size.height), cornerRadius = r,
        )
    }
}

private fun List<DayForecast>.bounds(): Pair<Int, Int> =
    (minOfOrNull { it.min } ?: 0) to (maxOfOrNull { it.max } ?: 1)

private fun Weather.details(): String = buildList {
    apparent?.let { add(trf("Gefühlt {0}°", "Feels like {0}°", it)) }
    windText?.let { add(trf("Wind {0}", "Wind {0}", it)) }
}.joinToString(" · ")

private fun Weather.hiLo(): String = today?.let { "↑${it.max}° ↓${it.min}°" } ?: ""

private fun Weather.rainLine(): String = when {
    rainFrom != null -> trf("Regen ab {0} Uhr", "Rain from {0}:00", rainFrom!!.label)
    dryFrom != null -> trf("trocken ab {0} Uhr", "dry from {0}:00", dryFrom!!.label)
    else -> today?.rainChance?.takeIf { it >= 10 }?.let { trf("Regen {0} %", "Rain {0}%", it) } ?: ""
}

// Wetter-Anzeige
@Composable
fun WeatherMini(weather: Weather, colored: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(weather.kind, 22.dp, colored, weather.isDay)
        Spacer(Modifier.width(10.dp))
        T("${weather.temperature}°", ws(17.sp, FontWeight.Medium))
        Spacer(Modifier.width(10.dp))
        T(listOf(weather.description, weather.hiLo()).filter { it.isNotEmpty() }.joinToString(" · "), ws(17.sp, color = Palette.TextDim))
    }
}

@Composable
fun WeatherPanel(style: WeatherStyle, weather: Weather, colored: Boolean, place: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (style) {
            WeatherStyle.Dezent -> Dezent(weather, colored)
            WeatherStyle.GlasKarte -> GlasKarte(weather, colored)
            WeatherStyle.Kennzahlen -> Kennzahlen(weather, colored)
            WeatherStyle.Stundenkurve -> Stundenkurve(weather, colored)
            WeatherStyle.Chips -> Chips(weather, colored, place)
            WeatherStyle.Gross -> Gross(weather, colored)
            WeatherStyle.Woche -> Woche(weather, colored)
            WeatherStyle.Regenradar -> Regenradar(weather, colored)
            WeatherStyle.Satz -> Satz(weather, colored)
            WeatherStyle.Minimal -> Minimal(weather, colored, place)
            WeatherStyle.FuenfTage -> FuenfTage(weather, colored)
            WeatherStyle.Tageskarten -> Tageskarten(weather, colored)
            WeatherStyle.Temperaturband -> Temperaturband(weather, colored)
            WeatherStyle.JetztListe -> JetztListe(weather, colored)
            WeatherStyle.ZeileFuenf -> ZeileFuenf(weather, colored)
            WeatherStyle.HeuteMorgen -> HeuteMorgen(weather, colored)
            WeatherStyle.Farbverlauf -> Farbverlauf(weather, colored)
        }
    }
}

// Wetter-Stile
@Composable
private fun Dezent(w: Weather, colored: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 26.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(20.sp, FontWeight.Medium))
        Spacer(Modifier.width(10.dp))
        T(w.description, ws(19.sp, color = Palette.TextDim))
    }
    val days = w.days.take(4)
    if (days.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).surface(Color.White.copy(alpha = 0.045f)).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            days.forEachIndexed { i, d ->
                if (i > 0) Divider(22.dp)
                Row(Modifier.padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(d.label, ws(14.sp, FontWeight.Medium, Palette.TextDim))
                    Spacer(Modifier.width(9.dp))
                    Icon(d.kind, 20.dp, colored)
                    Spacer(Modifier.width(9.dp))
                    T("${d.max}°", ws(15.sp, FontWeight.Medium))
                    Spacer(Modifier.width(6.dp))
                    T("${d.min}°", ws(15.sp, color = Palette.TextFaint))
                }
            }
        }
    }
}

@Composable
private fun DayColumn(d: DayForecast, colored: Boolean, lo: Int, hi: Int, highlight: Boolean, width: Dp = 56.dp) =
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        T(d.label.uppercase(), ws(12.sp, FontWeight.Medium, if (highlight) Palette.Text else Palette.TextDim, spacing = 1.sp))
        Spacer(Modifier.height(5.dp))
        Icon(d.kind, 24.dp, colored)
        Spacer(Modifier.height(6.dp))
        RangeBar(d.min, d.max, lo, hi)
        Spacer(Modifier.height(5.dp))
        Row {
            T("${d.max}°", ws(13.5.sp, FontWeight.Medium))
            Spacer(Modifier.width(4.dp))
            T("${d.min}°", ws(13.5.sp, color = Palette.TextFaint))
        }
    }

@Composable
private fun GlasKarte(w: Weather, colored: Boolean) {
    val days = w.days.drop(1).take(3)
    val (lo, hi) = w.days.take(4).bounds()
    Row(
        Modifier.height(IntrinsicSize.Min).clip(Glass).glassBackground(Glass).padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(w.kind, 44.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(44.sp, FontWeight.ExtraLight, spacing = (-1.5).sp))
        Spacer(Modifier.width(10.dp))
        Column {
            T(w.description, ws(15.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
            T(w.details(), ws(13.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start))
        }
        if (days.isNotEmpty()) {
            Spacer(Modifier.width(18.dp))
            Divider()
            Spacer(Modifier.width(14.dp))
            days.forEach { DayColumn(it, colored, lo, hi, false) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(12.dp)).surface(Color.White.copy(alpha = 0.05f)).padding(horizontal = 10.dp, vertical = 8.dp),
) {
    T(label.uppercase(), ws(10.5.sp, FontWeight.Medium, Palette.TextFaint, spacing = 1.sp))
    Spacer(Modifier.height(2.dp))
    T(value, ws(15.sp, FontWeight.Medium))
}

@Composable
private fun Kennzahlen(w: Weather, colored: Boolean) = Column(
    Modifier.width(500.dp).clip(Glass).glassBackground(Glass).padding(horizontal = 18.dp, vertical = 16.dp),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 46.dp, colored, w.isDay)
        Spacer(Modifier.width(12.dp))
        T("${w.temperature}°", ws(48.sp, FontWeight.ExtraLight, spacing = (-2).sp))
        Spacer(Modifier.width(12.dp))
        Column {
            T(w.description, ws(16.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
            T(listOfNotNull(w.apparent?.let { trf("Gefühlt {0}°", "Feels like {0}°", it) }, w.hiLo().ifEmpty { null }).joinToString(" · "), ws(13.sp, color = Palette.TextDim))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Stat(tr("Wind", "Wind"), w.windText ?: "–", Modifier.weight(1f))
        Stat(tr("Luftfeuchte", "Humidity"), w.humidity?.let { trf("{0} %", "{0}%", it) } ?: "–", Modifier.weight(1f))
        Stat(tr("Regen", "Rain"), w.today?.rainChance?.let { trf("{0} %", "{0}%", it) } ?: "–", Modifier.weight(1f))
        Stat(tr("Sonne bis", "Sunset"), w.sunset ?: "–", Modifier.weight(1f))
    }
    val hours = w.hours.take(6)
    if (hours.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            hours.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(h.label, ws(11.5.sp, FontWeight.Medium, Palette.TextFaint))
                    Spacer(Modifier.height(4.dp))
                    Icon(h.kind, 20.dp, colored, h.isDay)
                    Spacer(Modifier.height(4.dp))
                    T("${h.temperature}°", ws(13.sp, FontWeight.Medium))
                }
            }
        }
    }
}

@Composable
private fun Stundenkurve(w: Weather, colored: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 26.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(18.sp, FontWeight.Medium))
        Spacer(Modifier.width(10.dp))
        T(listOf(w.description, w.hiLo()).filter { it.isNotEmpty() }.joinToString(" · "), ws(18.sp, color = Palette.TextDim))
    }
    val hours = w.hours.take(8)
    if (hours.size < 2) return@Column
    Spacer(Modifier.height(12.dp))
    val lo = hours.minOf { it.temperature }
    val hi = hours.maxOf { it.temperature }
    val top = 26.dp
    val band = 32.dp
    fun y(t: Int): Dp = top + band * ((hi - t).toFloat() / (hi - lo).coerceAtLeast(1))
    Box(Modifier.size(560.dp, 128.dp).clip(Glass).glassBackground(Glass)) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            val cw = size.width / hours.size
            val pts = hours.mapIndexed { i, h -> Offset(cw * i + cw / 2, y(h.temperature).toPx()) }
            val line = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val mx = (pts[i - 1].x + pts[i].x) / 2
                    cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
                }
            }
            val area = Path().apply {
                addPath(line)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(Warm.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(line, Warm.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()))
            pts.forEachIndexed { i, p -> drawCircle(if (i == 0) Color.White else Warm, (if (i == 0) 4f else 2.5f).dp.toPx(), p) }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            hours.forEachIndexed { i, h ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height((y(h.temperature) - 22.dp).coerceAtLeast(0.dp)))
                    T("${h.temperature}°", ws(13.sp, FontWeight.Medium))
                    Spacer(Modifier.weight(1f))
                    Icon(h.kind, 18.dp, colored, h.isDay)
                    Spacer(Modifier.height(3.dp))
                    T(if (h.rainChance >= 10) trf("{0} %", "{0}%", h.rainChance) else " ", ws(10.sp, FontWeight.Medium, Rain))
                    T(h.label, ws(11.sp, FontWeight.Medium, if (i == 0) Palette.Text else Palette.TextFaint))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, value: String) = Row(
    Modifier.clip(CircleShape).surface(Color.White.copy(alpha = 0.07f)).padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    T(label, ws(14.sp, color = Palette.TextDim))
    Spacer(Modifier.width(6.dp))
    T(value, ws(14.sp, FontWeight.Medium))
}

@Composable
private fun Chips(w: Weather, colored: Boolean, place: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 58.dp, colored, w.isDay)
        Spacer(Modifier.width(14.dp))
        T("${w.temperature}°", ws(74.sp, FontWeight.ExtraLight, spacing = (-3).sp))
        Spacer(Modifier.width(14.dp))
        Column {
            T(w.description, ws(20.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
            T(place, ws(15.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start))
        }
    }
    Spacer(Modifier.height(16.dp))
    val chips = listOfNotNull(
        w.apparent?.let { tr("Gefühlt", "Feels like") to "$it°" },
        w.windText?.let { tr("Wind", "Wind") to it },
        w.today?.rainChance?.let { tr("Regen", "Rain") to trf("{0} %", "{0}%", it) },
        w.sunset?.let { tr("Sonne bis", "Sunset") to it },
        w.today?.let { tr("Heute", "Today") to "${it.max}° / ${it.min}°" },
    )
    chips.chunked(3).forEach { line ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            line.forEach { (k, v) -> Chip(k, v) }
        }
    }
}

@Composable
private fun Gross(w: Weather, colored: Boolean) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(w.kind, 132.dp, colored, w.isDay)
    Spacer(Modifier.width(26.dp))
    Column {
        T("${w.temperature}°", ws(116.sp, FontWeight.ExtraLight, spacing = (-6).sp).copy(textAlign = TextAlign.Start))
        T(w.description, ws(20.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
        Spacer(Modifier.height(4.dp))
        T(listOf(w.hiLo(), w.today?.rainChance?.let { trf("Regen {0} %", "Rain {0}%", it) } ?: "").filter { it.isNotEmpty() }.joinToString("   ·   "), ws(16.sp, color = Palette.TextDim))
    }
}

@Composable
private fun Woche(w: Weather, colored: Boolean) {
    val days = w.days.take(7)
    val (lo, hi) = days.bounds()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, d ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                Modifier.width(66.dp).clip(shape)
                    .then(if (i == 0) Modifier.glassBackground(shape) else Modifier.surface(Color.White.copy(alpha = 0.04f)))
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                T(d.label, ws(13.sp, FontWeight.Medium, if (i == 0) Palette.Text else Palette.TextDim))
                Spacer(Modifier.height(8.dp))
                Icon(d.kind, 26.dp, colored)
                Spacer(Modifier.height(8.dp))
                T("${d.max}°", ws(15.sp, FontWeight.Medium))
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.size(5.dp, 40.dp)) {
                    val span = (hi - lo).coerceAtLeast(1).toFloat()
                    val r = CornerRadius(size.width / 2)
                    drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
                    val yTop = (hi - d.max) / span * size.height
                    val yBottom = ((hi - d.min) / span * size.height).coerceAtLeast(yTop + size.width)
                    drawRoundRect(
                        Brush.verticalGradient(listOf(Warm, Cold), startY = 0f, endY = size.height),
                        topLeft = Offset(0f, yTop), size = Size(size.width, yBottom - yTop), cornerRadius = r,
                    )
                }
                Spacer(Modifier.height(6.dp))
                T("${d.min}°", ws(13.sp, color = Palette.TextFaint))
            }
        }
    }
}

@Composable
private fun Regenradar(w: Weather, colored: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    val hours = w.hours.take(12)
    val title = when {
        w.rainFrom != null -> trf("Regen ab {0} Uhr", "Rain from {0}:00", w.rainFrom!!.label)
        w.dryFrom != null -> trf("Regen, ab {0} Uhr trocken", "Rain, dry from {0}:00", w.dryFrom!!.label)
        w.kind.wet -> tr("Regen in den nächsten Stunden", "Rain in the next few hours")
        else -> tr("Kein Regen in Sicht", "No rain in sight")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (w.kind.wet || w.rainFrom != null) WeatherKind.Rain else w.kind, 40.dp, colored, w.isDay)
        Spacer(Modifier.width(12.dp))
        Column {
            T(title, ws(22.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
            T(trf("jetzt {0}° · {1}", "Now {0}° · {1}", w.temperature, w.description), ws(15.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start))
        }
    }
    if (hours.size < 2) return@Column
    Spacer(Modifier.height(14.dp))
    val peak = hours.maxOf { it.precipitation }.coerceAtLeast(1f)
    Column(Modifier.width(540.dp).clip(Glass).glassBackground(Glass).padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 10.dp)) {
        Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            hours.forEach { h ->
                val f = maxOf(h.precipitation / peak, h.rainChance / 100f * 0.35f)
                Box(
                    Modifier.weight(1f).height((80.dp * f).coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (f > 0.04f) Brush.verticalGradient(listOf(Rain, Color(0xFF3D6FE8))) else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hours.forEachIndexed { i, h ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i % 2 == 0) {
                        T(
                            if (h.label == tr("Jetzt", "Now")) h.label else "${h.label}:00",
                            ws(12.sp, FontWeight.Medium, Palette.TextFaint),
                            Modifier.requiredWidthIn(min = 52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Satz(w: Weather, colored: Boolean) = Column(Modifier.width(600.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 34.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(40.sp, FontWeight.ExtraLight, spacing = (-1).sp))
    }
    Spacer(Modifier.height(10.dp))
    val tomorrow = w.days.getOrNull(1)
    BasicText(
        buildAnnotatedString {
            append(w.description)
            val rain = w.rainFrom?.let { trf("ab {0} Uhr Regen", "rain from {0}:00", it.label) } ?: w.dryFrom?.let { trf("ab {0} Uhr trocken", "dry from {0}:00", it.label) }
            if (rain != null) {
                append(", ")
                withStyle(SpanStyle(color = Rain)) { append(rain) }
            }
            append(if (I18n.code == "ja") "。" else ".")
            if (tomorrow != null) {
                withStyle(SpanStyle(color = Palette.TextDim)) {
                    append("\n" + trf("Morgen: {0}, bis {1}°", "Tomorrow: {0}, high of {1}°", tomorrow.description.let { if (I18n.code == "de") it.lowercaseAdjective() else it.lowercase(I18n.locale) }, tomorrow.max))
                    tomorrow.rainChance?.takeIf { it >= 30 && !tomorrow.kind.wet }?.let { append(trf(", Regen {0} %", ", rain {0}%", it)) }
                    append(if (I18n.code == "ja") "。" else ".")
                }
            }
        },
        style = ws(25.sp, family = SerifText).copy(lineHeight = 36.sp),
    )
}

@Composable
private fun Minimal(w: Weather, colored: Boolean, place: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 30.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(26.sp, FontWeight.Medium))
        T("  ·  ", ws(26.sp, color = Palette.TextFaint))
        T(place, ws(26.sp, color = Palette.TextDim))
    }
    Spacer(Modifier.height(6.dp))
    T(listOf(w.hiLo(), w.rainLine()).filter { it.isNotEmpty() }.joinToString(" · "), ws(15.sp, color = Palette.TextFaint, spacing = 0.5.sp))
}

@Composable
private fun FuenfTage(w: Weather, colored: Boolean) {
    val days = w.days.take(5)
    val (lo, hi) = days.bounds()
    Row(
        Modifier.height(IntrinsicSize.Min).clip(Glass).glassBackground(Glass).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(w.kind, 40.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        Column {
            T("${w.temperature}°", ws(40.sp, FontWeight.ExtraLight, spacing = (-1.5).sp).copy(textAlign = TextAlign.Start))
            w.apparent?.let { T(trf("Gefühlt {0}°", "Feels like {0}°", it), ws(12.5.sp, color = Palette.TextDim)) }
        }
        Spacer(Modifier.width(22.dp))
        Divider()
        Spacer(Modifier.width(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            days.forEachIndexed { i, d -> DayColumn(d, colored, lo, hi, i == 0) }
        }
    }
}

@Composable
private fun Drop(modifier: Modifier = Modifier) = Canvas(modifier.size(9.dp, 12.dp)) {
    val path = Path().apply {
        moveTo(size.width / 2, 0f)
        cubicTo(size.width * 1.05f, size.height * 0.45f, size.width, size.height, size.width / 2, size.height)
        cubicTo(0f, size.height, -size.width * 0.05f, size.height * 0.45f, size.width / 2, 0f)
        close()
    }
    drawPath(path, Rain)
}

@Composable
private fun Tageskarten(w: Weather, colored: Boolean) = Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    w.days.take(5).forEachIndexed { i, d ->
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier.width(96.dp).clip(shape)
                .then(
                    if (i == 0) Modifier.glassBackground(shape).then(
                        if (LocalPanels.current == dev.hiarni.gimbo.launcher.PanelStyle.Glas) Modifier.border(1.5.dp, Color(0xFFC9A8FF).copy(alpha = 0.55f), shape) else Modifier,
                    )
                    else Modifier.surface(Color.White.copy(alpha = 0.045f)),
                )
                .padding(top = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            T(d.label.uppercase(), ws(13.sp, FontWeight.SemiBold, if (i == 0) Palette.Text else Palette.TextDim, spacing = 1.sp))
            Spacer(Modifier.height(7.dp))
            Icon(d.kind, 34.dp, colored)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                T("${d.max}°", ws(18.sp, FontWeight.Medium))
                Spacer(Modifier.width(4.dp))
                T("${d.min}°", ws(15.sp, color = Palette.TextFaint))
            }
            d.rainChance?.let {
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Drop()
                    Spacer(Modifier.width(4.dp))
                    T(trf("{0} %", "{0}%", it), ws(12.sp, FontWeight.Medium, Rain))
                }
            }
        }
    }
}

@Composable
private fun Temperaturband(w: Weather, colored: Boolean) {
    val days = w.days.take(5)
    if (days.size < 2) return Dezent(w, colored)
    val (lo, hi) = days.bounds()
    val top = 46.dp
    val band = 96.dp
    fun y(t: Int): Dp = top + band * ((hi - t).toFloat() / (hi - lo).coerceAtLeast(1))
    Box(Modifier.size(560.dp, 172.dp).clip(Glass).glassBackground(Glass)) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width / days.size
            fun pts(f: (DayForecast) -> Int) = days.mapIndexed { i, d -> Offset(cw * i + cw / 2, y(f(d)).toPx()) }
            val highs = pts { it.max }
            val lows = pts { it.min }
            fun Path.curve(p: List<Offset>, start: Boolean) {
                if (start) moveTo(p[0].x, p[0].y) else lineTo(p[0].x, p[0].y)
                for (i in 1 until p.size) {
                    val mx = (p[i - 1].x + p[i].x) / 2
                    cubicTo(mx, p[i - 1].y, mx, p[i].y, p[i].x, p[i].y)
                }
            }
            val area = Path().apply { curve(highs, true); curve(lows.reversed(), false); close() }
            drawPath(area, Brush.verticalGradient(listOf(Warm.copy(alpha = 0.3f), Cold.copy(alpha = 0.22f))))
            drawPath(Path().apply { curve(highs, true) }, Warm, style = Stroke(2.dp.toPx()))
            drawPath(Path().apply { curve(lows, true) }, Rain, style = Stroke(2.dp.toPx()))
            highs.forEach { drawCircle(Warm, 3.dp.toPx(), it) }
            lows.forEach { drawCircle(Rain, 3.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxSize()) {
            days.forEach { d ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.align(Alignment.TopCenter).padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(d.label, ws(12.5.sp, FontWeight.Medium, Palette.TextDim))
                        Spacer(Modifier.width(6.dp))
                        Icon(d.kind, 18.dp, colored)
                    }
                    T("${d.max}°", ws(14.sp, FontWeight.Medium), Modifier.align(Alignment.TopCenter).padding(start = 30.dp, top = (y(d.max) - 20.dp).coerceAtLeast(0.dp)))
                    T("${d.min}°", ws(13.sp, color = Palette.TextDim), Modifier.align(Alignment.TopCenter).padding(start = 30.dp, top = y(d.min) + 2.dp))
                }
            }
        }
    }
}

@Composable
private fun JetztListe(w: Weather, colored: Boolean) {
    val days = w.days.take(5)
    val (lo, hi) = days.bounds()
    Row(
        Modifier.height(IntrinsicSize.Min).clip(Glass).glassBackground(Glass).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(130.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(w.kind, 54.dp, colored, w.isDay)
            Spacer(Modifier.height(4.dp))
            T("${w.temperature}°", ws(50.sp, FontWeight.ExtraLight, spacing = (-2).sp))
            T(w.description, ws(13.sp, color = Palette.TextDim))
        }
        Spacer(Modifier.width(20.dp))
        Divider()
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(d.label, ws(14.sp, FontWeight.Medium, if (i == 0) Palette.Text else Palette.TextDim).copy(textAlign = TextAlign.Start), Modifier.width(52.dp))
                    Icon(d.kind, 20.dp, colored)
                    T("${d.min}°", ws(14.sp, color = Palette.TextFaint).copy(textAlign = TextAlign.End), Modifier.width(36.dp))
                    Spacer(Modifier.width(10.dp))
                    RangeBar(d.min, d.max, lo, hi, 96.dp)
                    Spacer(Modifier.width(10.dp))
                    T("${d.max}°", ws(14.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start), Modifier.width(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ZeileFuenf(w: Weather, colored: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(w.kind, 24.dp, colored, w.isDay)
        Spacer(Modifier.width(10.dp))
        T("${w.temperature}°", ws(19.sp, FontWeight.Medium))
        Spacer(Modifier.width(10.dp))
        T(w.description, ws(19.sp, color = Palette.TextDim))
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        w.days.take(5).forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(d.label, ws(15.sp, FontWeight.Medium, Palette.TextFaint))
                Spacer(Modifier.width(7.dp))
                Icon(d.kind, 18.dp, colored)
                Spacer(Modifier.width(7.dp))
                T("${d.max}°", ws(15.sp, FontWeight.Medium))
                Spacer(Modifier.width(5.dp))
                T("${d.min}°", ws(15.sp, color = Palette.TextFaint))
            }
        }
    }
}

@Composable
private fun HeuteMorgen(w: Weather, colored: Boolean) = Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    w.days.take(2).forEachIndexed { i, d ->
        Column(Modifier.width(206.dp).fillMaxHeight().clip(Glass).glassBackground(Glass).padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T(if (i == 0) tr("HEUTE", "TODAY") else tr("MORGEN", "TOMORROW"), ws(14.sp, FontWeight.SemiBold, if (i == 0) Palette.Text else Palette.TextDim, spacing = 1.sp))
                Spacer(Modifier.weight(1f))
                Icon(d.kind, 30.dp, colored)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                T("${d.max}°", ws(34.sp, FontWeight.ExtraLight, spacing = (-1).sp))
                T(" / ${d.min}°", ws(24.sp, FontWeight.ExtraLight, Palette.TextFaint))
            }
            Spacer(Modifier.height(4.dp))
            val extra = if (i == 0) w.windText?.let { trf("Wind {0}", "Wind {0}", it) } else d.description
            T(
                listOfNotNull(d.rainChance?.let { trf("Regen {0} %", "Rain {0}%", it) }, extra).joinToString("\n"),
                ws(13.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start, lineHeight = 19.sp),
            )
        }
    }
    Column(Modifier.fillMaxHeight().padding(vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
        w.days.drop(2).take(3).forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(d.label, ws(14.sp, FontWeight.Medium, Palette.TextDim).copy(textAlign = TextAlign.Start), Modifier.width(30.dp))
                Icon(d.kind, 20.dp, colored)
                Spacer(Modifier.width(10.dp))
                T("${d.max}°", ws(14.sp, FontWeight.Medium))
                Spacer(Modifier.width(5.dp))
                T("${d.min}°", ws(14.sp, color = Palette.TextFaint))
            }
        }
    }
}

private fun tempColor(t: Int, lo: Int, hi: Int): Color {
    val f = ((t - lo).toFloat() / (hi - lo).coerceAtLeast(1)).coerceIn(0f, 1f)
    return androidx.compose.ui.graphics.lerp(Color(0xFF5CC8FF), Color(0xFFFFB36B), f)
}

@Composable
private fun Farbverlauf(w: Weather, colored: Boolean) {
    val panelStyle = LocalPanels.current
    val panels = panelStyle == dev.hiarni.gimbo.launcher.PanelStyle.Glas
    val fill = if (panelStyle == dev.hiarni.gimbo.launcher.PanelStyle.Transparent) 0.45f else 1f
    val days = w.days.take(5)
    val (lo, hi) = days.bounds()
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEachIndexed { i, d ->
            val shape = when (i) {
                0 -> RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp, topEnd = 6.dp, bottomEnd = 6.dp)
                days.lastIndex -> RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 22.dp, bottomEnd = 22.dp)
                else -> RoundedCornerShape(6.dp)
            }
            Column(
                Modifier.size(102.dp, 148.dp).clip(shape)
                    .then(
                        if (panelStyle == dev.hiarni.gimbo.launcher.PanelStyle.Aus) Modifier
                        else Modifier
                            .background(Brush.verticalGradient(listOf(tempColor(d.max, lo, hi).copy(alpha = 0.34f * fill), tempColor(d.min, lo, hi).copy(alpha = 0.08f * fill))))
                            .then(if (i == 0 && panels) Modifier.border(2.dp, Color.White.copy(alpha = 0.5f), shape) else Modifier),
                    )
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                T(d.label.uppercase(), ws(13.sp, FontWeight.SemiBold, spacing = 1.sp))
                Icon(d.kind, 34.dp, colored)
                T("${d.max}°", ws(30.sp, FontWeight.ExtraLight, if (panels) Palette.Text else tempColor(d.max, lo, hi), spacing = (-1).sp))
                T("${d.min}°", ws(13.sp, color = Palette.TextDim))
            }
        }
    }
}

// Sonstiges
private fun String.lowercaseAdjective(): String {
    val adjectives = listOf("Sonnig", "Klar", "Überwiegend", "Leicht", "Bewölkt", "Starker")
    return if (adjectives.any { startsWith(it) }) replaceFirstChar { it.lowercase() } else this
}
