package dev.hiarni.gimbo.launcher.ui

import android.graphics.Bitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.mutableLongStateOf
import android.view.SoundEffectConstants
import android.os.SystemClock
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.hiarni.gimbo.launcher.AppLauncher
import dev.hiarni.gimbo.launcher.Backdrop
import dev.hiarni.gimbo.launcher.BuiltIn
import dev.hiarni.gimbo.launcher.WeatherStyle
import dev.hiarni.gimbo.launcher.ClockStyle
import dev.hiarni.gimbo.launcher.DayForecast
import dev.hiarni.gimbo.launcher.InstalledApps
import dev.hiarni.gimbo.launcher.MediaItem
import dev.hiarni.gimbo.launcher.MediaRow
import dev.hiarni.gimbo.launcher.Source
import dev.hiarni.gimbo.launcher.Jellyfin
import dev.hiarni.gimbo.launcher.Emby
import dev.hiarni.gimbo.launcher.LauncherConfig
import dev.hiarni.gimbo.launcher.R
import dev.hiarni.gimbo.launcher.Tile
import dev.hiarni.gimbo.launcher.Updater
import dev.hiarni.gimbo.launcher.TileImages
import dev.hiarni.gimbo.launcher.TileStore
import dev.hiarni.gimbo.launcher.TileStyle
import dev.hiarni.gimbo.launcher.Weather
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// Maße und Konstanten
private val NavKeys = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
)

private val BaseTileWidth = 384.dp
private val BaseTileHeight = 216.dp
private val TileShape = RoundedCornerShape(18.dp)
private val TileGap = 28.dp

private const val UiScale = 0.76f

// Seitenleisten
private sealed interface Sheet {
    data object Settings : Sheet
    data class Menu(val id: String) : Sheet
    data class Edit(val id: String) : Sheet
    data class PickApp(val forId: String?) : Sheet
    data class PickImage(val forId: String?) : Sheet
    data object WeatherPlace : Sheet
    data object AllApps : Sheet
    data object ClockStyles : Sheet
    data object WeatherStyles : Sheet
    data object Sizes : Sheet
    data class SourceInfo(val source: Source) : Sheet
    data class Connect(val source: Source) : Sheet
    data object Licenses : Sheet
    data object Languages : Sheet
    data object Update : Sheet
}

// Startseite
@Composable
fun LauncherScreen(
    config: LauncherConfig,
    weather: Weather?,
    initialTileId: String?,
    showHomeSetup: Boolean,
    onConfigChange: (LauncherConfig) -> Unit,
    onLaunch: (Tile) -> Unit,
    onOpenFireTvSettings: () -> Unit,
    onOpenHomeSetup: () -> Unit,
    editable: Boolean = true,
    requestImageAccess: ((Boolean) -> Unit) -> Unit = { it(false) },
    refreshKey: Int = 0,
    homeKey: Int = 0,
    animateIntro: Boolean = true,
    online: Boolean = true,
    onLaunchPackage: (String) -> Unit = {},
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onKeepScreenOn: (Boolean) -> Unit = {},
    rows: List<MediaRow> = emptyList(),
    onOpenItem: (MediaItem) -> Unit = {},
    onSourceConnected: (Source) -> Unit = {},
    onSourceSignOut: (Source) -> Unit = {},
    onOpenHomeSheet: (HomeSheet) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val latest by rememberUpdatedState(config)

    var focusedId by remember { mutableStateOf(initialTileId ?: config.tiles.firstOrNull()?.id) }
    val focusedTile = config.tiles.firstOrNull { it.id == focusedId } ?: config.tiles.firstOrNull()
    val accentTarget = focusedTile?.let { t ->
        BuiltIn.artKey(t.packageName)?.let { Color(BuiltIn.accent(it).first) } ?: Color(t.color)
    } ?: Color(0xFFC9A8FF)
    val accent = remember(accentTarget) { lerp(accentTarget, Color.White, 0.15f) }
    var sheets by remember { mutableStateOf(listOf<Sheet>()) }
    var movingId by remember { mutableStateOf<String?>(null) }
    var focusedItem by remember { mutableStateOf<MediaItem?>(null) }
    val tileListState = rememberLazyListState()
    val rowStates = remember { mutableMapOf<Source, LazyListState>() }
    fun rowState(source: Source) = rowStates.getOrPut(source) { LazyListState() }
    val firstRequesters = remember { mutableMapOf<Source, FocusRequester>() }
    fun firstRequester(source: Source) = firstRequesters.getOrPut(source) { FocusRequester() }
    val shelfRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val firstRowHeight = remember { mutableIntStateOf(0) }
    var shelfHasFocus by remember { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    val pageScroll = rememberScrollState()
    val glowTarget = remember { mutableStateOf<GlowTarget?>(null) }
    val glowVisible = remember { mutableStateOf(false) }
    val showShelf = rows.isNotEmpty()
    val stacked = rows.size > 1
    var focusTick by remember { mutableIntStateOf(0) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val addRequester = remember { FocusRequester() }

    val installed = remember(config.tiles, refreshKey) {
        config.tiles.map { it.packageName }.filter { InstalledApps.isInstalled(context.packageManager, it) }.toSet()
    }

    val sheetStates = rememberSaveableStateHolder()
    fun sheetKey(index: Int, sheet: Sheet) = "$index:$sheet"
    val restoreId = remember { arrayOfNulls<String>(1) }
    fun push(sheet: Sheet) {
        if (sheets.isEmpty()) restoreId[0] = focusedId
        sheets = sheets + sheet
    }
    fun pop() {
        val top = sheets.lastOrNull() ?: return
        sheetStates.removeState(sheetKey(sheets.lastIndex, top))
        sheets = sheets.dropLast(1)
    }
    fun closeAll() {
        sheets.forEachIndexed { i, sh -> sheetStates.removeState(sheetKey(i, sh)) }
        sheets = emptyList()
    }
    fun update(tile: Tile) = onConfigChange(latest.copy(tiles = latest.tiles.map { if (it.id == tile.id) tile else it }))
    fun remove(id: String) {
        movingId = null
        latest.tiles.firstOrNull { it.id == id }?.let { TileImages.delete(it.imagePath) }
        val rest = latest.tiles.filterNot { it.id == id }
        val index = latest.tiles.indexOfFirst { it.id == id }
        focusedId = rest.getOrNull(index.coerceAtMost(rest.size - 1))?.id
        restoreId[0] = null
        onConfigChange(latest.copy(tiles = rest))
        closeAll()
        focusTick++
    }
    fun move(id: String, delta: Int) {
        val list = latest.tiles.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return
        list.add(to, list.removeAt(from))
        onConfigChange(latest.copy(tiles = list))
        focusTick++
    }

    val intro = remember { Animatable(if (animateIntro) 0f else 1f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    LaunchedEffect(focusTick, sheets.isEmpty()) {
        if (sheets.isNotEmpty()) return@LaunchedEffect
        val wanted = restoreId[0] ?: focusedId
        restoreId[0] = null
        withFrameNanos { }
        val id = wanted?.takeIf { fid -> config.tiles.any { it.id == fid } } ?: config.tiles.firstOrNull()?.id
        if (id == null) {
            if (editable) runCatching { addRequester.requestFocus() }
            return@LaunchedEffect
        }
        val index = config.tiles.indexOfFirst { it.id == id }
        if (tileListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            runCatching { tileListState.scrollToItem(index) }
            withFrameNanos { }
        }
        runCatching { requesters[id]?.requestFocus() }
    }

    var lastInput by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var ambient by remember { mutableStateOf(false) }
    var swallowWake by remember { mutableStateOf(false) }
    fun resetForHome() {
        if (HomeRequests.at > 0 && SystemClock.uptimeMillis() - HomeRequests.at < 2_000) return
        closeAll()
        movingId = null
        TextEditing.active = false
        OkGuard.swallow = false
    }
    LaunchedEffect(homeKey) {
        if (homeKey > 0) resetForHome()
    }
    val request = HomeRequests.sheet
    LaunchedEffect(request, homeKey) {
        if (!editable || request == null) return@LaunchedEffect
        HomeRequests.sheet = null
        closeAll()
        movingId = null
        ambient = false
        push(if (request == HomeSheet.AllApps) Sheet.AllApps else Sheet.Settings)
    }
    LaunchedEffect(HomeRequests.focusRescue) {
        if (editable && HomeRequests.focusRescue > 0 && sheets.isEmpty()) focusTick++
    }
    LaunchedEffect(refreshKey) {
        lastInput = SystemClock.uptimeMillis()
        ambient = false
        if (refreshKey > 0 && !editable) {
            resetForHome()
            pageScroll.scrollTo(0)
        }
        if (refreshKey > 0 && movingId == null && sheets.isEmpty()) {
            AppLauncher.lastFocusedTileId?.let { id -> if (config.tiles.any { it.id == id }) focusedId = id }
            val itemKey = AppLauncher.lastFocusedItemId
            val row = if (itemKey == null) null else rows.firstOrNull { r -> r.items.any { it.key == itemKey } }
            val index = row?.items?.indexOfFirst { it.key == itemKey } ?: -1
            if (row != null && index >= 0) {
                focusedItem = row.items[index]
                withFrameNanos { }
                val state = rowState(row.source)
                if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    runCatching { state.scrollToItem(index) }
                    withFrameNanos { }
                }
                runCatching { shelfRequesters[itemKey]?.requestFocus() }
            } else {
                focusedItem = null
                focusTick++
            }
        }
    }
    LaunchedEffect(config.ambientMinutes, editable) {
        if (!editable || config.ambientMinutes <= 0) {
            ambient = false
            return@LaunchedEffect
        }
        while (true) {
            delay(5_000)
            val idle = SystemClock.uptimeMillis() - lastInput
            if (!ambient && sheets.isEmpty() && movingId == null && idle > config.ambientMinutes * 60_000L) ambient = true
        }
    }
    val keepOn by rememberUpdatedState(onKeepScreenOn)
    LaunchedEffect(ambient, config.ambientMinutes, editable) {
        if (!editable) return@LaunchedEffect
        if (config.ambientMinutes <= 0) {
            keepOn(false)
            return@LaunchedEffect
        }
        keepOn(true)
        if (ambient) {
            delay(30 * 60_000L)
            keepOn(false)
        }
    }
    val ambientAlpha by animateFloatAsState(if (ambient) 1f else 0f, tween(1200), label = "ambient")

    val homeFocusable = sheets.isEmpty() && movingId == null

    val backAction by rememberUpdatedState<() -> Unit> {
        if (sheets.isNotEmpty()) {
            if (BackDispatch.sheetBack?.invoke() != true) pop()
        } else if (movingId != null) {
            movingId = null
            focusTick++
        }
    }
    if (editable) {
        DisposableEffect(Unit) {
            BackDispatch.handler = { backAction() }
            onDispose { BackDispatch.handler = null }
        }
    }

    LaunchedEffect(contentHasFocus, sheets.isEmpty(), ambient) {
        if (contentHasFocus || sheets.isNotEmpty() || ambient) return@LaunchedEffect
        delay(120)
        if (!contentHasFocus) focusTick++
    }
    LaunchedEffect(showShelf) { if (!showShelf) focusedItem = null }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                lastInput = SystemClock.uptimeMillis()
                if (ambient || swallowWake) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (ambient) focusTick++
                        ambient = false
                        swallowWake = true
                    } else if (e.type == KeyEventType.KeyUp) {
                        swallowWake = false
                    }
                    return@onPreviewKeyEvent true
                }
                if (!contentHasFocus && sheets.isEmpty() && e.key in NavKeys) {
                    if (e.type == KeyEventType.KeyDown) focusTick++
                    return@onPreviewKeyEvent true
                }
                if (OkGuard.swallow && e.type == KeyEventType.KeyUp &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    OkGuard.swallow = false
                    return@onPreviewKeyEvent true
                }
                if (e.key == Key.Back && TextEditing.active) return@onPreviewKeyEvent false
                if (config.sounds && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionUp -> SoundEffectConstants.NAVIGATION_UP
                        Key.DirectionDown -> SoundEffectConstants.NAVIGATION_DOWN
                        Key.DirectionLeft -> SoundEffectConstants.NAVIGATION_LEFT
                        Key.DirectionRight -> SoundEffectConstants.NAVIGATION_RIGHT
                        else -> null
                    }?.let { view.playSoundEffect(it) }
                }
                if (e.key != Key.Back) return@onPreviewKeyEvent false
                when {
                    sheets.isNotEmpty() -> {
                        if (e.type == KeyEventType.KeyUp && BackDispatch.sheetBack?.invoke() != true) pop()
                        true
                    }
                    movingId != null -> { if (e.type == KeyEventType.KeyUp) { movingId = null; focusTick++ }; true }
                    else -> false
                }
            },
    ) {
        if (ambientAlpha < 1f) {
            BackdropLayer(config, focusedTile)
            if (config.jellyfinBackdrop) MediaBackdrop(focusedItem.takeIf { shelfHasFocus && sheets.isEmpty() })
            FocusGlow(glowTarget, glowVisible)
        }

        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * UiScale, base.fontScale),
            LocalUiSounds provides config.sounds,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onFocusChanged { contentHasFocus = it.hasFocus }
                    .graphicsLayer {
                        alpha = intro.value
                        translationY = (1f - intro.value) * 20.dp.toPx()
                    },
            ) {
                val rowsProgress by animateFloatAsState(
                    if (stacked && shelfHasFocus) 1f else 0f,
                    tween(450, easing = FastOutSlowInEasing),
                    label = "rows",
                )
                val pickerOpen = sheets.lastOrNull().let { it == Sheet.ClockStyles || it == Sheet.WeatherStyles || it == Sheet.Sizes }
                val pickerShift by animateFloatAsState(if (pickerOpen) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "pickerShift")
                BoxWithConstraints(
                    Modifier.fillMaxSize().graphicsLayer { translationX = -pickerShift * (StylePickerWidth / 2).toPx() },
                ) {
                    HomeLayout(
                        wantTileScale = config.tileScale,
                        collapsed = showShelf && shelfHasFocus,
                        headerKey = listOf(config.clockStyle, config.weatherStyle, config.clockScale, config.weatherScale, config.panelStyle, weather != null),
                        reportFit = editable,
                        tileRowHeight = { scale -> BaseTileHeight * scale + 52.dp },
                        modifier = Modifier.fillMaxSize(),
                        rowsProgress = { rowsProgress },
                        stackedShelf = stacked,
                        firstRowHeight = { firstRowHeight.intValue },
                        header = {
                        Header(config, weather, accent, collapsed = showShelf && shelfHasFocus)
                        },
                        tiles = { tileScale ->
                        TileRow(
                            scale = tileScale,
                            modifier = Modifier.onPreviewKeyEvent { e ->
                                if (movingId == null && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                                    val index = config.tiles.indexOfFirst { it.id == focusedId }
                                    val edge = index >= 0 &&
                                        ((e.key == Key.DirectionLeft && index == 0) ||
                                            (e.key == Key.DirectionRight && index == config.tiles.lastIndex))
                                    return@onPreviewKeyEvent edge
                                }
                                if (!showShelf || movingId != null || e.key != Key.DirectionDown) return@onPreviewKeyEvent false
                                if (e.type == KeyEventType.KeyDown) {
                                    val first = rows.firstOrNull()?.source
                                    if (first != null) scope.launch {
                                        runCatching { rowState(first).scrollToItem(0) }
                                        withFrameNanos { }
                                        runCatching { firstRequester(first).requestFocus() }
                                    }
                                }
                                true
                            },
                            config = config,
                            installed = installed,
                            movingId = movingId,
                            focusEnabled = homeFocusable,
                            editable = editable,
                            listState = tileListState,
                            requesterFor = { id -> requesters.getOrPut(id) { FocusRequester() } },
                            addRequester = addRequester,
                            onFocused = {
                                focusedId = it
                                focusedItem = null
                                glowVisible.value = true
                                AppLauncher.lastFocusedTileId = it
                                AppLauncher.lastFocusedItemId = null
                            },
                            onFocusLost = { glowVisible.value = false },
                            onGlow = { glowTarget.value = it },
                            onClick = { tile ->
                                if (movingId != null) { movingId = null; focusTick++ } else onLaunch(tile)
                            },
                            onMenu = { tile -> focusedId = tile.id; push(Sheet.Menu(tile.id)) },
                            onMove = { id, delta -> move(id, delta) },
                            onMoveDone = { movingId = null; focusTick++ },
                            onAdd = { push(Sheet.PickApp(null)) },
                        )
                        },
                        shelf = if (!showShelf) null else { {
                            val shelfDensity = LocalDensity.current
                            CompositionLocalProvider(
                                LocalDensity provides Density(shelfDensity.density * config.shelfScale, shelfDensity.fontScale),
                            ) {
                            Box(Modifier.onFocusChanged { shelfHasFocus = it.hasFocus }) {
                            ShelfStack(
                                rows = rows,
                                scrollable = stacked,
                                focusEnabled = homeFocusable,
                                stateFor = ::rowState,
                                firstRequesterFor = ::firstRequester,
                                requesterFor = { key -> shelfRequesters.getOrPut(key) { FocusRequester() } },
                                onUp = { focusTick++ },
                                onFocused = {
                                    focusedItem = it
                                    AppLauncher.lastFocusedItemId = it.key
                                },
                                onClick = onOpenItem,
                                onFirstRowHeight = { firstRowHeight.intValue = it },
                            )
                            }
                            }
                        } },
                    )
                }

                Row(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 64.dp)
                        .onPreviewKeyEvent { e ->
                            if (e.key != Key.DirectionDown && e.key != Key.DirectionUp) return@onPreviewKeyEvent false
                            if (e.key == Key.DirectionDown && e.type == KeyEventType.KeyDown) focusTick++
                            true
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!online) {
                        OfflineBadge()
                        Spacer(Modifier.width(10.dp))
                    }
                    val update = Updater.available
                    if (editable && update != null) {
                        Pill(trf("Update {0}", "Update {0}", update.version), homeFocusable) { push(Sheet.Update) }
                        Spacer(Modifier.width(10.dp))
                    }
                    if (showHomeSetup) {
                        Pill(tr("Home-Taste einrichten", "Set up Home button"), homeFocusable, onOpenHomeSetup)
                        Spacer(Modifier.width(10.dp))
                    }
                    AppsButton(homeFocusable) {
                        if (editable) push(Sheet.AllApps) else onOpenHomeSheet(HomeSheet.AllApps)
                    }
                    Spacer(Modifier.width(10.dp))
                    GearButton(homeFocusable) {
                        if (editable) push(Sheet.Settings) else onOpenHomeSheet(HomeSheet.Settings)
                    }
                }

                if (movingId != null) {
                    BasicText(
                        tr("◀ ▶  verschieben   ·   OK  fertig", "◀ ▶  move   ·   OK  done"),
                        style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = Palette.TextDim),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }

                sheets.lastOrNull()?.let { sheet ->
                    key(sheet) {
                        sheetStates.SaveableStateProvider(sheetKey(sheets.lastIndex, sheet)) {
                        SheetHost(
                            sheet = sheet,
                            config = config,
                            editable = editable,
                            requestImageAccess = requestImageAccess,
                            onConfigChange = onConfigChange,
                            onUpdate = ::update,
                            onPush = ::push,
                            onPop = ::pop,
                            onCloseAll = ::closeAll,
                            onRemove = ::remove,
                            onStartMove = { id -> closeAll(); focusedId = id; restoreId[0] = null; movingId = id; focusTick++ },
                            onCreated = { tile ->
                                onConfigChange(latest.copy(tiles = latest.tiles + tile))
                                focusedId = tile.id
                                AppLauncher.lastFocusedTileId = tile.id
                                AppLauncher.lastFocusedItemId = null
                                closeAll()
                                push(Sheet.Edit(tile.id))
                            },
                            onImportImage = { forId, uri ->
                                scope.launch {
                                    val id = forId ?: "backdrop"
                                    val path = withContext(Dispatchers.IO) {
                                        if (forId == null) TileImages.import(context, uri, id, 1920, 1080)
                                        else TileImages.import(context, uri, id)
                                    }
                                    if (path != null) {
                                        val cfg = latest
                                        if (forId == null) {
                                            onConfigChange(cfg.copy(backdrop = Backdrop.Bild, backdropImage = path))
                                        } else {
                                            onConfigChange(cfg.copy(tiles = cfg.tiles.map {
                                                if (it.id == forId) it.copy(imagePath = path, style = TileStyle.Image) else it
                                            }))
                                        }
                                    }
                                    if (sheets.lastOrNull() == Sheet.PickImage(forId)) pop()
                                }
                            },
                            onFireTvSettings = { closeAll(); onOpenFireTvSettings() },
                            onLaunchPackage = { pkg -> closeAll(); onLaunchPackage(pkg) },
                            onExport = onExport,
                            onImport = { closeAll(); onImport(); focusTick++ },
                            onSourceConnected = { source -> closeAll(); onSourceConnected(source); focusTick++ },
                            onSourceSignOut = onSourceSignOut,
                        )
                        }
                    }
                }

                if (ambientAlpha > 0f) AmbientLayer(config, weather, accent, ambientAlpha)
            }
        }
    }
}

@Composable
private fun SheetHost(
    sheet: Sheet,
    config: LauncherConfig,
    editable: Boolean,
    requestImageAccess: ((Boolean) -> Unit) -> Unit,
    onConfigChange: (LauncherConfig) -> Unit,
    onUpdate: (Tile) -> Unit,
    onPush: (Sheet) -> Unit,
    onPop: () -> Unit,
    onCloseAll: () -> Unit,
    onRemove: (String) -> Unit,
    onStartMove: (String) -> Unit,
    onCreated: (Tile) -> Unit,
    onImportImage: (String?, Uri) -> Unit,
    onFireTvSettings: () -> Unit,
    onLaunchPackage: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSourceConnected: (Source) -> Unit,
    onSourceSignOut: (Source) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val opened = remember { config }
    val current by rememberUpdatedState(config)
    fun tile(id: String) = config.tiles.firstOrNull { it.id == id }
    when (sheet) {
        Sheet.Settings -> SettingsSheet(
            config = config,
            onChange = onConfigChange,
            onAddApp = { onPush(Sheet.PickApp(null)) },
            onPickBackdropImage = { onPush(Sheet.PickImage(null)) },
            onPickWeatherPlace = { onPush(Sheet.WeatherPlace) },
            onPickClockStyle = { onPush(Sheet.ClockStyles) },
            onPickSizes = { onPush(Sheet.Sizes) },
            onPickWeatherStyle = { onPush(Sheet.WeatherStyles) },
            onFireTvSettings = onFireTvSettings,
            onExport = onExport,
            onImport = onImport,
            onSource = { onPush(Sheet.SourceInfo(it)) },
            onLicenses = { onPush(Sheet.Licenses) },
            onPickLanguage = { onPush(Sheet.Languages) },
            onCheckUpdates = { scope.launch { if (Updater.check(context) != null) onPush(Sheet.Update) } },
            onOpenUpdate = { onPush(Sheet.Update) },
        )
        Sheet.Update -> UpdateSheet(
            onInstall = { scope.launch { Updater.install(context) } },
            onLater = onPop,
        )
        Sheet.Languages -> LanguageSheet(config.language) {
            onConfigChange(config.copy(language = it))
            onPop()
        }
        is Sheet.SourceInfo -> SourceSheet(
            source = sheet.source,
            config = config,
            onChange = onConfigChange,
            onConnect = { onPush(Sheet.Connect(sheet.source)) },
            onSignOut = { onSourceSignOut(sheet.source) },
        )
        is Sheet.Connect -> when (sheet.source) {
            Source.Jellyfin -> ServerSetupSheet(Jellyfin) { onSourceConnected(Source.Jellyfin) }
            Source.Emby -> ServerSetupSheet(Emby) { onSourceConnected(Source.Emby) }
            Source.Plex -> PlexSetupSheet { onSourceConnected(Source.Plex) }
            Source.Kodi -> KodiSetupSheet { onSourceConnected(Source.Kodi) }
            Source.Recent -> LaunchedEffect(Unit) { onPop() }
        }
        Sheet.Licenses -> LicensesSheet()
        Sheet.Sizes -> SizeSheet(config, onConfigChange)
        Sheet.ClockStyles -> StylePickerSheet(
            title = tr("Uhr-Stil", "Clock style"),
            options = ClockStyle.entries.map { it.label },
            selected = config.clockStyle.ordinal,
            onPreview = { onConfigChange(config.copy(clockStyle = ClockStyle.entries[it])) },
            onDone = onPop,
            onDiscard = { onConfigChange(current.copy(clockStyle = opened.clockStyle, panelStyle = opened.panelStyle)) },
        ) {
            PanelChoice(config.panelStyle) { onConfigChange(config.copy(panelStyle = it)) }
        }
        Sheet.WeatherStyles -> StylePickerSheet(
            title = tr("Wetter-Stil", "Weather style"),
            options = WeatherStyle.entries.map { it.label },
            selected = config.weatherStyle.ordinal,
            onPreview = { onConfigChange(config.copy(weatherStyle = WeatherStyle.entries[it])) },
            onDone = onPop,
            onDiscard = {
                onConfigChange(current.copy(weatherStyle = opened.weatherStyle, panelStyle = opened.panelStyle, colorIcons = opened.colorIcons))
            },
        ) {
            PanelChoice(config.panelStyle) { onConfigChange(config.copy(panelStyle = it)) }
            ToggleRow(tr("Farbige Symbole", "Colored icons"), config.colorIcons) { onConfigChange(config.copy(colorIcons = it)) }
        }
        Sheet.AllApps -> AllAppsSheet(
            order = config.appOrder,
            canEdit = editable,
            onOrderChange = { onConfigChange(config.copy(appOrder = it)) },
            hidden = config.hiddenApps,
            onHiddenChange = { onConfigChange(config.copy(hiddenApps = it)) },
            onLaunch = { onLaunchPackage(it.packageName) },
            onAddTile = { onCreated(TileStore.newTile(context, it)) },
        )
        Sheet.WeatherPlace -> WeatherPlaceSheet(config.weatherPlace) { place ->
            onConfigChange(config.copy(weatherPlace = place))
            onPop()
        }
        is Sheet.Menu -> tile(sheet.id)?.let { t ->
            TileMenuSheet(
                tile = t,
                onEdit = { onPop(); onPush(Sheet.Edit(t.id)) },
                onMove = { onStartMove(t.id) },
                onAdd = { onPop(); onPush(Sheet.PickApp(null)) },
                onRemove = { onRemove(t.id) },
            )
        } ?: LaunchedEffect(Unit) { onPop() }
        is Sheet.Edit -> tile(sheet.id)?.let { t ->
            EditTileSheet(
                tile = t,
                onChange = onUpdate,
                onPickApp = { onPush(Sheet.PickApp(t.id)) },
                onPickImage = { onPush(Sheet.PickImage(t.id)) },
                onRemove = { onRemove(t.id) },
                onDone = onCloseAll,
            )
        } ?: LaunchedEffect(Unit) { onPop() }
        is Sheet.PickApp -> {
            val t = sheet.forId?.let(::tile)
            AppPickerSheet(
                title = if (t == null) tr("App hinzufügen", "Add app") else tr("App wählen", "Choose app"),
                current = t?.packageName,
            ) { app ->
                if (t == null) {
                    onCreated(TileStore.newTile(context, app))
                } else {
                    val oldLabel = InstalledApps.label(context.packageManager, t.packageName)
                    onUpdate(
                        t.copy(
                            packageName = app.packageName,
                            label = if (t.label == oldLabel || BuiltIn.artKey(t.packageName) != null) app.label else t.label,
                            style = TileStore.fitStyle(context, t.style, app.packageName),
                        ),
                    )
                    onPop()
                }
            }
        }
        is Sheet.PickImage -> ImagePickerSheet(
            title = if (sheet.forId == null) tr("Hintergrundbild", "Background image") else tr("Kachelbild", "Tile image"),
            requestAccess = requestImageAccess,
            onBack = onPop,
        ) { uri -> onImportImage(sheet.forId, uri) }
    }
}

// Kachel-Reihe
private data class GlowTarget(val center: Offset, val radius: Float, val color: Color)

@Composable
private fun TileRow(
    modifier: Modifier = Modifier,
    scale: Float,
    config: LauncherConfig,
    installed: Set<String>,
    movingId: String?,
    focusEnabled: Boolean,
    editable: Boolean,
    listState: LazyListState,
    requesterFor: (String) -> FocusRequester,
    addRequester: FocusRequester,
    onFocused: (String) -> Unit,
    onFocusLost: () -> Unit,
    onGlow: (GlowTarget) -> Unit,
    onClick: (Tile) -> Unit,
    onMenu: (Tile) -> Unit,
    onMove: (String, Int) -> Unit,
    onMoveDone: () -> Unit,
    onAdd: () -> Unit,
) {
    KeepInsideEdges(80.dp) {
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TileGap, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 80.dp, vertical = 26.dp),
    ) {
        items(config.tiles, key = { it.id }) { tile ->
            HomeTile(
                tile = tile,
                scale = scale,
                installed = tile.packageName in installed,
                moving = movingId == tile.id,
                focusEnabled = focusEnabled || movingId == tile.id,
                modifier = Modifier.animateItem().focusRequester(requesterFor(tile.id)),
                onFocused = { onFocused(tile.id) },
                onFocusLost = onFocusLost,
                onGlow = onGlow,
                onClick = { onClick(tile) },
                onMenu = if (editable) ({ onMenu(tile) }) else null,
                onMove = { delta -> onMove(tile.id, delta) },
                onMoveDone = onMoveDone,
            )
        }
        if (editable && config.tiles.isEmpty()) {
            item { AddTile(scale, focusEnabled, Modifier.focusRequester(addRequester), onAdd) }
        }
    }
    }
}

@Composable
private fun FocusGlow(targetState: State<GlowTarget?>, visibleState: State<Boolean>) {
    val t = targetState.value ?: return
    val alpha by animateFloatAsState(if (visibleState.value) 1f else 0f, tween(350), label = "glowA")
    val cx by animateFloatAsState(t.center.x, spring(stiffness = Spring.StiffnessMediumLow), label = "glowX")
    val cy by animateFloatAsState(t.center.y, spring(stiffness = Spring.StiffnessMediumLow), label = "glowY")
    val r by animateFloatAsState(t.radius, spring(stiffness = Spring.StiffnessMediumLow), label = "glowR")
    val color by animateColorAsState(t.color, tween(450), label = "glowC")
    if (alpha <= 0f) return
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val c = Offset(cx, cy)
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.5f * alpha), color.copy(alpha = 0.16f * alpha), Color.Transparent),
                c,
                r,
            ),
            radius = r,
            center = c,
        )
    }
}

@Composable
private fun HomeTile(
    tile: Tile,
    scale: Float,
    installed: Boolean,
    moving: Boolean,
    focusEnabled: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onFocusLost: () -> Unit,
    onGlow: (GlowTarget) -> Unit,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val p by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "tileFocus",
    )
    val lift by animateFloatAsState(if (moving) 1f else 0f, tween(200), label = "tileLift")
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "tilePress")
    val glow = Color(BuiltIn.artKey(tile.packageName)?.let { BuiltIn.accent(it).first } ?: tile.color)

    fun reportGlow() {
        val c = coords[0] ?: return
        if (!c.isAttached) return
        val pos = c.positionInRoot()
        val w = c.size.width.toFloat()
        val h = c.size.height.toFloat()
        onGlow(GlowTarget(Offset(pos.x + w / 2f, pos.y + h * 0.72f), w * 0.85f, glow))
    }

    Box(
        modifier
            .zIndex(p + lift)
            .size(BaseTileWidth * scale, BaseTileHeight * scale)
            .onGloballyPositioned {
                coords[0] = it
                if (focused) reportGlow()
            }
            .graphicsLayer {
                val s = 1f + 0.08f * p + 0.04f * lift - 0.05f * press
                scaleX = s
                scaleY = s
                translationY = -10.dp.toPx() * lift
            }
            .clip(TileShape)
            .drawWithContent {
                drawContent()
                val dim = if (installed) 0.32f else 0.55f
                drawRect(Color.Black.copy(alpha = dim * (1f - p) + (if (installed) 0f else 0.25f * p)))
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.16f * p), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.6f, size.height),
                    ),
                )
            }
            .border(3.dp, lerp(Color.White, Color(0xFFB48CFF), lift).copy(alpha = 0.95f * max(p, lift)), TileShape)
            .onFocusChanged {
                val was = focused
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                    reportGlow()
                } else if (was) {
                    onFocusLost()
                }
            }
            .onPreviewKeyEvent { e ->
                if (!moving) return@onPreviewKeyEvent false
                val delta = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    Key.DirectionUp, Key.DirectionDown, Key.Menu -> return@onPreviewKeyEvent true
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (e.type == KeyEventType.KeyUp) onMoveDone()
                        return@onPreviewKeyEvent true
                    }
                    else -> return@onPreviewKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) onMove(delta)
                true
            }
            .tvClickable(enabled = focusEnabled, onLongClick = onMenu, onPress = { pressed = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
            TileFace(tile)
        }
        if (moving) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("◀", "▶").forEach {
                    BasicText(
                        it,
                        style = TextStyle(fontSize = 22.sp, color = Color.White),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTile(scale: Float, focusEnabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "add")
    Box(
        modifier
            .size(BaseTileWidth * scale, BaseTileHeight * scale)
            .graphicsLayer { val s = 1f + 0.06f * p; scaleX = s; scaleY = s }
            .clip(TileShape)
            .background(lerp(Color.White.copy(alpha = 0.05f), Color.White, p))
            .border(2.dp, Color.White.copy(alpha = 0.18f), TileShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = focusEnabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            tr("+  App hinzufügen", "+  Add app"),
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 22.sp, color = lerp(Palette.TextDim, Palette.Base, p)),
        )
    }
}

// Hintergrund
@Composable
private fun BackdropLayer(config: LauncherConfig, focused: Tile?) {
    when (config.backdrop) {
        Backdrop.Follow -> {
            val art = focused?.let { BuiltIn.artKey(it.packageName) }
            val (a, b) = when {
                art != null -> BuiltIn.accent(art).let { Color(it.first) to Color(it.second) }
                focused != null -> Color(focused.color) to lerp(Color(focused.color), Color(0xFF1B2A6B), 0.55f)
                else -> Palette.Neutral to Palette.Neutral
            }
            AuroraBackground(a, b)
            Grain()
        }
        Backdrop.Tageszeit -> {
            val colors by produceState(daytimeColors()) {
                while (true) {
                    delay(60_000)
                    value = daytimeColors()
                }
            }
            AuroraBackground(colors.first, colors.second)
            Grain()
        }
        Backdrop.Schlicht -> {
            QuietBackground()
            Grain()
        }
        Backdrop.Bild -> {
            val path = config.backdropImage
            val image by produceState<ImageBitmap?>(null, path, config.backdropBlur) {
                value = withContext(Dispatchers.IO) {
                    path?.let { TileImages.decode(it, sample = if (config.backdropBlur) 2 else 1) }
                        ?.let { if (config.backdropBlur) TileImages.soften(it) else it }
                        ?.also { it.prepareToDraw() }
                        ?.asImageBitmap()
                }
            }
            val img = image
            if (img != null) {
                Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.6f))),
                    ),
                )
            } else {
                AuroraBackground(Palette.Neutral, Palette.Neutral)
            }
        }
        else -> {
            AuroraBackground(Color(config.backdrop.a), Color(config.backdrop.b))
            Grain()
        }
    }
}

@Composable
private fun QuietBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Palette.Base)
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF1B2A6B).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.5f, -size.height * 0.1f),
                radius = size.width * 0.75f,
            ),
        )
    }
}

@Composable
private fun AuroraBackground(accentA: Color, accentB: Color) {
    val a by animateColorAsState(accentA, tween(1400), label = "auroraA")
    val b by animateColorAsState(accentB, tween(1400), label = "auroraB")
    val frame = remember { AuroraFrame(384, 216) }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            withFrameMillis { }
            t = ((SystemClock.uptimeMillis() - start) % 60_000L) / 60_000f
            delay(83)
        }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        frame.render(t, a, b)
        drawImage(
            frame.image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

private class AuroraFrame(private val w: Int, private val h: Int) {
    private val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val image = bitmap.asImageBitmap()
    private val canvas = android.graphics.Canvas(bitmap)
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val base = Palette.Base.toArgb()
    private val deep = Color(0xFF1B2A6B)
    private val night = Color(0xFF0E3B4F)

    fun render(t: Float, a: Color, b: Color) {
        val phase = t * 2f * PI.toFloat()
        canvas.drawColor(base)
        blob(deep, 0.55f, 0.80f + 0.06f * cos(phase), 0.20f + 0.05f * sin(phase), 0.55f)
        blob(night, 0.45f, 0.15f + 0.05f * sin(phase * 2), 0.25f + 0.06f * cos(phase), 0.45f)
        blob(a, 0.50f, 0.30f + 0.10f * sin(phase), 0.95f + 0.04f * cos(phase * 2), 0.55f)
        blob(b, 0.40f, 0.72f + 0.08f * cos(phase + 1.3f), 0.90f + 0.05f * sin(phase), 0.50f)
        paint.shader = android.graphics.RadialGradient(
            w * 0.5f, h * 0.5f, w * 0.72f,
            intArrayOf(android.graphics.Color.TRANSPARENT, Palette.Base.copy(alpha = 0.70f).toArgb()),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun blob(color: Color, alpha: Float, cx: Float, cy: Float, r: Float) {
        val x = cx * w
        val y = cy * h
        val radius = r * w
        paint.shader = android.graphics.RadialGradient(
            x, y, radius,
            intArrayOf(color.copy(alpha = alpha).toArgb(), android.graphics.Color.TRANSPARENT),
            null,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(x, y, radius, paint)
    }
}

@Composable
private fun Grain() {
    val brush = remember {
        val size = 128
        val rnd = Random(7)
        val pixels = IntArray(size * size) {
            val v = rnd.nextInt(256)
            (rnd.nextInt(18) shl 24) or (v shl 16) or (v shl 8) or v
        }
        val bmp = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        drawRect(brush)
    }
}

// Kopf und Knöpfe
@Composable
internal fun Header(
    config: LauncherConfig,
    weather: Weather?,
    accent: Color,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    val size = config.weatherScale
    val scale by animateFloatAsState(
        if (collapsed) 0.6f * config.clockScale else config.clockScale,
        tween(400, easing = FastOutSlowInEasing),
        label = "headerScale",
    )
    val hourTick by produceState(System.currentTimeMillis() / 3_600_000L) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(3_600_000L - now % 3_600_000L + 1_000L)
            value = System.currentTimeMillis() / 3_600_000L
        }
    }
    val current = weather?.takeIf { hourTick * 3_600_000L - it.fetchedAt < 6 * 3_600_000L }
    CompositionLocalProvider(LocalPanels provides config.panelStyle) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(config.clockStyle, accent, current, Modifier.scaled(scale))
        if (current != null) key(hourTick) {
            val weather = current
            AnimatedVisibility(visible = !collapsed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp * size))
                    WeatherPanel(
                        config.weatherStyle, weather, config.colorIcons, config.weatherPlace.name,
                        Modifier.scaled(size),
                    )
                }
            }
            AnimatedVisibility(visible = collapsed) {
                WeatherMini(weather, config.colorIcons, Modifier.padding(top = 8.dp).scaled(size.coerceAtMost(1.2f)))
            }
        }
    }
    }
}

@Composable
private fun GearButton(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "gear")
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
        Image(
            painter = painterResource(R.drawable.ic_settings),
            contentDescription = tr("Einstellungen", "Settings"),
            modifier = Modifier.size(22.dp),
            colorFilter = ColorFilter.tint(lerp(Palette.TextDim, Palette.Base, p)),
        )
    }
}

@Composable
private fun Pill(text: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "pill")
    Box(
        Modifier
            .height(44.dp)
            .graphicsLayer {
                val s = 1f + 0.06f * p
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(lerp(Color.White.copy(alpha = 0.06f), Color.White, p))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = lerp(Palette.TextDim, Palette.Base, p),
            ),
        )
    }
}
