package dev.hiarni.gimbo.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

object HomeFit {
    val factor = mutableFloatStateOf(1f)
}

@Composable
fun HomeLayout(
    wantTileScale: Float,
    collapsed: Boolean,
    headerKey: Any,
    reportFit: Boolean,
    tileRowHeight: (Float) -> Dp,
    modifier: Modifier = Modifier,
    topPadding: Dp = 36.dp,
    bottomPadding: Dp = 28.dp,
    rowsProgress: () -> Float = { 0f },
    stackedShelf: Boolean = false,
    firstRowHeight: () -> Int = { 0 },
    header: @Composable () -> Unit,
    tiles: @Composable (Float) -> Unit,
    shelf: (@Composable () -> Unit)?,
) {
    val expandedHeader = remember(headerKey) { intArrayOf(0) }
    val lastReported = remember { floatArrayOf(-1f) }
    val tilesSlot = remember(tiles) { arrayOfNulls<@Composable () -> Unit>(1) }
    val tilesSlotScale = remember(tiles) { floatArrayOf(Float.NaN) }
    // Der Kopf wird über die Dichte verkleinert statt über eine Grafik-Ebene, damit die Schrift scharf bleibt.
    val headerScale = remember { mutableFloatStateOf(1f) }
    val headerSlot = remember(header) {
        @Composable {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density * headerScale.floatValue, d.fontScale), content = header)
        }
    }
    SubcomposeLayout(modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxWidth = width)
        val padTop = topPadding.roundToPx()
        val padBottom = bottomPadding.roundToPx()
        val gap = (if (shelf != null) 14.dp else 28.dp).roundToPx()

        val drawn = headerScale.floatValue
        val headerPlaceables = subcompose("header", headerSlot).map { it.measure(Constraints(maxWidth = (width * drawn).roundToInt())) }
        val headerNow = ((headerPlaceables.maxOfOrNull { it.height } ?: 0) / drawn).roundToInt()
        if (!collapsed) expandedHeader[0] = maxOf(expandedHeader[0], headerNow)
        else if (expandedHeader[0] == 0) expandedHeader[0] = headerNow
        val headerRef = expandedHeader[0]

        val stacked = shelf != null && stackedShelf
        val singleShelf = if (shelf != null && !stacked) subcompose("shelf", shelf).map { it.measure(loose) } else emptyList()
        val reserved = when {
            stacked -> firstRowHeight().takeIf { it > 0 } ?: 220.dp.roundToPx()
            else -> singleShelf.sumOf { it.height }
        }

        val room = height - padTop - padBottom - reserved - gap
        val wanted = headerRef + tileRowHeight(wantTileScale).roundToPx()
        val factor = if (wanted > room && wanted > 0) (room.toFloat() / wanted).coerceIn(0.25f, 1f) else 1f
        if (reportFit && lastReported[0] != factor) {
            lastReported[0] = factor
            HomeFit.factor.floatValue = factor
        }
        if (kotlin.math.abs(factor - drawn) > 0.002f) headerScale.floatValue = factor
        val tileScale = wantTileScale * factor
        if (tilesSlot[0] == null || tilesSlotScale[0] != tileScale) {
            tilesSlotScale[0] = tileScale
            tilesSlot[0] = { tiles(tileScale) }
        }

        val tilePlaceables = subcompose("tiles", tilesSlot[0]!!).map { it.measure(loose) }
        val tilesHeight = tilePlaceables.sumOf { it.height }

        val headerZone = (headerRef * factor).roundToInt()
        val total = headerZone + gap + tilesHeight + reserved
        val available = height - padTop - padBottom
        val top = padTop + ((available - total) / 2).coerceAtLeast(0)

        val p = if (stacked) rowsProgress().coerceIn(0f, 1f) else 0f
        val homeShelfY = top + headerZone + gap + tilesHeight
        val rowsShelfY = padTop + (headerNow * factor).roundToInt() + gap
        val shelfY = (homeShelfY + (rowsShelfY - homeShelfY) * p).roundToInt()
        val lift = homeShelfY - shelfY
        val headerTop = (top + (padTop - top) * p).roundToInt()

        val stackPlaceables = if (stacked) {
            val viewport = (height - shelfY).coerceAtLeast(reserved)
            subcompose("shelf", shelf!!).map { it.measure(Constraints(maxWidth = width, minHeight = viewport, maxHeight = viewport)) }
        } else {
            emptyList()
        }

        layout(width, height) {
            // Bis der Kopf in der neuen Größe gezeichnet ist (ein Bild lang), gleicht eine Ebene die Differenz aus
            val fix = factor / drawn
            headerPlaceables.forEach { pl ->
                val x = (width - pl.width) / 2
                if (kotlin.math.abs(fix - 1f) < 0.002f) {
                    pl.place(x, headerTop)
                } else {
                    pl.placeWithLayer(x, headerTop - (pl.height * (1f - fix) / 2f).roundToInt()) {
                        scaleX = fix
                        scaleY = fix
                    }
                }
            }
            var y = top + headerZone + gap - lift
            tilePlaceables.forEach { pl ->
                if (p > 0f) pl.placeWithLayer(0, y) { alpha = 1f - p } else pl.place(0, y)
                y += pl.height
            }
            if (stacked) {
                stackPlaceables.forEach { it.place(0, shelfY) }
            } else {
                singleShelf.forEach { pl ->
                    pl.place(0, y)
                    y += pl.height
                }
            }
        }
    }
}
