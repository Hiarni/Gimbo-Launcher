package dev.hiarni.gimbo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.hiarni.gimbo.launcher.WeatherKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Linien-Symbole
@Composable
fun WeatherIcon(kind: WeatherKind, isDay: Boolean, color: Color, modifier: Modifier = Modifier, colored: Boolean = false) {
    if (colored) {
        ColoredWeatherIcon(kind, isDay, modifier)
        return
    }
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
        when (kind) {
            WeatherKind.Clear -> if (isDay) sun(color, stroke, u, 12f, 12f, 4.2f) else moon(color, stroke, u, 12f, 12f)
            WeatherKind.PartlyCloudy -> {
                if (isDay) sun(color, stroke, u, 8.5f, 8.5f, 3.2f, rays = true) else moon(color, stroke, u, 9f, 9f, 0.75f)
                cloud(color, stroke, u, dy = 2f)
            }
            WeatherKind.Cloudy -> cloud(color, stroke, u)
            WeatherKind.Fog -> {
                for (y in listOf(8f, 12f, 16f)) {
                    drawLine(color, Offset(4f * u, y * u), Offset(20f * u, y * u), stroke.width, StrokeCap.Round)
                }
            }
            WeatherKind.Rain -> {
                cloud(color, stroke, u, dy = -2.5f)
                for (x in listOf(8.5f, 12f, 15.5f)) {
                    drawLine(color, Offset(x * u, 16.5f * u), Offset((x - 1f) * u, 20f * u), stroke.width, StrokeCap.Round)
                }
            }
            WeatherKind.Snow -> {
                cloud(color, stroke, u, dy = -2.5f)
                for ((x, y) in listOf(8.5f to 17.5f, 12f to 19.5f, 15.5f to 17.5f)) {
                    drawCircle(color, radius = 1.1f * u, center = Offset(x * u, y * u))
                }
            }
            WeatherKind.Thunder -> {
                cloud(color, stroke, u, dy = -2.5f)
                val bolt = Path().apply {
                    moveTo(12.8f * u, 15f * u)
                    lineTo(10.6f * u, 18.6f * u)
                    lineTo(13.2f * u, 18.6f * u)
                    lineTo(11.2f * u, 22f * u)
                }
                drawPath(bolt, color, style = stroke)
            }
        }
    }
}

private fun DrawScope.sun(
    color: Color, stroke: Stroke, u: Float, cx: Float, cy: Float, r: Float, rays: Boolean = true,
) {
    drawCircle(color, radius = r * u, center = Offset(cx * u, cy * u), style = stroke)
    if (!rays) return
    for (i in 0 until 8) {
        val angle = i * PI.toFloat() / 4f
        val inner = r + 2.2f
        val outer = r + 4.0f
        drawLine(
            color,
            Offset((cx + cos(angle) * inner) * u, (cy + sin(angle) * inner) * u),
            Offset((cx + cos(angle) * outer) * u, (cy + sin(angle) * outer) * u),
            stroke.width,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(color: Color, stroke: Stroke, u: Float, cx: Float, cy: Float, scale: Float = 1f) {
    val r = 6.5f * scale
    val outer = Path().apply {
        addOval(Rect(Offset((cx - r) * u, (cy - r) * u), Size(2 * r * u, 2 * r * u)))
    }
    val r2 = r * 0.85f
    val ox = cx + r * 0.55f
    val oy = cy - r * 0.40f
    val inner = Path().apply {
        addOval(Rect(Offset((ox - r2) * u, (oy - r2) * u), Size(2 * r2 * u, 2 * r2 * u)))
    }
    drawPath(Path.combine(PathOperation.Difference, outer, inner), color, style = stroke)
}

private fun DrawScope.cloud(color: Color, stroke: Stroke, u: Float, dy: Float = 0f) {
    val path = Path().apply {
        moveTo(7f * u, (18f + dy) * u)
        cubicTo(3.5f * u, (18f + dy) * u, 3.2f * u, (12.6f + dy) * u, 7f * u, (12.4f + dy) * u)
        cubicTo(7.6f * u, (8.4f + dy) * u, 13.6f * u, (7.2f + dy) * u, 15.4f * u, (11f + dy) * u)
        cubicTo(19.8f * u, (10.6f + dy) * u, 21.2f * u, (18f + dy) * u, 16.6f * u, (18f + dy) * u)
        close()
    }
    drawPath(path, color, style = stroke)
}

// Farbige Symbole
private val SunColor = Color(0xFFFFD36B)
private val CloudLight = Color(0xFFDFE6F2)
private val CloudGrey = Color(0xFFC9D1DE)
private val RainBlue = Color(0xFF7CC4FF)
private val MoonColor = Color(0xFFE4E8F5)

@Composable
private fun ColoredWeatherIcon(kind: WeatherKind, isDay: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(width = 1.7f * u, cap = StrokeCap.Round)
        fun filledCloud(color: Color, dy: Float) {
            val path = cloudPath(u, dy)
            drawPath(path, color)
            drawPath(path, color, style = stroke)
        }
        fun filledSun(cx: Float, cy: Float, r: Float) {
            drawCircle(SunColor, radius = r * u, center = Offset(cx * u, cy * u))
            for (i in 0 until 8) {
                val angle = i * PI.toFloat() / 4f
                drawLine(
                    SunColor,
                    Offset((cx + cos(angle) * (r + 2f)) * u, (cy + sin(angle) * (r + 2f)) * u),
                    Offset((cx + cos(angle) * (r + 3.8f)) * u, (cy + sin(angle) * (r + 3.8f)) * u),
                    stroke.width, StrokeCap.Round,
                )
            }
        }
        fun filledMoon(cx: Float, cy: Float, scale: Float) {
            val r = 6.5f * scale
            val outer = Path().apply { addOval(Rect(Offset((cx - r) * u, (cy - r) * u), Size(2 * r * u, 2 * r * u))) }
            val r2 = r * 0.85f
            val ox = cx + r * 0.55f
            val oy = cy - r * 0.40f
            val inner = Path().apply { addOval(Rect(Offset((ox - r2) * u, (oy - r2) * u), Size(2 * r2 * u, 2 * r2 * u))) }
            drawPath(Path.combine(PathOperation.Difference, outer, inner), MoonColor)
        }
        when (kind) {
            WeatherKind.Clear -> if (isDay) filledSun(12f, 12f, 4.4f) else filledMoon(12f, 12f, 1f)
            WeatherKind.PartlyCloudy -> {
                if (isDay) filledSun(8.5f, 8.5f, 3.2f) else filledMoon(9f, 9f, 0.75f)
                filledCloud(CloudLight, 2f)
            }
            WeatherKind.Cloudy -> filledCloud(CloudGrey, 0f)
            WeatherKind.Fog -> for (y in listOf(8f, 12f, 16f)) {
                drawLine(CloudGrey, Offset(4f * u, y * u), Offset(20f * u, y * u), stroke.width, StrokeCap.Round)
            }
            WeatherKind.Rain -> {
                filledCloud(CloudGrey, -2.5f)
                for (x in listOf(8.5f, 12f, 15.5f)) {
                    drawLine(RainBlue, Offset(x * u, 17f * u), Offset((x - 1f) * u, 20.5f * u), stroke.width, StrokeCap.Round)
                }
            }
            WeatherKind.Snow -> {
                filledCloud(CloudGrey, -2.5f)
                for ((x, y) in listOf(8.5f to 17.8f, 12f to 19.8f, 15.5f to 17.8f)) {
                    drawCircle(Color.White, radius = 1.2f * u, center = Offset(x * u, y * u))
                }
            }
            WeatherKind.Thunder -> {
                filledCloud(CloudGrey, -2.5f)
                val bolt = Path().apply {
                    moveTo(12.8f * u, 15f * u); lineTo(10.6f * u, 18.6f * u); lineTo(13.2f * u, 18.6f * u); lineTo(11.2f * u, 22f * u)
                }
                drawPath(bolt, SunColor, style = stroke)
            }
        }
    }
}

private fun cloudPath(u: Float, dy: Float) = Path().apply {
    moveTo(7f * u, (18f + dy) * u)
    cubicTo(3.5f * u, (18f + dy) * u, 3.2f * u, (12.6f + dy) * u, 7f * u, (12.4f + dy) * u)
    cubicTo(7.6f * u, (8.4f + dy) * u, 13.6f * u, (7.2f + dy) * u, 15.4f * u, (11f + dy) * u)
    cubicTo(19.8f * u, (10.6f + dy) * u, 21.2f * u, (18f + dy) * u, 16.6f * u, (18f + dy) * u)
    close()
}
