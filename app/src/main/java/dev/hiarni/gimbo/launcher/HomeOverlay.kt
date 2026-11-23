package dev.hiarni.gimbo.launcher

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.PausableMonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hiarni.gimbo.launcher.ui.HomeRequests
import dev.hiarni.gimbo.launcher.ui.HomeSheet
import dev.hiarni.gimbo.launcher.ui.LauncherScreen
import dev.hiarni.gimbo.launcher.ui.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// Overlay über der Amazon-Startseite
object HomeOverlay {
    private const val MAX_SHOW_MS = 7_000L
    private const val REPEAT_SHOW_MS = 1_200L

    private val main = Handler(Looper.getMainLooper())
    private var root: FrameLayout? = null
    private var compose: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var clock: PausableMonotonicFrameClock? = null
    private var attached = false
    private val autoHide = Runnable { hide() }

    private var shownAt = 0L
    private var launching = false

    private val config = mutableStateOf(LauncherConfig(emptyList()))
    private val weather = mutableStateOf<Weather?>(null)
    private val rows = mutableStateOf<List<MediaRow>>(emptyList())
    private val shown = mutableIntStateOf(0)

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (!canShow(context)) return
        if (attached) {
            main.removeCallbacks(autoHide)
            main.postDelayed(autoHide, if (MainActivity.visible) REPEAT_SHOW_MS else MAX_SHOW_MS)
            return
        }
        shownAt = SystemClock.uptimeMillis()
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, MAX_SHOW_MS)
        launching = false

        val app = context.applicationContext
        val cfg = TileStore.load(app)
        config.value = cfg
        weather.value = if (cfg.weatherEnabled) WeatherRepository.cached(app, cfg.weatherPlace, cfg.fahrenheit) else null
        Shelves.restore(app)
        rows.value = Shelves.rows(app, cfg)
        shown.intValue++

        val view = root ?: build(app).also { root = it }
        try {
            (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, layoutParams())
            attached = true
            clock?.resume()
            owner?.resume()
            compose?.requestFocus()
        } catch (_: Exception) {
            attached = false
        }
    }

    private fun build(app: Context): FrameLayout {
        val lifecycle = OverlayOwner().also { owner = it }
        val ui = AndroidUiDispatcher.Main
        val frames = PausableMonotonicFrameClock(ui[MonotonicFrameClock]!!).also { clock = it }
        val recomposeContext = ui + frames
        val recomposer = Recomposer(recomposeContext)
        CoroutineScope(recomposeContext).launch(start = CoroutineStart.UNDISPATCHED) {
            recomposer.runRecomposeAndApplyChanges()
        }
        val view = ComposeView(app).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle))
            setParentCompositionContext(recomposer)
            setBackgroundColor(Palette.Base.toArgb())
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setContent {
                LauncherScreen(
                    config = config.value,
                    weather = weather.value,
                    initialTileId = AppLauncher.lastFocusedTileId ?: AppLauncher.lastTileId(app),
                    showHomeSetup = false,
                    onConfigChange = {},
                    onLaunch = { tile -> launchOnce(app, tile.label) { AppLauncher.launch(app, tile) } },
                    onOpenFireTvSettings = { launchOnce(app, tr("Einstellungen", "Settings")) { AppLauncher.openSettings(app) } },
                    onOpenHomeSetup = {},
                    editable = false,
                    refreshKey = shown.intValue,
                    rows = rows.value,
                    onOpenItem = { item -> launchOnce(app, item.title) { Shelves.open(app, item) } },
                    animateIntro = false,
                    onOpenHomeSheet = { sheet -> openInLauncher(app, sheet) },
                )
            }
        }
        compose = view
        return object : FrameLayout(app) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
                return super.dispatchKeyEvent(event)
            }
        }.apply {
            setBackgroundColor(Palette.Base.toArgb())
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    private fun launchOnce(app: Context, label: String, start: () -> Unit) {
        if (launching) return
        launching = true
        if (shownAt + 5_000 - SystemClock.uptimeMillis() > 700) {
            Toast.makeText(app, trf("{0} startet …", "Starting {0} …", label), Toast.LENGTH_SHORT).show()
        }
        start()
        hideAfterLaunch()
    }

    private fun openInLauncher(app: Context, sheet: HomeSheet) {
        if (launching) return
        launching = true
        HomeRequests.at = SystemClock.uptimeMillis()
        HomeRequests.sheet = sheet
        if (!MainActivity.visible) HomeWatchService.openLauncher(app)
        hideSoon(if (MainActivity.visible) 50L else 400L)
    }

    fun hideAfterLaunch() {
        if (!attached) return
        hideSoon((shownAt + 5_300 - SystemClock.uptimeMillis()).coerceAtLeast(300L))
    }

    fun hideSoon(delayMs: Long = 250L) {
        if (!attached) return
        main.removeCallbacks(autoHide)
        main.postDelayed(autoHide, delayMs)
    }

    fun hide() {
        main.removeCallbacks(autoHide)
        val view = root ?: return
        if (!attached) return
        attached = false
        runCatching {
            (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeViewImmediate(view)
        }
        owner?.pause()
        clock?.pause()
    }
}

// Lebenszyklus des Overlays
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun resume() {
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        registry.currentState = Lifecycle.State.CREATED
    }
}
