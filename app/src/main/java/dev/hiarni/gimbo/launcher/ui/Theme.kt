package dev.hiarni.gimbo.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.hiarni.gimbo.launcher.R

// Schriften
val Inter = FontFamily(
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val SerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))
val SerifText = FontFamily(
    Font(R.font.libre_baskerville, FontWeight.Normal),
    Font(R.font.libre_baskerville_italic, FontWeight.Normal, androidx.compose.ui.text.font.FontStyle.Italic),
)
val Grotesk = FontFamily(
    Font(R.font.space_grotesk_light, FontWeight.Light),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_extralight, FontWeight.ExtraLight),
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
)

// Farben
object Palette {
    val Base = Color(0xFF07080C)
    val Surface = Color(0xFF15171E)
    val Text = Color(0xFFF4F5F8)
    val TextDim = Color(0xA6F4F5F8)
    val TextFaint = Color(0x66F4F5F8)
    val Neutral = Color(0xFF3A3F55)
}
