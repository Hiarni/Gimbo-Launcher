package dev.hiarni.gimbo.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.JellyfinServer
import dev.hiarni.gimbo.launcher.Kodi
import dev.hiarni.gimbo.launcher.KodiAccount
import dev.hiarni.gimbo.launcher.LauncherConfig
import dev.hiarni.gimbo.launcher.MediaImages
import dev.hiarni.gimbo.launcher.MediaItem
import dev.hiarni.gimbo.launcher.MediaRow
import dev.hiarni.gimbo.launcher.MediaServerClient
import dev.hiarni.gimbo.launcher.Plex
import dev.hiarni.gimbo.launcher.PlexPin
import dev.hiarni.gimbo.launcher.PlexServer
import dev.hiarni.gimbo.launcher.QuickConnectCode
import dev.hiarni.gimbo.launcher.RecentApps
import dev.hiarni.gimbo.launcher.Shelves
import dev.hiarni.gimbo.launcher.Source
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Weiterschauen-Reihen
private val CardWidth = 212.dp
private val CardHeight = 119.dp

@Composable
fun ShelfStack(
    rows: List<MediaRow>,
    scrollable: Boolean,
    focusEnabled: Boolean,
    stateFor: (Source) -> LazyListState,
    firstRequesterFor: (Source) -> FocusRequester,
    requesterFor: (String) -> FocusRequester,
    onUp: () -> Unit,
    onFocused: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
    onFirstRowHeight: (Int) -> Unit = {},
) {
    val scroll = rememberScrollState()
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(hasFocus, scrollable) {
        if (!hasFocus && scroll.value != 0) scroll.animateScrollTo(0)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (scrollable) Modifier.fillMaxHeight().verticalScroll(scroll) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (scrollable) 6.dp else 0.dp),
    ) {
        rows.forEachIndexed { index, row ->
            key(row.source) {
                MediaShelf(
                    row = row,
                    first = index == 0,
                    focusEnabled = focusEnabled,
                    listState = stateFor(row.source),
                    firstRequester = firstRequesterFor(row.source),
                    requesterFor = requesterFor,
                    onUp = onUp,
                    onFocused = onFocused,
                    onClick = onClick,
                    modifier = if (index == 0) Modifier.onSizeChanged { onFirstRowHeight(it.height) } else Modifier,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MediaShelf(
    row: MediaRow,
    first: Boolean,
    focusEnabled: Boolean,
    listState: LazyListState,
    firstRequester: FocusRequester,
    requesterFor: (String) -> FocusRequester,
    onUp: () -> Unit,
    onFocused: (MediaItem) -> Unit,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = row.items
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    val relocation = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val order = items.map { it.key }
    LaunchedEffect(order, hasFocus) {
        val index = order.indexOf(focusedKey)
        runCatching { listState.alignTo(if (hasFocus && index >= 0) index else 0) }
    }
    Column(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(relocation)
            .onFocusChanged {
                if (it.hasFocus && !hasFocus) scope.launch { runCatching { relocation.bringIntoView() } }
                hasFocus = it.hasFocus
            }
            .onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) {
                    val index = items.indexOfFirst { it.key == focusedKey }
                    return@onPreviewKeyEvent if (e.key == Key.DirectionLeft) index == 0 else index == items.lastIndex
                }
                if (e.key != Key.DirectionUp || !first) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyDown) onUp()
                true
            },
    ) {
        Row(Modifier.padding(start = 68.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(row.source.rowTitle, style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Palette.Text))
        }
        KeepInsideEdges(64.dp) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 64.dp, vertical = 12.dp),
            ) {
                itemsIndexed(items, key = { _, entry -> entry.key }) { i, item ->
                    ShelfCard(
                        item,
                        focusEnabled,
                        modifier = Modifier
                            .focusRequester(requesterFor(item.key))
                            .then(if (i == 0) Modifier.focusRequester(firstRequester) else Modifier),
                        onFocused = {
                            focusedKey = item.key
                            onFocused(item)
                        },
                        onClick = { onClick(item) },
                    )
                }
            }
        }
    }
}

private suspend fun LazyListState.alignTo(index: Int) {
    val info = layoutInfo
    val first = info.visibleItemsInfo.firstOrNull()
    val end = info.viewportEndOffset - info.afterContentPadding
    val shown = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (firstVisibleItemScrollOffset == 0 && shown != null && shown.offset >= 0 && shown.offset + shown.size <= end) return
    val target = if (first == null || index == 0) 0 else {
        val space = info.viewportSize.width - info.beforeContentPadding - info.afterContentPadding
        val step = first.size + info.mainAxisItemSpacing
        val fit = ((space + info.mainAxisItemSpacing) / step).coerceAtLeast(1)
        (index - fit + 1).coerceAtLeast(0)
    }
    if (firstVisibleItemIndex != target || firstVisibleItemScrollOffset != 0) scrollToItem(target)
}

@Composable
private fun ShelfCard(
    item: MediaItem,
    focusEnabled: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumb by produceState(item.thumbUrl?.let(MediaImages::peek)?.asImageBitmap(), item.thumbUrl) {
        val url = item.thumbUrl
        value = url?.let { MediaImages.peek(it) ?: MediaImages.image(context, it) }?.asImageBitmap()
    }
    var focused by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "card")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .width(CardWidth)
            .graphicsLayer {
                val s = 1f + 0.1f * p
                scaleX = s
                scaleY = s
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.3f)
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusProperties { canFocus = focusEnabled }
            .tvClickable(enabled = focusEnabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(CardWidth, CardHeight)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.06f))
                .border(3.dp, Color.White.copy(alpha = 0.95f * p), shape),
        ) {
            thumb?.let { Image(it, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f * (1f - p))))
            if (item.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(Modifier.fillMaxWidth(item.progress).height(4.dp).background(Color.White))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            item.title,
            style = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Palette.Text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            item.subtitle,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, color = if (focused) Palette.TextDim else Palette.TextFaint),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Titel-Hintergrund
@Composable
fun MediaBackdrop(item: MediaItem?) {
    val context = LocalContext.current
    val url = item?.takeIf { it.source != Source.Recent }?.let { it.backdropUrl ?: it.thumbUrl }
    val image by produceState(url?.let(MediaImages::peekBackdrop)?.asImageBitmap(), url) {
        value = url?.let(MediaImages::peekBackdrop)?.asImageBitmap()
        if (url != null) MediaImages.backdrop(context, url)?.let { if (value?.asAndroidBitmap() !== it) value = it.asImageBitmap() }
    }
    val alpha by animateFloatAsState(if (url != null && image != null) 1f else 0f, tween(700), label = "mediaBackdrop")
    val shown = image ?: return
    if (alpha <= 0f) return
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
        Crossfade(targetState = shown, animationSpec = tween(600), label = "mediaImage") { img ->
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Palette.Base.copy(alpha = 0.62f), Palette.Base.copy(alpha = 0.42f), Palette.Base.copy(alpha = 0.88f)),
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Palette.Base.copy(alpha = 0.6f)),
                        center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
        )
    }
}

// Quelle: Status, Reihenfolge, Abmelden
fun sourceStatus(context: android.content.Context, config: LauncherConfig, source: Source): String = when {
    source == Source.Recent && !RecentApps.granted(context) -> tr("Freigabe fehlt", "Access missing")
    !Shelves.connected(context, source) -> tr("Nicht verbunden", "Not connected")
    !config.rowOn(source) -> tr("Aus", "Off")
    source == Source.Recent -> tr("An", "On")
    else -> Shelves.serverName(context, source).orEmpty()
}

@Composable
fun SourceSheet(
    source: Source,
    config: LauncherConfig,
    onChange: (LauncherConfig) -> Unit,
    onConnect: () -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    Shelves.revision
    var recheck by remember { mutableStateOf(0) }
    val connected = remember(recheck, Shelves.revision) { Shelves.connected(context, source) }
    val position = config.rowOrder.indexOf(source)
    SideSheet(title = source.label, subtitle = sourceStatus(context, config, source)) {
        when {
            source == Source.Recent && !connected -> {
                Note(tr(
                    "Zeigt die zuletzt geöffneten Apps, zum Beispiel Netflix, Disney+ oder Prime Video. Dafür braucht der Launcher einmalig Zugriff auf die Nutzungsstatistik:",
                    "Shows the apps you opened last, for example Netflix, Disney+ or Prime Video. For this the launcher needs one-time access to usage statistics:",
                ))
                Note("adb shell appops set dev.hiarni.gimbo.launcher GET_USAGE_STATS allow", mono = true)
                SheetRow(tr("Erneut prüfen", "Check again"), autoFocus = true) {
                    recheck++
                    Shelves.changed()
                }
                SheetRow(tr("Systemeinstellung öffnen", "Open system setting")) { RecentApps.openAccessSettings(context) }
            }
            !connected -> {
                SheetRow(tr("Verbinden", "Connect"), autoFocus = true, onClick = onConnect)
                Note(sourceHint(source))
            }
            else -> {
                ToggleRow(tr("In Weiterschauen zeigen", "Show in Continue watching"), config.rowOn(source)) {
                    onChange(config.withRowOn(source, it))
                }
            }
        }
        SheetSection(tr("Reihenfolge", "Order"))
        SheetRow(tr("Nach oben", "Move up"), value = "${position + 1} / ${config.rowOrder.size}") { onChange(config.moveRow(source, -1)) }
        SheetRow(tr("Nach unten", "Move down")) { onChange(config.moveRow(source, 1)) }
        if (connected && source != Source.Recent) {
            SheetSection(tr("Verbindung", "Connection"))
            SheetRow(tr("Server", "Server"), value = Shelves.serverName(context, source), onClick = onConnect)
            SheetRow(tr("Abmelden", "Sign out"), danger = true, onClick = onSignOut)
            Note(sourceHint(source))
        }
    }
}

private fun sourceHint(source: Source): String = when (source) {
    Source.Jellyfin -> tr("Öffnet Titel direkt in der Jellyfin-App.", "Opens titles directly in the Jellyfin app.")
    Source.Emby -> tr("Öffnet die Emby-App. Die Liste kommt vom eigenen Emby-Server.", "Opens the Emby app. The list comes from your own Emby server.")
    Source.Plex -> tr("Anmeldung über plex.tv/link. Öffnet Titel in der Plex-App.", "Sign in via plex.tv/link. Opens titles in the Plex app.")
    Source.Kodi -> tr(
        "Kodi muss die Fernsteuerung erlauben: Einstellungen › Dienste › Steuerung › Fernsteuerung über HTTP. Die Liste aktualisiert sich, solange Kodi läuft.",
        "Kodi must allow remote control: Settings › Services › Control › Allow remote control via HTTP. The list updates while Kodi is running.",
    )
    Source.Recent -> ""
}

@Composable
private fun Note(text: String, mono: Boolean = false) {
    if (text.isEmpty()) return
    BasicText(text, style = RowValue.copy(color = Palette.TextDim, fontFamily = if (mono) Mono else Inter, fontSize = if (mono) 15.sp else RowValue.fontSize))
}

// Jellyfin und Emby verbinden
@Composable
fun ServerSetupSheet(client: MediaServerClient, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = client.source.title

    var servers by remember { mutableStateOf<List<JellyfinServer>?>(null) }
    var server by remember { mutableStateOf<String?>(null) }
    var serverName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<QuickConnectCode?>(null) }
    var usePassword by remember { mutableStateOf(false) }
    var expired by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { servers = client.discover(context) }

    LaunchedEffect(code) {
        val qc = code ?: return@LaunchedEffect
        val address = server ?: return@LaunchedEffect
        val deadline = System.currentTimeMillis() + 9 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            delay(2_000)
            if (client.quickConnectApproved(address, qc.secret)) {
                if (client.quickConnectFinish(context, address, serverName, qc.secret) != null) onConnected()
                else error = tr("Anmeldung fehlgeschlagen. Bitte noch einmal versuchen.", "Sign-in failed. Please try again.")
                return@LaunchedEffect
            }
        }
        code = null
        expired = true
    }

    fun newCode() {
        val address = server ?: return
        scope.launch {
            busy = true
            error = null
            expired = false
            code = client.quickConnectStart(context, address)
            if (code == null) usePassword = true
            busy = false
        }
    }

    fun select(address: String, label: String?) {
        scope.launch {
            busy = true
            error = null
            val found = label ?: client.check(address)
            if (found == null) {
                error = trf("Unter {0} antwortet kein {1}-Server.", "No {1} server responds at {0}.", address, name)
            } else {
                server = address
                serverName = found
                code = client.quickConnectStart(context, address)
                usePassword = code == null
            }
            busy = false
        }
    }

    val subtitle = when {
        server == null -> tr("Server im Heimnetz wählen", "Choose a server on your home network")
        usePassword -> trf("{0} · mit Benutzer und Passwort", "{0} · with username and password", serverName)
        else -> serverName
    }

    SideSheet(title = trf("{0} verbinden", "Connect {0}", name), subtitle = subtitle) {
        error?.let { BasicText(it, style = RowValue.copy(color = Color(0xFFFF8A80))) }

        when {
            busy -> BasicText(tr("Einen Moment …", "One moment …"), style = RowValue.copy(color = Palette.TextDim))

            server == null -> {
                val list = servers
                if (list == null) {
                    BasicText(trf("Suche {0} im Heimnetz …", "Searching for {0} on your home network …", name), style = RowValue.copy(color = Palette.TextDim))
                } else {
                    list.forEachIndexed { i, s ->
                        SheetRow(s.name, value = s.address.substringAfter("://"), autoFocus = i == 0) { select(s.address, s.name) }
                    }
                    if (list.isEmpty()) {
                        BasicText(tr("Kein Server gefunden. Adresse selbst eingeben:", "No server found. Enter the address manually:"), style = RowValue.copy(color = Palette.TextDim))
                    }
                }
                NameRow(tr("Adresse eingeben", "Enter address"), value = tr("z. B. 192.168.178.20", "e.g. 192.168.178.20"), initialText = "") { input ->
                    if (input.isNotBlank() && !input.startsWith(tr("z. B.", "e.g."))) select(client.normalize(input), null)
                }
            }

            expired && !usePassword -> {
                BasicText(tr("Der Code ist abgelaufen.", "The code has expired."), style = RowValue.copy(color = Palette.TextDim))
                SheetRow(tr("Neuen Code anzeigen", "Show new code"), autoFocus = true) { newCode() }
                SheetRow(tr("Mit Passwort anmelden", "Sign in with password")) {
                    expired = false
                    usePassword = true
                }
            }

            !usePassword && code != null -> {
                BasicText(
                    tr("Code in Jellyfin am Handy oder PC eingeben: Einstellungen › Quick Connect", "Enter the code in Jellyfin on your phone or PC: Settings › Quick Connect"),
                    style = RowValue.copy(color = Palette.TextDim),
                )
                BigCode(code!!.code.chunked(3).joinToString(" "))
                BasicText(tr("Wartet auf Bestätigung …", "Waiting for confirmation …"), style = RowValue.copy(color = Palette.TextFaint, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                SheetRow(tr("Stattdessen mit Passwort anmelden", "Sign in with password instead"), autoFocus = true) {
                    code = null
                    usePassword = true
                }
            }

            else -> {
                NameRow(tr("Benutzer", "Username"), value = user.ifEmpty { tr("Name eingeben", "Enter name") }, initialText = user, startEditing = user.isEmpty()) { user = it.takeUnless { v -> v == tr("Name eingeben", "Enter name") } ?: "" }
                NameRow(tr("Passwort", "Password"), value = password, initialText = password, secret = true) { password = it }
                SheetRow(tr("Anmelden", "Sign in"), autoFocus = remember { user.isNotEmpty() }) {
                    val address = server ?: return@SheetRow
                    scope.launch {
                        busy = true
                        error = null
                        val ok = client.passwordLogin(context, address, serverName, user, password) != null
                        busy = false
                        error = when {
                            ok -> null
                            client.lastLoginCode == 401 || client.lastLoginCode == 403 ->
                                tr("Benutzer oder Passwort stimmt nicht.", "Username or password is incorrect.")
                            else -> tr("Server antwortet nicht. Bitte später erneut versuchen.", "The server isn't responding. Please try again later.")
                        }
                        if (ok) onConnected()
                    }
                }
            }
        }
    }
}

@Composable
private fun BigCode(text: String) {
    BasicText(
        text,
        style = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 64.sp,
            letterSpacing = 6.sp,
            color = Palette.Text,
            textAlign = TextAlign.Center,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
    )
}

// Plex verbinden
@Composable
fun PlexSetupSheet(onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf<PlexPin?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var servers by remember { mutableStateOf<List<PlexServer>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    fun choose(server: PlexServer) {
        val t = token ?: return
        scope.launch {
            busy = true
            error = null
            if (Plex.connect(context, t, server)) onConnected()
            else error = trf("{0} ist gerade nicht erreichbar.", "{0} can't be reached right now.", server.name)
            busy = false
        }
    }

    LaunchedEffect(attempt) {
        error = null
        pin = Plex.startPin(context)
        val p = pin ?: run {
            error = tr("Keine Verbindung zu plex.tv", "Can't reach plex.tv")
            return@LaunchedEffect
        }
        val deadline = System.currentTimeMillis() + 15 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            delay(2_000)
            val t = Plex.pinToken(context, p) ?: continue
            token = t
            busy = true
            val list = Plex.servers(context, t)
            busy = false
            servers = list
            when {
                list.isEmpty() -> error = tr("Kein Plex-Server in diesem Konto gefunden.", "No Plex server found in this account.")
                list.size == 1 -> choose(list.first())
            }
            return@LaunchedEffect
        }
        pin = null
        error = tr("Der Code ist abgelaufen.", "The code has expired.")
    }

    SideSheet(title = trf("{0} verbinden", "Connect {0}", "Plex"), subtitle = tr("Anmeldung mit dem Plex-Konto", "Sign in with your Plex account")) {
        error?.let { BasicText(it, style = RowValue.copy(color = Color(0xFFFF8A80))) }
        val list = servers
        when {
            busy -> BasicText(tr("Einen Moment …", "One moment …"), style = RowValue.copy(color = Palette.TextDim))
            list != null && list.size > 1 -> {
                BasicText(tr("Server wählen:", "Choose a server:"), style = RowValue.copy(color = Palette.TextDim))
                list.forEachIndexed { i, s -> SheetRow(s.name, autoFocus = i == 0) { choose(s) } }
            }
            pin != null && token == null -> {
                BasicText(
                    tr("Am Handy oder PC plex.tv/link öffnen, anmelden und diesen Code eingeben:", "On your phone or PC, open plex.tv/link, sign in and enter this code:"),
                    style = RowValue.copy(color = Palette.TextDim),
                )
                BigCode(pin!!.code)
                BasicText(tr("Wartet auf Bestätigung …", "Waiting for confirmation …"), style = RowValue.copy(color = Palette.TextFaint, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            }
            pin == null && error == null -> BasicText(tr("Einen Moment …", "One moment …"), style = RowValue.copy(color = Palette.TextDim))
        }
        if (error != null) SheetRow(tr("Erneut versuchen", "Try again"), autoFocus = true) {
            token = null
            servers = null
            attempt++
        }
    }
}

// Kodi verbinden
@Composable
fun KodiSetupSheet(onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = remember { Kodi.draft(context) }
    var host by remember { mutableStateOf(start.host) }
    var port by remember { mutableStateOf(start.port.toString()) }
    var user by remember { mutableStateOf(start.user) }
    var password by remember { mutableStateOf(start.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SideSheet(title = trf("{0} verbinden", "Connect {0}", "Kodi"), subtitle = tr("Kodi auf diesem Fire TV oder im Heimnetz", "Kodi on this Fire TV or on your home network")) {
        error?.let { BasicText(it, style = RowValue.copy(color = Color(0xFFFF8A80))) }
        NameRow(tr("Adresse", "Address"), value = host, initialText = host) { host = it.trim().ifEmpty { "127.0.0.1" } }
        NameRow(tr("Port", "Port"), value = port, initialText = port) { port = it.filter(Char::isDigit).ifEmpty { "8080" } }
        NameRow(tr("Benutzer", "Username"), value = user, initialText = user) { user = it.trim() }
        NameRow(tr("Passwort", "Password"), value = password, initialText = password, secret = true) { password = it }
        SheetRow(if (busy) tr("Einen Moment …", "One moment …") else tr("Verbinden", "Connect"), autoFocus = true) {
            if (busy) return@SheetRow
            val account = KodiAccount(host, port.toIntOrNull() ?: 8080, user, password)
            scope.launch {
                busy = true
                error = null
                val result = Kodi.check(account)
                busy = false
                when (result) {
                    200 -> {
                        Kodi.save(context, account)
                        onConnected()
                    }
                    401 -> error = tr("Benutzer oder Passwort stimmt nicht.", "Username or password is incorrect.")
                    else -> error = tr("Kodi antwortet nicht. Läuft Kodi, und ist die Fernsteuerung über HTTP erlaubt?", "Kodi isn't responding. Is Kodi running, and is remote control via HTTP allowed?")
                }
            }
        }
        Note(sourceHint(Source.Kodi))
    }
}
