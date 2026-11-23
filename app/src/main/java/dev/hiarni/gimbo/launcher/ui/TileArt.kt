package dev.hiarni.gimbo.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Kachelbilder
@Composable
fun TileArt(key: String, modifier: Modifier = Modifier) {
    when (key) {
        "jellyfin" -> JellyfinTile(modifier)
        "smarttube" -> SmartTubeTile(modifier)
        else -> Box(modifier.fillMaxSize().background(Palette.Surface))
    }
}

// Jellyfin
private val JfGround = Color(0xFF0F1020)

@Composable
private fun JellyfinTile(modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(JfGround)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF2A2140), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.20f),
                        radius = size.width * 0.70f,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Canvas(Modifier.size(74.dp)) {
                val u = size.width / 74f
                val gradient = Brush.linearGradient(
                    listOf(Color(0xFFB16BE3), Color(0xFF16A3E6)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val outer = Path().apply {
                    moveTo(37f * u, 8f * u); lineTo(66f * u, 62f * u); lineTo(8f * u, 62f * u); close()
                }
                drawPath(outer, gradient, style = Fill)
                drawPath(outer, gradient, style = Stroke(width = 6f * u, join = StrokeJoin.Round))
                val inner = Path().apply {
                    moveTo(37f * u, 32f * u); lineTo(48f * u, 52f * u); lineTo(26f * u, 52f * u); close()
                }
                drawPath(inner, JfGround, style = Fill)
                drawPath(inner, JfGround, style = Stroke(width = 3f * u, join = StrokeJoin.Round))
            }
            BasicText(
                text = "Jellyfin",
                style = TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 50.sp,
                    letterSpacing = (-1.5).sp,
                    color = Color.White,
                ),
            )
        }
    }
}

// SmartTube
private val StRed = Color(0xFFFF3B30)

@Composable
private fun SmartTubeTile(modifier: Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF1A1A1D), Color(0xFF0C0C0E)))),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Canvas(Modifier.size(width = 78.dp, height = 56.dp)) {
                val u = size.width / 78f
                drawRoundRect(StRed, cornerRadius = CornerRadius(16f * u, 16f * u))
                val play = Path().apply {
                    moveTo(31f * u, 16f * u); lineTo(31f * u, 40f * u); lineTo(51f * u, 28f * u); close()
                }
                drawPath(play, Color.White)
            }
            BasicText(
                text = buildAnnotatedString {
                    append("Smart")
                    withStyle(SpanStyle(color = StRed)) { append("Tube") }
                },
                style = TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 46.sp,
                    letterSpacing = (-1.5).sp,
                    color = Color.White,
                ),
            )
        }
    }
}
