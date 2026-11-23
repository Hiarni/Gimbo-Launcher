package dev.hiarni.gimbo.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.Weather
import dev.hiarni.gimbo.launcher.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

// Ruhemodus
@Composable
internal fun AmbientLayer(config: dev.hiarni.gimbo.launcher.LauncherConfig, weather: Weather?, accent: Color, alpha: Float) {
    val x = remember { Animatable(0f) }
    val y = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        val rnd = Random(System.currentTimeMillis())
        while (true) {
            delay(60_000)
            launch { x.animateTo(rnd.nextInt(-320, 320).toFloat(), tween(8_000, easing = FastOutSlowInEasing)) }
            y.animateTo(rnd.nextInt(-170, 170).toFloat(), tween(8_000, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                translationX = x.value.dp.toPx()
                translationY = y.value.dp.toPx()
                this.alpha = 0.78f
            },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalPanels provides config.panelStyle) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ClockFace(config.clockStyle, accent, weather)
                if (weather != null) {
                    Spacer(Modifier.height(16.dp))
                    WeatherMini(weather, config.colorIcons)
                }
            }
            }
        }
    }
}

// Tageszeit-Hintergrund
private val DaytimeKeys = listOf(
    0f to (0xFF1B2A6B to 0xFF0E3B4F),
    6f to (0xFFE8672C to 0xFF6B3FD0),
    9f to (0xFFE0A21B to 0xFF3D6FE8),
    13f to (0xFF13A3B5 to 0xFF3D6FE8),
    18f to (0xFFE8672C to 0xFFD2458C),
    21f to (0xFF6B3FD0 to 0xFF1B2A6B),
    24f to (0xFF1B2A6B to 0xFF0E3B4F),
)

internal fun daytimeColors(): Pair<Color, Color> {
    val cal = Calendar.getInstance()
    val h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
    val i = DaytimeKeys.indexOfLast { it.first <= h }.coerceIn(0, DaytimeKeys.size - 2)
    val (h0, c0) = DaytimeKeys[i]
    val (h1, c1) = DaytimeKeys[i + 1]
    val t = ((h - h0) / (h1 - h0)).coerceIn(0f, 1f)
    return lerp(Color(c0.first), Color(c1.first), t) to lerp(Color(c0.second), Color(c1.second), t)
}

// Kleine Bedienelemente
@Composable
internal fun OfflineBadge() {
    Row(
        Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            val c = Palette.TextDim
            val center = Offset(size.width / 2f, size.height * 0.85f)
            for (r in listOf(0.85f, 0.58f, 0.31f)) {
                val rad = size.width * r
                drawArc(
                    color = c,
                    startAngle = 225f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(center.x - rad, center.y - rad),
                    size = Size(rad * 2, rad * 2),
                    style = stroke,
                )
            }
            drawLine(
                c,
                Offset(size.width * 0.12f, size.height * 0.08f),
                Offset(size.width * 0.88f, size.height * 0.92f),
                stroke.width,
                StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(8.dp))
        BasicText(
            tr("Offline", "Offline"),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Palette.TextDim),
        )
    }
}

@Composable
internal fun AppsButton(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "apps")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                val s = 1f + 0.1f * p
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(lerp(Color.White.copy(alpha = 0.06f), Color.White, p))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = lerp(Palette.TextDim, Palette.Base, p)
        Canvas(Modifier.size(18.dp)) {
            val r = size.width * 0.1f
            for (i in 0..2) for (j in 0..2) {
                drawCircle(tint, radius = r, center = Offset(size.width * (0.15f + 0.35f * i), size.height * (0.15f + 0.35f * j)))
            }
        }
    }
}
