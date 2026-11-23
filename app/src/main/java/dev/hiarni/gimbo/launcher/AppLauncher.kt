package dev.hiarni.gimbo.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast

object AppLauncher {
    private const val PREFS = "gimbo"
    private const val KEY_LAST_APP = "last_app"

    @Volatile var lastFocusedTileId: String? = null

    @Volatile var lastFocusedItemId: String? = null

    fun lastTileId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_APP, null)

    fun launch(context: Context, tile: Tile) {
        val intent = AppResolver.launchIntent(context.packageManager, tile.packageName)?.asLauncherStart(tile.packageName)
        if (intent != null && tryStart(context, intent)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_LAST_APP, tile.id)
                .apply()
            return
        }
        Toast.makeText(context, trf("{0} ist nicht installiert", "{0} is not installed", tile.label), Toast.LENGTH_LONG).show()
    }

    fun launchPackage(context: Context, pkg: String) {
        val intent = AppResolver.launchIntent(context.packageManager, pkg)?.asLauncherStart(pkg)
        if (intent == null || !tryStart(context, intent)) {
            Toast.makeText(context, tr("App lässt sich nicht starten", "App can't be started"), Toast.LENGTH_LONG).show()
        }
    }

    private fun Intent.asLauncherStart(pkg: String): Intent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (pkg in BuiltIn.jellyfin) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return this
    }

    fun openSettings(context: Context) {
        HomeWatchService.allowAmazonUntil = SystemClock.uptimeMillis() + 4_000
        HomeOverlay.hideAfterLaunch()
        tryStart(context, Intent(Settings.ACTION_SETTINGS))
    }

    fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
