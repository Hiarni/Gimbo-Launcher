package dev.hiarni.gimbo.launcher.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.State
import android.graphics.Bitmap
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.hiarni.gimbo.launcher.tr
import dev.hiarni.gimbo.launcher.trf

// Eingabe und Fokus
internal object OkGuard {
    var swallow = false
}

object BackDispatch {
    var handler: (() -> Unit)? = null

    var sheetBack: (() -> Boolean)? = null
}

internal object TextEditing {
    var active = false
}

enum class HomeSheet { AllApps, Settings }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun KeepInsideEdges(padding: Dp, content: @Composable () -> Unit) {
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { padding.toPx() }
    val spec = remember(px) {
        object : androidx.compose.foundation.gestures.BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val start = px
                val end = containerSize - px
                return when {
                    size > end - start || offset < start -> offset - start
                    offset + size > end -> offset + size - end
                    else -> 0f
                }
            }
        }
    }
    CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides spec, content = content)
}

object HomeRequests {
    var sheet by mutableStateOf<HomeSheet?>(null)
    var focusRescue by mutableStateOf(0)
    @Volatile var at = 0L
}

fun Modifier.tvClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onPress: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    var longFired by remember { mutableStateOf(false) }
    var downSeen by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val press by rememberUpdatedState(onPress)
    val view = LocalView.current
    val sounds = LocalUiSounds.current
    this
        .onKeyEvent { e ->
            when (e.key) {
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (OkGuard.swallow) return@onKeyEvent true
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            longFired = false
                            downSeen = true
                            press?.invoke(true)
                        } else if (!longFired) {
                            long?.let {
                                longFired = true
                                OkGuard.swallow = true
                                press?.invoke(false)
                                if (sounds) view.playSoundEffect(SoundEffectConstants.CLICK)
                                it()
                            }
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        press?.invoke(false)
                        if (OkGuard.swallow) {
                            OkGuard.swallow = false
                        } else if (!longFired && downSeen && !e.nativeKeyEvent.isCanceled) {
                            if (sounds) view.playSoundEffect(SoundEffectConstants.CLICK)
                            click()
                        }
                        longFired = false
                        downSeen = false
                        true
                    }
                    else -> false
                }
                Key.Menu -> {
                    val l = long ?: return@onKeyEvent false
                    if (e.type == KeyEventType.KeyUp) {
                        if (sounds) view.playSoundEffect(SoundEffectConstants.CLICK)
                        l()
                    }
                    true
                }
                else -> false
            }
        }
        .focusProperties { canFocus = enabled }
        .focusable(enabled)
}

val LocalUiSounds = compositionLocalOf { true }

fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<IntOffset?>(null) }
    val offset = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    this
        .onPlaced { coords ->
            val now = coords.positionInParent().round()
            val before = last
            if (before != null && before != now) {
                scope.launch {
                    offset.snapTo(offset.value + (before - now))
                    offset.animateTo(IntOffset.Zero, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
            }
            last = now
        }
        .offset { offset.value }
}

@Composable
fun rememberAutoFocus(enabled: Boolean): FocusRequester {
    val requester = remember { FocusRequester() }
    var done by rememberSaveable { mutableStateOf(false) }
    if (enabled && !done) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
            done = true
        }
    }
    return requester
}

class SheetMemory(initialKey: String?, private val save: (String) -> Unit) {
    var restoreKey: String? = initialKey
    fun focused(key: String) = save(key)
}

val LocalSheetMemory = staticCompositionLocalOf<SheetMemory?> { null }

fun Modifier.rememberedFocus(key: String): Modifier = composed {
    val memory = LocalSheetMemory.current ?: return@composed Modifier
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (memory.restoreKey == key) {
            memory.restoreKey = null
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    Modifier
        .focusRequester(requester)
        .onFocusChanged { if (it.isFocused) memory.focused(key) }
}

// Seitenleiste
private val SheetBg = Color(0xFA121520)
private val RowShape = RoundedCornerShape(16.dp)

internal val SheetTitle = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 30.sp,
    letterSpacing = (-0.5).sp, color = Palette.Text,
)
internal val SheetLabel = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 15.sp,
    letterSpacing = 0.6.sp, color = Palette.TextFaint,
)
internal val RowText = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 21.sp)
internal val RowValue = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 18.sp)

internal val StylePickerWidth = 420.dp

@Composable
fun SideSheet(
    title: String,
    subtitle: String? = null,
    width: Dp = 540.dp,
    scrollable: Boolean = true,
    scrim: Float = 0.45f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(width)
                .clip(RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp))
                .background(SheetBg)
                .padding(horizontal = 36.dp, vertical = 44.dp),
        ) {
            BasicText(title, style = SheetTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                BasicText(subtitle, style = RowValue.copy(color = Palette.TextDim), maxLines = 2)
            }
            Spacer(Modifier.height(28.dp))
            val fallback = remember { FocusRequester() }
            var hasFocus by remember { mutableStateOf(false) }
            LaunchedEffect(hasFocus) {
                while (!hasFocus) {
                    delay(180)
                    if (!hasFocus) runCatching { fallback.requestFocus() }
                }
            }
            var lastKey by rememberSaveable { mutableStateOf<String?>(null) }
            val memory = remember { SheetMemory(lastKey) { lastKey = it } }
            CompositionLocalProvider(LocalSheetMemory provides memory) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onFocusChanged { hasFocus = it.hasFocus }
                    .focusRequester(fallback)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
            }
        }
    }
}

// Zeilen und Eingaben
@Composable
fun SheetSection(text: String) {
    Spacer(Modifier.height(10.dp))
    BasicText(text.uppercase(), style = SheetLabel, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun focusProgress(focused: Boolean) =
    animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "focus").value

@Composable
fun SheetRow(
    title: String,
    value: String? = null,
    autoFocus: Boolean = false,
    danger: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    requester: FocusRequester? = null,
    onFocus: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focusAction by rememberUpdatedState(onFocus)
    val p = focusProgress(focused)
    val fg = lerp(if (danger) Color(0xFFFF8A80) else Palette.Text, Palette.Base, p)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .focusRequester(rememberAutoFocus(autoFocus))
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .rememberedFocus("row:$title")
            .clip(RowShape)
            .background(lerp(Color.White.copy(alpha = 0.045f), Color.White, p))
            .onFocusChanged {
                if (it.isFocused && !focused) focusAction?.invoke()
                focused = it.isFocused
            }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        BasicText(
            title,
            style = RowText.copy(color = fg),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            BasicText(
                value,
                style = RowValue.copy(color = lerp(Palette.TextDim, Palette.Base.copy(alpha = 0.7f), p)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p = focusProgress(focused)
    val k = animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "toggle").value
    Row(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .rememberedFocus("toggle:$title")
            .clip(RowShape)
            .background(lerp(Color.White.copy(alpha = 0.045f), Color.White, p))
            .onFocusChanged { focused = it.isFocused }
            .tvClickable { onToggle(!checked) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(title, style = RowText.copy(color = lerp(Palette.Text, Palette.Base, p)), modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(width = 52.dp, height = 30.dp)
                .clip(CircleShape)
                .background(lerp(lerp(Color.White.copy(alpha = 0.18f), Color(0xFF8E4FD6), k), lerp(Color(0x33000000), Color(0xFF8E4FD6), k), p)),
        ) {
            Box(
                Modifier
                    .padding(4.dp)
                    .graphicsLayer { translationX = k * 22.dp.toPx() }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun ChoiceRow(options: List<String>, selected: Int, autoFocus: Boolean = false, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            var focused by remember { mutableStateOf(false) }
            val p = focusProgress(focused)
            val active = i == selected
            val bg = if (active) Color(0xFF8E4FD6) else Color.White.copy(alpha = 0.06f)
            Box(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .focusRequester(rememberAutoFocus(autoFocus && active))
                    .rememberedFocus("choice:${options.joinToString("|")}:$i")
                    .graphicsLayer { val s = 1f + 0.05f * p; scaleX = s; scaleY = s }
                    .clip(RowShape)
                    .background(lerp(bg, Color.White, p))
                    .onFocusChanged { focused = it.isFocused }
                    .tvClickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    label,
                    style = RowValue.copy(
                        fontWeight = FontWeight.Medium,
                        color = lerp(if (active) Color.White else Palette.TextDim, Palette.Base, p),
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ColorRow(colors: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        colors.forEach { c ->
            var focused by remember { mutableStateOf(false) }
            val p = focusProgress(focused)
            Box(
                Modifier
                    .size(42.dp)
                    .rememberedFocus("color:$c")
                    .graphicsLayer { val s = 1f + 0.18f * p; scaleX = s; scaleY = s }
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        width = if (c == selected) 3.dp else 0.dp,
                        color = Color.White.copy(alpha = if (c == selected) 0.95f else 0f),
                        shape = CircleShape,
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.9f * p), CircleShape)
                    .onFocusChanged { focused = it.isFocused }
                    .tvClickable { onSelect(c) },
            )
        }
    }
}

@Composable
fun NameRow(
    title: String,
    value: String,
    startEditing: Boolean = false,
    initialText: String = value,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(startEditing) }
    val rowRequester = remember { FocusRequester() }
    var refocus by remember { mutableStateOf(false) }
    DisposableEffect(editing) {
        if (editing) {
            TextEditing.active = true
            onDispose { TextEditing.active = false }
        } else {
            onDispose { }
        }
    }
    if (!editing) {
        if (refocus) {
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { rowRequester.requestFocus() }
                refocus = false
            }
        }
        SheetRow(
            title = title,
            value = if (secret && value.isNotEmpty()) "••••••" else value,
            requester = rowRequester,
        ) { editing = true }
        return
    }
    var text by remember { mutableStateOf(initialText) }
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
        keyboard?.show()
    }
    var finished by remember { mutableStateOf(false) }
    val finish = { backToRow: Boolean ->
        if (!finished) {
            finished = true
            keyboard?.hide()
            onChange(if (secret) text else text.trim().ifEmpty { value })
            refocus = backToRow
            editing = false
        }
    }
    var hadFocus by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RowShape)
            .background(Color.White)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = RowText.copy(color = Palette.Base),
            cursorBrush = SolidColor(Palette.Base),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardActions = KeyboardActions(onDone = { finish(true) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(requester)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true
                    else if (hadFocus) finish(false)
                }
                .onKeyEvent { e ->
                    if (e.key == Key.Back && e.type == KeyEventType.KeyUp) {
                        finish(true)
                        true
                    } else {
                        false
                    }
                },
        )
    }
}

@Composable
fun rememberBitmap(
    key: Any?,
    peek: () -> Bitmap?,
    load: () -> Bitmap?,
): State<ImageBitmap?> {
    val generation = dev.hiarni.gimbo.launcher.ImageCache.generation.intValue
    val state = remember(key) { mutableStateOf<ImageBitmap?>(peek()?.asImageBitmap()) }
    var shownGeneration by remember(key) { mutableStateOf(generation) }
    LaunchedEffect(key, generation) {
        if (state.value == null || shownGeneration != generation) {
            val bitmap = withContext(Dispatchers.IO) { load() }
            if (bitmap != null) state.value = bitmap.asImageBitmap()
            shownGeneration = generation
        }
    }
    return state
}

@Composable
fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 0.05f,
    autoFocus: Boolean = false,
    onChange: (Float) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val p = focusProgress(focused)
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val fg = lerp(Palette.Text, Palette.Base, p)
    Column(
        Modifier
            .fillMaxWidth()
            .focusRequester(rememberAutoFocus(autoFocus))
            .rememberedFocus("slider:$title")
            .clip(RowShape)
            .background(lerp(Color.White.copy(alpha = 0.045f), Color.White, p))
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { e ->
                val delta = when (e.key) {
                    Key.DirectionLeft -> -step
                    Key.DirectionRight -> step
                    else -> return@onKeyEvent false
                }
                if (e.type == KeyEventType.KeyDown) {
                    val next = (kotlin.math.round((current + delta) * 100f) / 100f).coerceIn(range.start, range.endInclusive)
                    if (next != current) change(next)
                }
                true
            }
            .focusable()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(title, style = RowText.copy(color = fg), modifier = Modifier.weight(1f))
            BasicText(
                trf("◀  {0} %  ▶", "◀  {0}%  ▶", kotlin.math.round(value * 100).toInt()),
                style = RowValue.copy(color = lerp(Palette.TextDim, Palette.Base.copy(alpha = 0.7f), p)),
            )
        }
        Spacer(Modifier.height(12.dp))
        val accent = Color(0xFF8E4FD6)
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val f = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
            val y = size.height / 2
            val track = 6.dp.toPx()
            val r = androidx.compose.ui.geometry.CornerRadius(track / 2)
            drawRoundRect(
                lerp(Color.White.copy(alpha = 0.12f), Palette.Base.copy(alpha = 0.15f), p),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - track / 2),
                size = androidx.compose.ui.geometry.Size(size.width, track), cornerRadius = r,
            )
            drawRoundRect(
                lerp(Color.White.copy(alpha = 0.55f), accent, p),
                topLeft = androidx.compose.ui.geometry.Offset(0f, y - track / 2),
                size = androidx.compose.ui.geometry.Size(size.width * f, track), cornerRadius = r,
            )
            if (p > 0f) {
                drawCircle(Color.White, radius = 10.dp.toPx() * p, center = androidx.compose.ui.geometry.Offset(size.width * f, y))
                drawCircle(accent, radius = 7.dp.toPx() * p, center = androidx.compose.ui.geometry.Offset(size.width * f, y))
            }
        }
    }
}
