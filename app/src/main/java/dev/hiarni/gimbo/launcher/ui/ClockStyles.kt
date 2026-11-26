package dev.hiarni.gimbo.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.ClockStyle
import dev.hiarni.gimbo.launcher.I18n
import dev.hiarni.gimbo.launcher.Weather
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Uhrzeit und Datum
internal class ClockInfo(val now: Long, is24: Boolean) {
    private val cal = Calendar.getInstance().apply { timeInMillis = now }
    private val locale = I18n.locale
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val hh: String = if (is24) "%02d".format(hour24) else ((hour24 + 11) % 12 + 1).toString()
    val mm: String = "%02d".format(minute)
    val time = "$hh:$mm"
    val weekday: String = SimpleDateFormat("EEEE", locale).format(Date(now))
    val weekdayShort: String = SimpleDateFormat("EE", locale).format(Date(now)).trimEnd('.')
    val dayMonth: String = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale).format(Date(now))
    val dateLong = trf("{0}, {1}", "{0}, {1}", weekday, dayMonth)
    val dateCaps = "$weekday · $dayMonth".uppercase(locale)
    val dateShort = trf("{0}, {1}", "{0}, {1}", weekdayShort, dayMonth)
    val dateShortest: String = trf("{0}, {1}", "{0}, {1}", weekdayShort, SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(Date(now)).trimEnd('.'))
    val dateNumeric: String = (weekdayShort.uppercase(locale) + " " + SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "ddMM"), locale).format(Date(now)))
    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
    val monthShort: String = SimpleDateFormat("MMM", locale).format(Date(now)).trimEnd('.').uppercase(locale)
    val week: Int = Calendar.getInstance(Locale.GERMANY).apply {
        firstDayOfWeek = Calendar.MONDAY
        minimalDaysInFirstWeek = 4
        timeInMillis = now
    }.get(Calendar.WEEK_OF_YEAR)
}

@Composable
internal fun rememberClockInfo(): ClockInfo {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            val ms = System.currentTimeMillis()
            value = ms
            delay(60_000L - ms % 60_000L + 25L)
        }
    }
    val is24 = remember { DateFormat.is24HourFormat(context) }
    return remember(now / 60_000L, is24, I18n.code) { ClockInfo(now, is24) }
}

// Hilfsfunktionen
internal fun Modifier.fitHeight(max: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val limit = max.roundToPx()
    val factor = if (placeable.height > limit && placeable.height > 0) limit.toFloat() / placeable.height else 1f
    val w = (placeable.width * factor).roundToInt()
    val h = (placeable.height * factor).roundToInt()
    layout(w, h) {
        placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
            scaleX = factor
            scaleY = factor
        }
    }
}

// Vergrößert über die Dichte statt über eine Grafik-Ebene: Schrift und Linien werden in echter Größe
// gezeichnet und bleiben scharf (Fire OS rastert Text in skalierten Ebenen nur in gerundeter Größe).
// Sieht aus wie die frühere Ebenen-Skalierung: Inhalt bekommt die Grenzen × factor, in dp also dieselben.
@Composable
internal fun Scaled(factor: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val base = LocalDensity.current
    Layout(
        content = {
            CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale), content = content)
        },
        modifier = modifier,
    ) { measurables, constraints ->
        fun scale(v: Int) = if (v == Constraints.Infinity) v else (v * factor).roundToInt()
        val placeable = measurables.first().measure(Constraints(maxWidth = scale(constraints.maxWidth), maxHeight = scale(constraints.maxHeight)))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

private fun ts(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Palette.Text,
    family: FontFamily = Inter,
    spacing: TextUnit = 0.sp,
    italic: Boolean = false,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size,
    letterSpacing = spacing,
    color = color,
    textAlign = TextAlign.Center,
)

@Composable
private fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier) =
    BasicText(text, style = style, modifier = modifier, maxLines = 3)

// Uhr-Stile
@Composable
fun ClockFace(style: ClockStyle, accent: Color, weather: Weather?, modifier: Modifier = Modifier) {
    val c = rememberClockInfo()
    Box(modifier, contentAlignment = Alignment.Center) {
        when (style) {
            ClockStyle.Klassisch -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(c.time, ts(104.sp, FontWeight.ExtraLight, spacing = (-3).sp))
                Spacer(Modifier.height(6.dp))
                Txt(c.dateLong, ts(19.sp, color = Palette.TextDim))
            }
            ClockStyle.Kontrast -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(c.dateCaps, ts(14.sp, FontWeight.Medium, Palette.TextDim, spacing = 3.sp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(c.hh, ts(108.sp, FontWeight.SemiBold, spacing = (-5).sp), Modifier.alignByBaseline())
                    Txt(":", ts(92.sp, FontWeight.ExtraLight, Palette.TextFaint), Modifier.alignByBaseline().padding(horizontal = 4.dp))
                    Txt(c.mm, ts(108.sp, FontWeight.ExtraLight, Palette.TextDim, spacing = (-5).sp), Modifier.alignByBaseline())
                }
            }
            ClockStyle.Gestapelt -> Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    buildAnnotatedString {
                        append(c.hh)
                        append("\n")
                        withStyle(SpanStyle(color = Palette.Text.copy(alpha = 0.38f))) { append(c.mm) }
                    },
                    style = ts(94.sp, FontWeight.Bold, family = Grotesk, spacing = (-4).sp)
                        .copy(textAlign = TextAlign.End, lineHeight = 82.sp),
                )
                Spacer(Modifier.width(24.dp))
                Box(
                    Modifier.size(width = 2.dp, height = 148.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(accent, Color(0xFF5CC8FF)))),
                )
                Spacer(Modifier.width(24.dp))
                Column {
                    Txt(c.weekday, ts(20.sp, FontWeight.Medium, family = Grotesk).copy(textAlign = TextAlign.Start))
                    Txt(c.dayMonth, ts(20.sp, FontWeight.Medium, Palette.TextDim, Grotesk).copy(textAlign = TextAlign.Start))
                    Txt(trf("KW {0}", "Week {0}", c.week), ts(16.sp, FontWeight.Medium, Palette.TextFaint, Grotesk).copy(textAlign = TextAlign.Start))
                }
            }
            ClockStyle.Analog -> Row(verticalAlignment = Alignment.CenterVertically) {
                AnalogFace(c, accent, Modifier.size(196.dp))
                Spacer(Modifier.width(28.dp))
                Column {
                    Txt(c.time, ts(54.sp, FontWeight.ExtraLight, spacing = (-2).sp).copy(textAlign = TextAlign.Start))
                    Spacer(Modifier.height(6.dp))
                    Txt("${c.weekday}\n${c.dayMonth}", ts(18.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start, lineHeight = 25.sp))
                }
            }
            ClockStyle.Editorial -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("${c.hh}.${c.mm}", ts(110.sp, family = SerifDisplay, spacing = (-1).sp))
                Txt(trf("{0}, der {1}", "{0}, {1}", c.weekday, c.dayMonth), ts(23.sp, color = Palette.TextDim, family = SerifText, italic = true))
            }
            ClockStyle.Wortuhr -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    wordTime(c.hour24, c.minute),
                    style = ts(40.sp, FontWeight.Normal, spacing = (-0.5).sp).copy(lineHeight = 50.sp),
                    modifier = Modifier.width(560.dp),
                )
                Spacer(Modifier.height(14.dp))
                Txt(c.dateCaps, ts(14.sp, FontWeight.Medium, Palette.TextFaint, spacing = 3.sp))
            }
            ClockStyle.Neon -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val glow = lerp(accent, Color.White, 0.25f)
                Txt(
                    c.time,
                    ts(98.sp, FontWeight.ExtraLight, lerp(Color.White, glow, 0.35f), Mono, (-4).sp)
                        .copy(shadow = Shadow(glow.copy(alpha = 0.9f), Offset.Zero, 36f)),
                )
                Spacer(Modifier.height(8.dp))
                Txt(c.dateNumeric, ts(17.sp, color = lerp(glow, Color.White, 0.3f), family = Mono, spacing = 2.sp))
            }
            ClockStyle.Kontur -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(
                    c.time,
                    ts(118.sp, FontWeight.Bold, Palette.Text.copy(alpha = 0.9f), Grotesk, (-4).sp)
                        .copy(drawStyle = Stroke(width = 2.2f)),
                )
                Txt(c.dateLong, ts(18.sp, FontWeight.Medium, Palette.TextDim, Grotesk))
            }
            ClockStyle.Kompakt -> Row(
                Modifier.clip(CircleShape).glassBackground(CircleShape).padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(c.time, ts(40.sp, FontWeight.Medium, spacing = (-1).sp))
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(width = 1.dp, height = 28.dp).background(Color.White.copy(alpha = 0.15f)))
                Spacer(Modifier.width(16.dp))
                Txt(c.dateShort, ts(18.sp, color = Palette.TextDim))
            }
            ClockStyle.Sonnenbogen -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(c.time, ts(92.sp, FontWeight.ExtraLight, spacing = (-3).sp))
                SunArc(c, weather, Modifier.size(width = 420.dp, height = 96.dp))
                Txt(daylightText(c, weather), ts(17.sp, color = Palette.TextDim))
            }
            ClockStyle.Klappanzeige -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    c.hh.padStart(2, ' ').forEach { Flap(it) }
                    Txt(":", ts(56.sp, FontWeight.ExtraLight, Palette.TextFaint), Modifier.padding(horizontal = 6.dp))
                    c.mm.forEach { Flap(it) }
                }
                Spacer(Modifier.height(14.dp))
                Txt(c.dateCaps, ts(14.sp, FontWeight.Medium, Palette.TextDim, spacing = 3.sp))
            }
            ClockStyle.Verlauf -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    c.time,
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(Color.White, lerp(accent, Color.White, 0.25f), Color(0xFF5CC8FF))),
                        fontFamily = Grotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 120.sp,
                        letterSpacing = (-6).sp,
                        textAlign = TextAlign.Center,
                    ),
                )
                Txt(c.dateLong, ts(19.sp, FontWeight.Medium, Palette.TextDim, Grotesk))
            }
            ClockStyle.Zeile -> Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(c.time, ts(84.sp, FontWeight.ExtraLight, spacing = (-3).sp))
                Spacer(Modifier.width(22.dp))
                Box(Modifier.size(width = 2.dp, height = 64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)))
                Spacer(Modifier.width(22.dp))
                Column {
                    Txt(c.weekday, ts(20.sp, FontWeight.Medium).copy(textAlign = TextAlign.Start))
                    Txt(c.dayMonth, ts(20.sp, color = Palette.TextDim).copy(textAlign = TextAlign.Start))
                }
            }
            ClockStyle.Ring -> Box(Modifier.size(226.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(226.dp)) {
                    val stroke = 5.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(Color(0xFF5CC8FF), accent, Color(0xFF5CC8FF))),
                        -90f, 360f * (c.minute + 1) / 60f, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(c.time, ts(62.sp, FontWeight.ExtraLight, spacing = (-2).sp))
                    Txt(c.dateShortest, ts(15.sp, color = Palette.TextDim))
                }
            }
            ClockStyle.Kalenderblatt -> Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(20.dp)
                Column(
                    Modifier.width(116.dp).clip(shape).glassBackground(shape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val panelStyle = LocalPanels.current
                    val panels = panelStyle != dev.hiarni.gimbo.launcher.PanelStyle.Aus
                    val headAlpha = if (panelStyle == dev.hiarni.gimbo.launcher.PanelStyle.Transparent) 0.4f else 1f
                    Box(
                        Modifier.fillMaxWidth()
                            .then(
                                if (panels) Modifier.background(
                                    Brush.horizontalGradient(listOf(accent.copy(alpha = headAlpha), lerp(accent, Color(0xFF3D6FE8), 0.5f).copy(alpha = headAlpha))),
                                )
                                else Modifier,
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt(c.monthShort, ts(14.sp, FontWeight.SemiBold, if (panels) Palette.Text else accent, spacing = 2.sp)) }
                    Txt(c.day, ts(58.sp, FontWeight.ExtraLight), Modifier.padding(top = 6.dp))
                    Txt(c.weekday, ts(14.sp, FontWeight.Medium, Palette.TextDim), Modifier.padding(bottom = 10.dp))
                }
                Spacer(Modifier.width(26.dp))
                Column {
                    Txt(c.time, ts(94.sp, FontWeight.ExtraLight, spacing = (-3).sp).copy(textAlign = TextAlign.Start))
                    Txt(trf("KW {0}", "Week {0}", c.week), ts(14.sp, FontWeight.Medium, Palette.TextFaint, spacing = 2.sp).copy(textAlign = TextAlign.Start))
                }
            }
        }
    }
}

// Flächen
val LocalPanels = androidx.compose.runtime.compositionLocalOf { dev.hiarni.gimbo.launcher.PanelStyle.Glas }

internal fun Modifier.glassBackground(shape: androidx.compose.ui.graphics.Shape): Modifier = this.composed {
    when (LocalPanels.current) {
        dev.hiarni.gimbo.launcher.PanelStyle.Glas -> Modifier
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))), shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
        dev.hiarni.gimbo.launcher.PanelStyle.Transparent -> Modifier.background(Color.White.copy(alpha = 0.035f), shape)
        dev.hiarni.gimbo.launcher.PanelStyle.Aus -> Modifier
    }
}

internal fun Modifier.surface(color: Color, shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape): Modifier =
    this.composed {
        when (LocalPanels.current) {
            dev.hiarni.gimbo.launcher.PanelStyle.Glas -> Modifier.background(color, shape)
            dev.hiarni.gimbo.launcher.PanelStyle.Transparent -> Modifier.background(color.copy(alpha = color.alpha * 0.55f), shape)
            dev.hiarni.gimbo.launcher.PanelStyle.Aus -> Modifier
        }
    }

// Bausteine der Uhr-Stile
@Composable
private fun Flap(ch: Char) {
    Box(
        Modifier
            .padding(horizontal = 3.dp)
            .size(width = 76.dp, height = 104.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(0f to Color(0xFF1D2130), 0.5f to Color(0xFF1A1E2B), 0.5f to Color(0xFF10131C), 1f to Color(0xFF0E1119)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Txt(ch.toString(), ts(80.sp, FontWeight.SemiBold))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.Black.copy(alpha = 0.55f)))
    }
}

@Composable
private fun AnalogFace(c: ClockInfo, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Color.White.copy(alpha = 0.04f), r, center)
        drawCircle(Color.White.copy(alpha = 0.08f), r, center, style = Stroke(1.dp.toPx()))
        for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val major = i % 3 == 0
            val outer = r - 6.dp.toPx()
            val inner = outer - (if (major) 13.dp else 7.dp).toPx()
            drawLine(
                Color.White.copy(alpha = if (major) 0.8f else 0.35f),
                Offset(center.x + sin(a) * inner, center.y - cos(a) * inner),
                Offset(center.x + sin(a) * outer, center.y - cos(a) * outer),
                (if (major) 3.dp else 2.dp).toPx(), StrokeCap.Round,
            )
        }
        fun hand(deg: Float, len: Float, width: Float, color: Color) {
            val a = deg * PI.toFloat() / 180f
            drawLine(color, center, Offset(center.x + sin(a) * len, center.y - cos(a) * len), width, StrokeCap.Round)
        }
        hand((c.hour24 % 12) * 30f + c.minute * 0.5f, r * 0.52f, 6.dp.toPx(), Palette.Text)
        hand(c.minute * 6f, r * 0.78f, 3.dp.toPx(), Palette.Text.copy(alpha = 0.85f))
        drawCircle(accent, 6.dp.toPx(), center)
    }
}

private fun minutesOf(hhmm: String?): Int? =
    hhmm?.split(':')?.takeIf { it.size == 2 }?.let { (h, m) -> h.toIntOrNull()?.times(60)?.plus(m.toIntOrNull() ?: 0) }

@Composable
private fun SunArc(c: ClockInfo, weather: Weather?, modifier: Modifier) {
    val known = minutesOf(weather?.sunrise) != null && minutesOf(weather?.sunset) != null
    val rise = minutesOf(weather?.sunrise) ?: 7 * 60
    val set = minutesOf(weather?.sunset) ?: 17 * 60
    val now = weather?.minuteOfDay(c.now) ?: (c.hour24 * 60 + c.minute)
    val t = ((now - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
    val day = now in rise..set
    val labelStyle = ts(13.sp, color = Palette.TextDim)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 20.dp.toPx()
            val right = size.width - 20.dp.toPx()
            val base = size.height - 22.dp.toPx()
            val top = 4.dp.toPx()
            fun point(f: Float): Offset {
                val x = (1 - f) * (1 - f) * left + 2 * (1 - f) * f * (size.width / 2) + f * f * right
                val y = (1 - f) * (1 - f) * base + 2 * (1 - f) * f * (top - (base - top) * 0.2f) + f * f * base
                return Offset(x, y)
            }
            val full = Path().apply {
                moveTo(left, base)
                quadraticTo(size.width / 2, top - (base - top) * 0.2f, right, base)
            }
            drawPath(full, Color.White.copy(alpha = 0.16f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 10f))))
            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            if (day && known) {
                val done = Path().apply {
                    moveTo(left, base)
                    var f = 0f
                    while (f <= t) { val p = point(f); lineTo(p.x, p.y); f += 0.02f }
                }
                drawPath(done, Brush.horizontalGradient(listOf(Color(0xFFFFB36B), Color(0xFFFFD36B))), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                val sun = point(t)
                drawCircle(Color(0xFFFFD36B).copy(alpha = 0.18f), 16.dp.toPx(), sun)
                drawCircle(Color(0xFFFFD36B), 8.dp.toPx(), sun)
            }
        }
        Txt(weather?.sunrise ?: "–", labelStyle, Modifier.align(Alignment.BottomStart))
        Txt(weather?.sunset ?: "–", labelStyle, Modifier.align(Alignment.BottomEnd))
    }
}

private fun daylightText(c: ClockInfo, weather: Weather?): String {
    val rise = minutesOf(weather?.sunrise) ?: return c.dateLong
    val set = minutesOf(weather?.sunset) ?: return c.dateLong
    val now = weather?.minuteOfDay(c.now) ?: (c.hour24 * 60 + c.minute)
    return when {
        now < rise -> trf("{0} · Sonnenaufgang {1}", "{0} · Sunrise {1}", c.dateLong, weather?.sunrise)
        now <= set -> {
            val left = set - now
            val span = if (left >= 60) trf("{0} h {1} min", "{0} h {1} min", left / 60, left % 60) else trf("{0} min", "{0} min", left)
            trf("{0} · noch {1} Tageslicht", "{0} · {1} of daylight left", c.dateLong, span)
        }
        else -> trf("{0} · Sonne ist untergegangen", "{0} · The sun has set", c.dateLong)
    }
}

private val HourWords = listOf("zwölf", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf")
private val HourWordsEn = listOf("twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven")

private fun wordTime(hour24: Int, minute: Int) = buildAnnotatedString {
    val total = hour24 * 60 + minute
    val rounded = ((total + 2) / 5) * 5
    val m = rounded % 60
    val h = (rounded / 60) % 24
    val english = I18n.code != "de"
    fun hourWord(x: Int) = (if (english) HourWordsEn else HourWords)[x % 12]
    val faint = SpanStyle(color = Palette.TextFaint)
    fun bright(s: String) = append(s)
    fun dim(s: String) = withStyle(faint) { append(s) }
    val pack = I18n.pack()?.wordClock
    if (pack != null) {
        if (pack.prefix.isNotEmpty()) dim(pack.prefix)
        pack.parts(hour24, minute).forEach { (text, strong) -> if (strong) bright(text) else dim(text) }
        return@buildAnnotatedString
    }
    dim(if (english) "it is " else "es ist ")
    if (english) {
        when (m) {
            0 -> { bright(hourWord(h)); dim(" o'clock") }
            5 -> { bright("five"); dim(" past "); bright(hourWord(h)) }
            10 -> { bright("ten"); dim(" past "); bright(hourWord(h)) }
            15 -> { bright("quarter"); dim(" past "); bright(hourWord(h)) }
            20 -> { bright("twenty"); dim(" past "); bright(hourWord(h)) }
            25 -> { bright("twenty-five"); dim(" past "); bright(hourWord(h)) }
            30 -> { bright("half"); dim(" past "); bright(hourWord(h)) }
            35 -> { bright("twenty-five"); dim(" to "); bright(hourWord(h + 1)) }
            40 -> { bright("twenty"); dim(" to "); bright(hourWord(h + 1)) }
            45 -> { bright("quarter"); dim(" to "); bright(hourWord(h + 1)) }
            50 -> { bright("ten"); dim(" to "); bright(hourWord(h + 1)) }
            else -> { bright("five"); dim(" to "); bright(hourWord(h + 1)) }
        }
        return@buildAnnotatedString
    }
    when (m) {
        0 -> { bright(if (h % 12 == 1) "ein" else hourWord(h)); dim(" Uhr") }
        5 -> { bright("fünf"); dim(" nach "); bright(hourWord(h)) }
        10 -> { bright("zehn"); dim(" nach "); bright(hourWord(h)) }
        15 -> { bright("Viertel"); dim(" nach "); bright(hourWord(h)) }
        20 -> { bright("zwanzig"); dim(" nach "); bright(hourWord(h)) }
        25 -> { bright("fünf"); dim(" vor "); bright("halb " + hourWord(h + 1)) }
        30 -> bright("halb " + hourWord(h + 1))
        35 -> { bright("fünf"); dim(" nach "); bright("halb " + hourWord(h + 1)) }
        40 -> { bright("zwanzig"); dim(" vor "); bright(hourWord(h + 1)) }
        45 -> { bright("Viertel"); dim(" vor "); bright(hourWord(h + 1)) }
        50 -> { bright("zehn"); dim(" vor "); bright(hourWord(h + 1)) }
        else -> { bright("fünf"); dim(" vor "); bright(hourWord(h + 1)) }
    }
}
