package dev.hiarni.gimbo.launcher.ui

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.AppLanguage
import dev.hiarni.gimbo.launcher.Backdrop
import dev.hiarni.gimbo.launcher.BuiltIn
import dev.hiarni.gimbo.launcher.InstalledApp
import dev.hiarni.gimbo.launcher.InstalledApps
import dev.hiarni.gimbo.launcher.LauncherConfig
import dev.hiarni.gimbo.launcher.Tile
import dev.hiarni.gimbo.launcher.TileColors
import dev.hiarni.gimbo.launcher.TileImages
import dev.hiarni.gimbo.launcher.TileSize
import dev.hiarni.gimbo.launcher.TileStore
import dev.hiarni.gimbo.launcher.TileStyle
import dev.hiarni.gimbo.launcher.UpdateState
import dev.hiarni.gimbo.launcher.Updater
import dev.hiarni.gimbo.launcher.WeatherPlace
import dev.hiarni.gimbo.launcher.WeatherRepository
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Kachel
@Composable
fun TileMenuSheet(
    tile: Tile,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
) {
    SideSheet(title = tile.label, width = 460.dp) {
        SheetRow(tr("Bearbeiten", "Edit"), autoFocus = true, onClick = onEdit)
        SheetRow(tr("Verschieben", "Move"), onClick = onMove)
        SheetRow(tr("App hinzufügen", "Add app"), onClick = onAdd)
        Box(Modifier.padding(top = 12.dp)) { SheetRow(tr("Entfernen", "Remove"), danger = true, onClick = onRemove) }
    }
}

@Composable
fun EditTileSheet(
    tile: Tile,
    onChange: (Tile) -> Unit,
    onPickApp: () -> Unit,
    onPickImage: () -> Unit,
    onRemove: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val appLabel = remember(tile.packageName) {
        InstalledApps.label(context.packageManager, tile.packageName) ?: tr("Nicht installiert", "Not installed")
    }
    val styles = remember(tile.packageName) { TileStore.availableStyles(context, tile.packageName) }

    SideSheet(title = tr("Kachel bearbeiten", "Edit tile")) {
        TilePreview(tile)

        SheetSection(tr("App", "App"))
        SheetRow(tr("App", "App"), value = appLabel, autoFocus = true, onClick = onPickApp)
        NameRow("Name", tile.label) { onChange(tile.copy(label = it)) }

        SheetSection(tr("Darstellung", "Appearance"))
        ChoiceRow(styles.map { it.label }, styles.indexOf(tile.style).coerceAtLeast(0)) { i ->
            val style = styles[i]
            onChange(tile.copy(style = style))
            if (style == TileStyle.Image && tile.imagePath == null) onPickImage()
        }
        if (tile.style == TileStyle.Image) {
            SheetRow(tr("Bild", "Image"), value = if (tile.imagePath == null) tr("Wählen", "Choose") else tr("Ändern", "Change"), onClick = onPickImage)
        }
        if (tile.style != TileStyle.Gimbo) {
            ToggleRow(tr("Name auf der Kachel", "Name on tile"), tile.showLabel) { onChange(tile.copy(showLabel = it)) }
            SheetSection(tr("Farbe", "Color"))
            ColorRow(TileColors, tile.color) { onChange(tile.copy(color = it)) }
        }

        Box(Modifier.padding(top = 16.dp)) { SheetRow(tr("Fertig", "Done"), onClick = onDone) }
        SheetRow(tr("Entfernen", "Remove"), danger = true, onClick = onRemove)
    }
}

@Composable
private fun TilePreview(tile: Tile) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(18.dp)),
    ) {
        val scale = maxWidth / 384.dp
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
            TileFace(tile)
        }
    }
}

// App- und Bildauswahl
@Composable
fun AppPickerSheet(title: String, current: String?, onPick: (InstalledApp) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.list(context) }
    }
    SideSheet(title = title, scrollable = false) {
        val list = apps
        if (list == null) {
            BasicText(tr("Apps werden geladen …", "Loading apps…"), style = RowValue.copy(color = Palette.TextDim))
            return@SideSheet
        }
        val focusIndex = list.indexOfFirst { it.packageName == current }.coerceAtLeast(0)
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = focusIndex),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(list, key = { _, app -> app.packageName }) { i, app ->
                SheetRow(
                    title = app.label,
                    value = if (app.packageName == current) tr("Aktuell", "Current") else null,
                    autoFocus = i == focusIndex,
                    leading = { AppIcon(app.packageName) },
                ) { onPick(app) }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val context = LocalContext.current
    val icon by rememberBitmap(
        pkg,
        peek = { dev.hiarni.gimbo.launcher.ImageCache.peek(InstalledApps.iconKey(pkg)) },
        load = { InstalledApps.icon(context.packageManager, pkg) },
    )
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
fun ImagePickerSheet(
    title: String,
    requestAccess: ((Boolean) -> Unit) -> Unit,
    onBack: () -> Unit,
    onPick: (Uri) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { requestAccess { granted = it } }

    val images by produceState<List<Uri>?>(null, granted) {
        if (granted == true) value = withContext(Dispatchers.IO) { TileImages.recent(context) }
    }

    SideSheet(title = title, subtitle = tr("Neueste Bilder vom Fire TV, z. B. aus dem Download-Ordner", "Recent images on the Fire TV, e.g. from the Download folder"), width = 620.dp, scrollable = false) {
        when {
            granted == false -> {
                Hint(tr("Ohne Zugriff auf Bilder geht es nicht. Bei der Abfrage „Zulassen“ wählen.", "Image access is required. Choose “Allow” when asked."))
                SheetRow(tr("Erneut erlauben", "Allow again"), autoFocus = true) {
                    granted = null
                    requestAccess { granted = it }
                }
                SheetRow(tr("Zurück", "Back"), onClick = onBack)
            }
            images == null -> {
                Hint(tr("Bilder werden geladen …", "Loading images…"))
                SheetRow(tr("Zurück", "Back"), autoFocus = true, onClick = onBack)
            }
            images!!.isEmpty() -> {
                Hint(tr("Keine Bilder gefunden. Bilder z. B. mit der Downloader-App in den Download-Ordner laden.", "No images found. Load images into the Download folder, e.g. with the Downloader app."))
                SheetRow(tr("Zurück", "Back"), autoFocus = true, onClick = onBack)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                itemsIndexed(images!!) { i, uri -> Thumb(uri, autoFocus = i == 0) { onPick(uri) } }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    BasicText(text, style = RowValue.copy(color = Palette.TextDim))
}

@Composable
private fun Thumb(uri: Uri, autoFocus: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { TileImages.thumbnail(context, uri)?.asImageBitmap() }
    }
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "thumb")
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .focusRequester(rememberAutoFocus(autoFocus))
            .graphicsLayer { val s = 1f + 0.05f * p; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(3.dp, Color.White.copy(alpha = 0.95f * p), RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
    ) {
        bmp?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

// Einstellungen
@Composable
fun SettingsSheet(
    config: LauncherConfig,
    onChange: (LauncherConfig) -> Unit,
    onAddApp: () -> Unit,
    onPickBackdropImage: () -> Unit,
    onPickWeatherPlace: () -> Unit,
    onPickClockStyle: () -> Unit,
    onPickSizes: () -> Unit,
    onPickWeatherStyle: () -> Unit,
    onFireTvSettings: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSource: (dev.hiarni.gimbo.launcher.Source) -> Unit,
    onLicenses: () -> Unit,
    onPickLanguage: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
    SideSheet(title = "Launcher") {
        SheetSection(tr("Größe", "Size"))
        SheetRow(
            tr("Größen", "Sizes"),
            value = trf("Kacheln {0} %", "Tiles {0}%", (config.tileScale * 100).roundToInt()),
            autoFocus = true,
            onClick = onPickSizes,
        )
        SheetRow(tr("App hinzufügen", "Add app"), onClick = onAddApp)

        SheetSection(tr("Hintergrund", "Background"))
        Backdrop.entries.forEach { b ->
            val active = b == config.backdrop
            SheetRow(
                title = b.label,
                value = when {
                    b == Backdrop.Bild && (active || config.backdropImage == null) ->
                        if (config.backdropImage == null) tr("Wählen", "Choose") else tr("Ändern", "Change")
                    active -> tr("Aktiv", "Active")
                    else -> null
                },
                leading = { BackdropSwatch(b, active) },
            ) {
                if (b == Backdrop.Bild && (active || config.backdropImage == null)) onPickBackdropImage()
                else onChange(config.copy(backdrop = b))
            }
        }
        if (config.backdrop == Backdrop.Bild) {
            ToggleRow(tr("Weichzeichnen", "Blur"), config.backdropBlur) { onChange(config.copy(backdropBlur = it)) }
        }

        SheetSection(tr("Uhr", "Clock"))
        SheetRow(tr("Uhr-Stil", "Clock style"), value = config.clockStyle.label, onClick = onPickClockStyle)
        PanelChoice(config.panelStyle) { onChange(config.copy(panelStyle = it)) }

        SheetSection(tr("Wetter", "Weather"))
        ToggleRow(tr("Wetter anzeigen", "Show weather"), config.weatherEnabled) { onChange(config.copy(weatherEnabled = it)) }
        if (config.weatherEnabled) {
            SheetRow(tr("Wetter-Stil", "Weather style"), value = config.weatherStyle.label, onClick = onPickWeatherStyle)
            SheetRow(tr("Ort", "Location"), value = config.weatherPlace.name, onClick = onPickWeatherPlace)
            ChoiceRow(listOf("°C", "°F"), if (config.fahrenheit) 1 else 0) {
                onChange(config.copy(fahrenheit = it == 1))
            }
        }

        SheetSection(tr("Weiterschauen", "Continue watching"))
        dev.hiarni.gimbo.launcher.Shelves.revision
        config.rowOrder.forEach { source ->
            SheetRow(source.label, value = sourceStatus(context, config, source)) { onSource(source) }
        }
        ToggleRow(tr("Hintergrund zeigt den Titel", "Background shows the title"), config.jellyfinBackdrop) { onChange(config.copy(jellyfinBackdrop = it)) }

        SheetSection(tr("Ruhemodus", "Ambient mode"))
        val ambientSteps = listOf(0, 5, 10, 30)
        ChoiceRow(
            listOf(tr("Aus", "Off"), tr("5 Min", "5 min"), tr("10 Min", "10 min"), tr("30 Min", "30 min")),
            ambientSteps.indexOf(config.ambientMinutes).coerceAtLeast(0),
        ) { onChange(config.copy(ambientMinutes = ambientSteps[it])) }

        SheetSection(tr("System", "System"))
        SheetRow(tr("Sprache", "Language"), value = config.language.label, onClick = onPickLanguage)
        ToggleRow(tr("Töne", "Sounds"), config.sounds) { onChange(config.copy(sounds = it)) }
        SheetRow(tr("Einstellungen sichern", "Back up settings"), value = tr("Download-Ordner", "Download folder"), onClick = onExport)
        SheetRow(tr("Sicherung wiederherstellen", "Restore backup"), onClick = onImport)
        SheetRow(tr("Fire-TV-Einstellungen", "Fire TV settings"), onClick = onFireTvSettings)

        SheetSection(tr("Info", "Info"))
        SheetRow(tr("Version", "Version"), value = version) {}
        SheetRow(tr("Updates", "Updates"), value = updateStatus(Updater.state)) {
            if (Updater.available != null) onOpenUpdate() else if (!Updater.busy) onCheckUpdates()
        }
        ToggleRow(tr("Automatisch nach Updates suchen", "Check for updates automatically"), config.autoUpdate) {
            onChange(config.copy(autoUpdate = it))
        }
        SheetRow(tr("Wetterdaten", "Weather data"), value = "Open-Meteo.com · CC BY 4.0") {}
        SheetRow(tr("Lizenzen", "Licenses"), value = "MIT", onClick = onLicenses)
    }
}

// Updates
private fun updateStatus(state: UpdateState): String = when (state) {
    UpdateState.Idle -> tr("Jetzt suchen", "Check now")
    UpdateState.Checking -> tr("Suche …", "Checking …")
    UpdateState.UpToDate -> tr("Aktuell", "Up to date")
    is UpdateState.Available -> trf("{0} verfügbar", "{0} available", state.info.version)
    is UpdateState.Downloading -> trf("Lädt … {0} %", "Downloading … {0}%", (state.progress * 100).roundToInt())
    is UpdateState.Installing -> tr("Wird installiert …", "Installing …")
    is UpdateState.Failed -> state.message
}

@Composable
fun UpdateSheet(onInstall: () -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val state = Updater.state
    val info = Updater.available
    val current = remember { Updater.currentVersion(context) }
    SideSheet(
        title = info?.let { trf("Version {0}", "Version {0}", it.version) } ?: tr("Updates", "Updates"),
        subtitle = trf("Installiert: {0}", "Installed: {0}", current),
    ) {
        val notes = info?.let(Updater::notesFor).orEmpty()
        if (notes.isNotEmpty()) {
            SheetSection(tr("Neu in dieser Version", "What's new"))
            notes.forEach { Hint("·  $it") }
            Spacer(Modifier.height(12.dp))
        }
        when (state) {
            is UpdateState.Downloading -> SheetRow(updateStatus(state), autoFocus = true) {}
            is UpdateState.Installing -> SheetRow(updateStatus(state), autoFocus = true) {}
            is UpdateState.Failed -> {
                Hint(state.message)
                SheetRow(tr("Erneut versuchen", "Try again"), autoFocus = true, onClick = onInstall)
            }
            else -> if (info != null) SheetRow(tr("Jetzt installieren", "Install now"), autoFocus = true, onClick = onInstall)
        }
        SheetRow(tr("Später", "Later"), onClick = onLater)
        Spacer(Modifier.height(12.dp))
        Hint(tr("Nach dem Update startet der Launcher von selbst neu.", "The launcher restarts by itself after the update."))
        Hint(
            tr(
                "Blockiert Fire OS die Installation: Einstellungen › Mein Fire TV › Entwickleroptionen › Unbekannte Apps installieren › Gimbo Launcher erlauben.",
                "If Fire OS blocks the installation: Settings › My Fire TV › Developer options › Install unknown apps › allow Gimbo Launcher.",
            ),
        )
    }
}

// Lizenzen
@Composable
fun LanguageSheet(selected: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    SideSheet(title = tr("Sprache", "Language")) {
        AppLanguage.entries.forEach { language ->
            SheetRow(
                language.label,
                value = if (language == selected) "✓" else null,
                autoFocus = language == selected,
                onClick = { onSelect(language) },
            )
        }
    }
}

@Composable
fun LicensesSheet() {
    SideSheet(title = tr("Lizenzen", "Licenses")) {
        SheetSection("Gimbo Launcher")
        SheetRow("© 2026 Hiarni", value = "MIT", autoFocus = true) {}
        SheetSection(tr("Bibliotheken", "Libraries"))
        listOf("Jetpack Compose", "AndroidX", "Kotlin", "kotlinx.coroutines").forEach { SheetRow(it, value = "Apache 2.0") {} }
        SheetSection(tr("Schriften", "Fonts"))
        listOf("Inter", "DM Serif Display", "JetBrains Mono", "Libre Baskerville", "Space Grotesk").forEach { SheetRow(it, value = "OFL 1.1") {} }
        SheetSection(tr("Daten", "Data"))
        SheetRow(tr("Wetter: Open-Meteo.com", "Weather: Open-Meteo.com"), value = "CC BY 4.0") {}
        SheetRow(tr("Ortssuche: GeoNames", "Place search: GeoNames"), value = "CC BY 4.0") {}
        SheetSection(tr("Marken", "Trademarks"))
        Hint(
            tr(
                "Amazon, Fire TV und Fire OS sind Marken von Amazon.com, Inc. Jellyfin, SmartTube und andere Namen gehören ihren Inhabern. Der Gimbo Launcher ist ein unabhängiges Projekt.",
                "Amazon, Fire TV and Fire OS are trademarks of Amazon.com, Inc. Jellyfin, SmartTube and other names belong to their owners. Gimbo Launcher is an independent project.",
            ),
        )
    }
}

@Composable
private fun BackdropSwatch(b: Backdrop, active: Boolean) {
    val fill = when (b) {
        Backdrop.Follow -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(Color(0xFF8E4FD6), Color(0xFF0A8FD8), Color(0xFFF0453A), Color(0xFF8E4FD6)),
        )
        Backdrop.Tageszeit -> androidx.compose.ui.graphics.Brush.sweepGradient(
            listOf(Color(0xFFE0A21B), Color(0xFF13A3B5), Color(0xFFD2458C), Color(0xFF1B2A6B), Color(0xFFE0A21B)),
        )
        Backdrop.Schlicht -> androidx.compose.ui.graphics.SolidColor(Color(0xFF1B2A6B))
        Backdrop.Bild -> androidx.compose.ui.graphics.SolidColor(Color(0xFF596079))
        else -> androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(b.a), Color(b.b)))
    }
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(fill)
            .border(if (active) 2.dp else 0.dp, Color.White.copy(alpha = if (active) 0.9f else 0f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {}
}

// Wetter-Ort
@Composable
fun WeatherPlaceSheet(current: WeatherPlace, onPick: (WeatherPlace) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    SideSheet(title = tr("Wetter-Ort", "Weather location"), subtitle = trf("Aktuell: {0}", "Current: {0}", current.name)) {
        NameRow(
            title = tr("Ort suchen", "Search location"),
            value = query.ifEmpty { tr("Name eingeben", "Enter name") },
            startEditing = true,
            initialText = query,
        ) { q ->
            if (q.isBlank() || q == tr("Name eingeben", "Enter name")) return@NameRow
            query = q
            loading = true
            failed = false
            scope.launch {
                val found = WeatherRepository.search(q)
                failed = found == null
                results = found.orEmpty()
                loading = false
            }
        }
        when {
            loading -> Hint(tr("Suche …", "Searching…"))
            failed -> Hint(tr("Keine Verbindung zum Wetterdienst. Später noch einmal versuchen.", "Can't reach the weather service. Try again later."))
            results?.isEmpty() == true -> Hint(tr("Kein Ort gefunden.", "No location found."))
            else -> results?.forEachIndexed { i, place ->
                SheetRow(title = place.name, value = place.region, autoFocus = i == 0) { onPick(place) }
            }
        }
    }
}

// Alle Apps
@Composable
fun AllAppsSheet(
    order: List<String>,
    canEdit: Boolean,
    onOrderChange: (List<String>) -> Unit,
    hidden: List<String> = emptyList(),
    onHiddenChange: (List<String>) -> Unit = {},
    onLaunch: (InstalledApp) -> Unit,
    onAddTile: (InstalledApp) -> Unit,
) {
    val context = LocalContext.current
    val saveOrder by rememberUpdatedState(onOrderChange)
    val saveHidden by rememberUpdatedState(onHiddenChange)
    var hiddenSet by remember { mutableStateOf(hidden.toSet()) }
    var showHidden by remember { mutableStateOf(false) }
    val loaded by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { InstalledApps.list(context) }
    }
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(loaded) { loaded?.let { apps = InstalledApps.ordered(it, order) } }

    var moving by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<InstalledApp?>(null) }
    var focusPkg by remember { mutableStateOf<String?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val requesters = remember { HashMap<String, FocusRequester>() }

    DisposableEffect(Unit) {
        val back: () -> Boolean = {
            when {
                moving != null -> { moving = null; focusTick++; true }
                menuFor != null -> { menuFor = null; focusTick++; true }
                else -> false
            }
        }
        BackDispatch.sheetBack = back
        onDispose { if (BackDispatch.sheetBack === back) BackDispatch.sheetBack = null }
    }

    LaunchedEffect(focusTick) {
        if (focusTick == 0) return@LaunchedEffect
        val all = apps ?: return@LaunchedEffect
        val list = all.filter { it.packageName !in hiddenSet } +
            (if (showHidden) all.filter { it.packageName in hiddenSet } else emptyList())
        val pkg = focusPkg ?: return@LaunchedEffect
        val index = list.indexOfFirst { it.packageName == pkg }
        if (index < 0) return@LaunchedEffect
        withFrameNanos { }
        if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            runCatching { gridState.scrollToItem(index) }
            withFrameNanos { }
        }
        runCatching { requesters[pkg]?.requestFocus() }
    }

    fun setHidden(app: InstalledApp, hide: Boolean) {
        hiddenSet = if (hide) hiddenSet + app.packageName else hiddenSet - app.packageName
        saveHidden(hiddenSet.toList())
    }

    fun move(delta: Int) {
        val list = apps?.filter { it.packageName !in hiddenSet } ?: return
        val pkg = moving ?: return
        val from = list.indexOfFirst { it.packageName == pkg }
        if (from < 0) return
        if (kotlin.math.abs(delta) > 1 && from + delta !in list.indices) return
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return
        val next = list.toMutableList().apply { add(to, removeAt(from)) }
        val rest = apps.orEmpty().filter { it.packageName in hiddenSet }
        apps = next + rest
        saveOrder((next + rest).map { it.packageName })
        focusPkg = pkg
        focusTick++
    }

    val menu = menuFor
    SideSheet(
        title = menu?.label ?: tr("Alle Apps", "All apps"),
        subtitle = when {
            menu != null -> null
            moving != null -> tr("◀ ▶ ▲ ▼  verschieben   ·   OK  fertig", "◀ ▶ ▲ ▼  move   ·   OK  done")
            canEdit -> tr("☰ oder OK lang halten: verschieben, ausblenden oder zur Startseite", "☰ or hold OK: move, hide or add to home screen")
            else -> null
        },
        width = 800.dp,
        scrollable = false,
    ) {
        val all = apps
        if (all == null) {
            Hint(tr("Apps werden geladen …", "Loading apps…"))
            return@SideSheet
        }
        val visible = all.filter { it.packageName !in hiddenSet }
        val hiddenApps = all.filter { it.packageName in hiddenSet }
        LaunchedEffect(hiddenApps.isEmpty()) { if (hiddenApps.isEmpty()) showHidden = false }
        val list = if (showHidden) visible + hiddenApps else visible
        if (menu != null) {
            val isHidden = menu.packageName in hiddenSet
            SheetRow(title = tr("Öffnen", "Open"), autoFocus = true) {
                menuFor = null
                focusPkg = menu.packageName
                focusTick++
                onLaunch(menu)
            }
            if (!isHidden) {
                SheetRow(title = tr("Verschieben", "Move"), value = tr("Reihenfolge in Alle Apps", "Order in All apps")) {
                    focusPkg = menu.packageName
                    moving = menu.packageName
                    menuFor = null
                    focusTick++
                }
            }
            SheetRow(
                title = if (isHidden) tr("Wieder einblenden", "Show again") else tr("Ausblenden", "Hide"),
                value = if (isHidden) null else tr("aus Alle Apps", "from All apps"),
            ) {
                setHidden(menu, !isHidden)
                focusPkg = if (!isHidden && !showHidden) {
                    val i = visible.indexOfFirst { it.packageName == menu.packageName }
                    (visible.getOrNull(i + 1) ?: visible.getOrNull(i - 1))?.packageName
                } else {
                    menu.packageName
                }
                menuFor = null
                focusTick++
            }
            SheetRow(title = tr("Zur Startseite hinzufügen", "Add to home screen"), value = tr("als Kachel", "as tile")) { onAddTile(menu) }
            if (order.isNotEmpty()) {
                SheetRow(title = tr("Alphabetisch sortieren", "Sort alphabetically"), value = tr("eigene Reihenfolge verwerfen", "discard custom order")) {
                    apps = all.sortedBy { it.label.lowercase() }
                    saveOrder(emptyList())
                    focusPkg = menu.packageName
                    menuFor = null
                    focusTick++
                }
            }
            return@SideSheet
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(AppColumns),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(6.dp),
        ) {
            itemsIndexed(list, key = { _, app -> app.packageName }) { i, app ->
                val requester = requesters.getOrPut(app.packageName) { FocusRequester() }
                AppCell(
                    app = app,
                    autoFocus = i == 0 && focusTick == 0,
                    requester = requester,
                    moving = moving == app.packageName,
                    modifier = Modifier.animateItem()
                        .graphicsLayer { alpha = if (app.packageName in hiddenSet) 0.45f else 1f },
                    onMove = ::move,
                    onMoveDone = { moving = null; focusPkg = app.packageName; focusTick++ },
                    onClick = { onLaunch(app) },
                    onLongClick = {
                        if (canEdit) {
                            focusPkg = app.packageName
                            menuFor = app
                        } else {
                            onAddTile(app)
                        }
                    },
                )
            }
            if (canEdit && hiddenApps.isNotEmpty() && moving == null) {
                item(key = "hidden-toggle") {
                    HiddenToggleCell(
                        count = hiddenApps.size,
                        showing = showHidden,
                        modifier = Modifier.animateItem(),
                    ) {
                        showHidden = !showHidden
                        if (showHidden) {
                            focusPkg = hiddenApps.first().packageName
                            focusTick++
                        }
                    }
                }
            }
        }
    }
}

private const val AppColumns = 4

@Composable
private fun HiddenToggleCell(count: Int, showing: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "hiddenCell")
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .graphicsLayer { val s = 1f + 0.05f * p; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.02f), Color.White, p))
            .border(1.dp, Color.White.copy(alpha = 0.12f * (1f - p)), RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            if (showing) tr("Ausgeblendete verbergen", "Hide hidden apps") else tr("Ausgeblendete zeigen", "Show hidden apps"),
            style = RowValue.copy(
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = androidx.compose.ui.graphics.lerp(Palette.TextDim, Palette.Base, p),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        BasicText(
            if (count == 1) trf("{0} App", "{0} app", count) else trf("{0} Apps", "{0} apps", count),
            style = RowValue.copy(fontSize = 14.sp, color = androidx.compose.ui.graphics.lerp(Palette.TextFaint, Palette.Base, p)),
        )
    }
}

@Composable
private fun AppCell(
    app: InstalledApp,
    autoFocus: Boolean,
    requester: FocusRequester,
    moving: Boolean,
    modifier: Modifier = Modifier,
    onMove: (Int) -> Unit,
    onMoveDone: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val icon by rememberBitmap(
        app.packageName,
        peek = { dev.hiarni.gimbo.launcher.ImageCache.peek(InstalledApps.iconKey(app.packageName)) },
        load = { InstalledApps.icon(context.packageManager, app.packageName) },
    )
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused && !moving) 1f else 0f, tween(160), label = "cell")
    val lift by animateFloatAsState(if (moving) 1f else 0f, tween(180), label = "cellLift")
    val auto = rememberAutoFocus(autoFocus)
    val move by rememberUpdatedState(onMove)
    val done by rememberUpdatedState(onMoveDone)
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .focusRequester(auto)
            .focusRequester(requester)
            .graphicsLayer { val s = 1f + 0.05f * p + 0.08f * lift; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.lerp(Color.White.copy(alpha = 0.045f), Color.White, p))
            .border(3.dp, Palette.Text.copy(alpha = lift), RoundedCornerShape(18.dp))
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (!moving) return@onPreviewKeyEvent false
                val delta = when (e.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    Key.DirectionUp -> -AppColumns
                    Key.DirectionDown -> AppColumns
                    else -> 0
                }
                when {
                    delta != 0 -> { if (e.type == KeyEventType.KeyDown) move(delta); true }
                    e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Menu -> {
                        if (e.type == KeyEventType.KeyUp) done()
                        true
                    }
                    else -> false
                }
            }
            .tvClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(15.dp))) {
            icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        BasicText(
            app.label,
            style = RowValue.copy(
                fontSize = 16.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = androidx.compose.ui.graphics.lerp(Palette.Text, Palette.Base, p),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// Stil und Größe
@Composable
fun StylePickerSheet(
    title: String,
    options: List<String>,
    selected: Int,
    onPreview: (Int) -> Unit,
    onDone: () -> Unit,
    onDiscard: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val preview by rememberUpdatedState(onPreview)
    val discard by rememberUpdatedState(onDiscard)
    val initial = rememberSaveable { selected }
    DisposableEffect(Unit) {
        val back: () -> Boolean = {
            discard?.invoke() ?: preview(initial)
            false
        }
        BackDispatch.sheetBack = back
        onDispose { if (BackDispatch.sheetBack === back) BackDispatch.sheetBack = null }
    }
    SideSheet(
        title = title,
        subtitle = tr("Wird sofort links gezeigt · OK übernehmen · Zurück verwerfen", "Previewed on the left · OK to apply · Back to discard"),
        width = StylePickerWidth,
        scrim = 0.12f,
    ) {
        if (extra != null) {
            androidx.compose.foundation.layout.Column(
                Modifier.onFocusChanged { if (it.hasFocus) preview(initial) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { extra() }
        }
        options.forEachIndexed { i, label ->
            SheetRow(
                title = "${i + 1}   $label",
                value = if (i == selected) "✓" else null,
                autoFocus = i == initial,
                onFocus = { preview(i) },
                onClick = onDone,
            )
        }
    }
}

@Composable
fun PanelChoice(current: dev.hiarni.gimbo.launcher.PanelStyle, onSelect: (dev.hiarni.gimbo.launcher.PanelStyle) -> Unit) {
    SheetSection(tr("Flächen hinter Uhr und Wetter", "Panels behind clock and weather"))
    val styles = dev.hiarni.gimbo.launcher.PanelStyle.entries
    ChoiceRow(styles.map { it.label }, styles.indexOf(current)) { onSelect(styles[it]) }
}

@Composable
fun SizeSheet(config: LauncherConfig, onChange: (LauncherConfig) -> Unit) {
    val latest by rememberUpdatedState(config)
    val change by rememberUpdatedState(onChange)
    SideSheet(
        title = tr("Größe", "Size"),
        subtitle = tr("◀ ▶ verstellt · wirkt sofort", "◀ ▶ adjusts · applies instantly"),
        width = StylePickerWidth,
        scrim = 0.12f,
    ) {
        SheetSection(tr("Kacheln", "Tiles"))
        SliderRow(tr("Kacheln", "Tiles"), config.tileScale, dev.hiarni.gimbo.launcher.TileScaleRange, autoFocus = true) {
            change(latest.copy(tileScale = it))
        }
        val presets = dev.hiarni.gimbo.launcher.TilePresets
        val nearest = presets.indexOfFirst { kotlin.math.abs(it.second - config.tileScale) < 0.026f }
        ChoiceRow(presets.map { it.first }, nearest) { change(latest.copy(tileScale = presets[it].second)) }

        SheetSection(tr("Uhr und Wetter", "Clock and weather"))
        SliderRow(tr("Uhr", "Clock"), config.clockScale, dev.hiarni.gimbo.launcher.HeaderScaleRange) {
            change(latest.copy(clockScale = it))
        }
        SliderRow(tr("Wetter", "Weather"), config.weatherScale, dev.hiarni.gimbo.launcher.HeaderScaleRange) {
            change(latest.copy(weatherScale = it))
        }
        val context = androidx.compose.ui.platform.LocalContext.current
        if (dev.hiarni.gimbo.launcher.Source.entries.any { config.rowOn(it) && dev.hiarni.gimbo.launcher.Shelves.connected(context, it) }) {
            SheetSection(tr("Weiterschauen", "Continue watching"))
            SliderRow(tr("Weiterschauen", "Continue watching"), config.shelfScale, dev.hiarni.gimbo.launcher.ShelfScaleRange) {
                change(latest.copy(shelfScale = it))
            }
        }

        val factor = HomeFit.factor.floatValue
        if (factor < 0.995f) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Hint(trf("Damit alles auf eine Seite passt, zeigt die Startseite Uhr, Wetter und Kacheln gerade mit {0} %.", "To fit everything on one screen, the home screen currently shows clock, weather and tiles at {0}%.", (factor * 100).roundToInt()))
        }
        SheetRow(tr("Alles zurücksetzen", "Reset all")) {
            change(latest.copy(tileScale = 0.9f, clockScale = 1f, weatherScale = 1f, shelfScale = 1f))
        }
    }
}
