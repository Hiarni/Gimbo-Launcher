package dev.hiarni.gimbo.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.BuiltIn
import dev.hiarni.gimbo.launcher.ImageCache
import dev.hiarni.gimbo.launcher.InstalledApps
import dev.hiarni.gimbo.launcher.Tile
import dev.hiarni.gimbo.launcher.TileImages
import dev.hiarni.gimbo.launcher.TileStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kachel-Darstellung
@Composable
fun TileFace(tile: Tile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val artKey = BuiltIn.artKey(tile.packageName)

    val pictureKey = when (tile.style) {
        TileStyle.Banner -> InstalledApps.bannerKey(tile.packageName)
        TileStyle.Image -> tile.imagePath?.let(TileImages::key)
        else -> null
    }
    val picture by rememberBitmap(
        pictureKey,
        peek = { pictureKey?.let(ImageCache::peek) },
        load = {
            when (tile.style) {
                TileStyle.Banner -> InstalledApps.banner(context.packageManager, tile.packageName)
                TileStyle.Image -> tile.imagePath?.let(TileImages::load)
                else -> null
            }
        },
    )

    Box(modifier.fillMaxSize()) {
        when {
            tile.style == TileStyle.Gimbo && artKey != null -> TileArt(artKey)
            (tile.style == TileStyle.Banner || tile.style == TileStyle.Image) && picture != null -> {
                Image(
                    bitmap = picture!!,
                    contentDescription = tile.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (tile.showLabel) BottomLabel(tile.label)
            }
            else -> IconFace(tile)
        }
    }
}

@Composable
private fun IconFace(tile: Tile) {
    val context = LocalContext.current
    val icon by rememberBitmap(
        tile.packageName,
        peek = { ImageCache.peek(InstalledApps.iconKey(tile.packageName)) },
        load = { InstalledApps.icon(context.packageManager, tile.packageName) },
    )
    val c = Color(tile.color)
    val glass = tile.style == TileStyle.Glas
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (glass) Brush.linearGradient(listOf(c.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)))
                else Brush.linearGradient(listOf(lerp(c, Color.Black, 0.10f), lerp(c, Color.Black, 0.62f))),
            )
            .then(if (glass) Modifier.border(2.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp)) else Modifier)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f),
                        radius = size.width * 0.75f,
                    ),
                )
            },
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val iconSize = if (tile.showLabel) 88.dp else 108.dp
            val bmp = icon
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = tile.label,
                    modifier = Modifier.size(iconSize).clip(RoundedCornerShape(iconSize * 0.22f)),
                )
            } else {
                BasicText(
                    tile.label.take(1).uppercase(),
                    style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 64.sp, color = Color.White),
                )
            }
            if (tile.showLabel) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                BasicText(
                    tile.label,
                    style = LabelStyle.copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

// Beschriftung
private val LabelStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    letterSpacing = (-0.3).sp,
    color = Color.White,
)

@Composable
private fun BottomLabel(text: String) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        BasicText(
            text,
            style = LabelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 22.dp, vertical = 18.dp),
        )
    }
}
