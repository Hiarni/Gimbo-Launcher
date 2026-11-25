package dev.hiarni.gimbo.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.content.IntentFilter
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.hiarni.gimbo.launcher.ui.BackDispatch
import dev.hiarni.gimbo.launcher.ui.HomeRequests
import dev.hiarni.gimbo.launcher.ui.LauncherScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var config by mutableStateOf(LauncherConfig(emptyList()))
    private var homeSetupMissing by mutableStateOf(false)
    private var weather by mutableStateOf<Weather?>(null)
    private var resumeCount by mutableIntStateOf(0)

    private var online by mutableStateOf(true)

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var homePresses by mutableIntStateOf(0)

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionCallback?.invoke(granted)
        permissionCallback = null
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { runOnUiThread { online = true } }
        override fun onLost(network: Network) { runOnUiThread { online = isOnline() } }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let(ImageCache::forgetPackage)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
        if (visible) HomeOverlay.hideSoon()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in NavKeyCodes && currentFocus == null) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                findViewById<ViewGroup>(android.R.id.content).getChildAt(0)?.requestFocus()
                HomeRequests.focusRescue++
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        created = true
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BackDispatch.handler?.invoke()
            }
        })

        config = TileStore.load(this)
        Shelves.restore(this)
        Updater.restore(this)
        refresh()
        if (Build.VERSION.SDK_INT >= 24) {
            runCatching { connectivity().registerDefaultNetworkCallback(networkCallback) }
        }
        weather = WeatherRepository.cached(this, config.weatherPlace, config.fahrenheit)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    refreshWeather()
                    refreshShelves()
                    if (config.autoUpdate && online && Updater.dueForCheck(this@MainActivity)) Updater.check(this@MainActivity)
                    delay(if (weather == null) 2 * 60_000L else 5 * 60_000L)
                }
            }
        }
        val lastId = AppLauncher.lastTileId(this)

        setContent {
            LauncherScreen(
                config = config,
                weather = weather?.takeIf {
                    config.weatherEnabled && it.id == WeatherRepository.idOf(config.weatherPlace, config.fahrenheit) &&
                        System.currentTimeMillis() - it.fetchedAt < 6 * 60 * 60 * 1000L
                },
                initialTileId = lastId,
                showHomeSetup = homeSetupMissing,
                onConfigChange = {
                    val placeChanged = it.weatherPlace != config.weatherPlace || it.fahrenheit != config.fahrenheit ||
                        (it.weatherEnabled && !config.weatherEnabled)
                    val reload = it.language != config.language || it.rowOff != config.rowOff
                    config = it
                    TileStore.save(this, it)
                    if (reload) lifecycleScope.launch { refreshShelves() }
                    if (placeChanged) {
                        weather = WeatherRepository.cached(this, it.weatherPlace, it.fahrenheit)
                        lifecycleScope.launch { refreshWeather() }
                    }
                },
                onLaunch = { AppLauncher.launch(this, it) },
                onOpenFireTvSettings = { AppLauncher.openSettings(this) },
                onOpenHomeSetup = ::showHomeSetupHint,
                requestImageAccess = ::requestImageAccess,
                refreshKey = resumeCount,
                homeKey = homePresses,
                online = online,
                onLaunchPackage = { AppLauncher.launchPackage(this, it) },
                onExport = ::exportBackup,
                onImport = ::importBackup,
                rows = Shelves.rows(this, config, Shelves.items).also { Shelves.revision },
                onOpenItem = { item ->
                    if (!Shelves.open(this, item)) toast(trf("{0} ist nicht installiert", "{0} is not installed", if (item.source == Source.Recent) item.title else item.source.title))
                },
                onSourceConnected = { source ->
                    config = config.withRowOn(source, true).also { TileStore.save(this, it) }
                    Shelves.changed()
                    lifecycleScope.launch { refreshShelves() }
                    toast(trf("Mit {0} verbunden", "Connected to {0}", source.title))
                },
                onSourceSignOut = { source -> Shelves.signOut(this, source) },
                onKeepScreenOn = { keep ->
                    if (keep) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                },
            )
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(packageReceiver) }
        if (Build.VERSION.SDK_INT >= 24) runCatching { connectivity().unregisterNetworkCallback(networkCallback) }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        visible = true
        I18n.apply(config.language)
        online = isOnline()
        if (config.autoUpdate && online && Updater.dueForCheck(this)) lifecycleScope.launch { Updater.check(this@MainActivity) }
        ImageCache.retryMissing()
        refresh()
        resumeCount++
        HomeOverlay.hideSoon()
    }

    private suspend fun refreshWeather() {
        if (!config.weatherEnabled) return
        val id = WeatherRepository.idOf(config.weatherPlace, config.fahrenheit)
        val current = weather
        val stale = current == null || current.id != id || current.allHours.isEmpty() ||
            System.currentTimeMillis() - current.fetchedAt > WeatherRepository.REFRESH_MS
        if (!stale) return
        val fresh = WeatherRepository.fetch(this, config.weatherPlace, config.fahrenheit) ?: return
        if (fresh.id == WeatherRepository.idOf(config.weatherPlace, config.fahrenheit)) weather = fresh
    }

    private suspend fun refreshShelves() {
        val before = Source.entries.filter { it != Source.Recent && Shelves.connected(this, it) }
        Shelves.refresh(this, config)
        before.filterNot { Shelves.connected(this, it) }.forEach {
            toast(trf("{0}-Anmeldung abgelaufen, bitte neu verbinden", "{0} sign-in expired, please connect again", it.title))
        }
    }

    private fun refresh() {
        homeSetupMissing = !HomeWatchService.canReadLogs(this)
        HomeWatchService.start(this)
    }

    private fun requestImageAccess(callback: (Boolean) -> Unit) {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermission(permission, callback)
    }

    private fun requestStorage(callback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= 29) callback(true)
        else requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, callback)
    }

    private fun requestPermission(permission: String, callback: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            callback(true)
            return
        }
        permissionCallback = callback
        permissionRequest.launch(permission)
    }

    private fun exportBackup() = requestStorage { granted ->
        val snapshot = config
        lifecycleScope.launch {
            val ok = granted && withContext(Dispatchers.IO) { Backup.export(this@MainActivity, snapshot) }
            toast(if (ok) trf("Gesichert: Download/{0}", "Backed up: Download/{0}", Backup.FILE_NAME) else tr("Sichern hat nicht geklappt", "Backup failed"))
        }
    }

    private fun importBackup() = requestStorage { granted -> lifecycleScope.launch {
        val restored = if (granted) withContext(Dispatchers.IO) { Backup.import(this@MainActivity) } else null
        if (restored == null) {
            toast(trf("Keine Sicherung gefunden (Download/{0})", "No backup found (Download/{0})", Backup.FILE_NAME))
        } else {
            config = restored
            TileStore.save(this@MainActivity, restored)
            weather = WeatherRepository.cached(this@MainActivity, restored.weatherPlace, restored.fahrenheit)
            refreshWeather()
            toast(tr("Einstellungen wiederhergestellt", "Settings restored"))
        }
    } }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun connectivity() = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private fun isOnline(): Boolean = runCatching { connectivity().activeNetworkInfo?.isConnected == true }.getOrDefault(true)

    private fun showHomeSetupHint() {
        Toast.makeText(
            this,
            trf("Einmalig per ADB: adb shell pm grant {0} android.permission.READ_LOGS", "Once via ADB: adb shell pm grant {0} android.permission.READ_LOGS", packageName),
            Toast.LENGTH_LONG,
        ).show()
    }

    companion object {
        @Volatile var visible = false

        @Volatile var created = false

        private val NavKeyCodes = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        )
    }
}
