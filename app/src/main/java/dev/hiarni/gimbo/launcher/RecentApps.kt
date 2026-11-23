package dev.hiarni.gimbo.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Zuletzt genutzte Apps
object RecentApps {
    const val SCHEME = "app:"
    private const val DAYS = 14

    private val hidden = setOf(
        "com.amazon.tv.launcher", "com.amazon.tv.settings", "com.amazon.tv.settings.v2", "com.android.settings",
        "com.amazon.venezia", "com.amazon.firebat", "com.amazon.tv.oobe", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.amazon.tv.notificationcenter", "com.amazon.vizzini",
    )

    fun granted(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun openAccessSettings(context: Context): Boolean =
        AppLauncher.tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    suspend fun fetchItems(context: Context, limit: Int = 10): List<MediaItem>? = withContext(Dispatchers.IO) {
        if (!granted(context)) return@withContext null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext null
        val now = System.currentTimeMillis()
        val last = HashMap<String, Long>()
        runCatching {
            val events = usm.queryEvents(now - DAYS * 86_400_000L, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                @Suppress("DEPRECATION")
                val foreground = e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (Build.VERSION.SDK_INT >= 29 && e.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                if (foreground && e.timeStamp > (last[e.packageName] ?: 0L)) last[e.packageName] = e.timeStamp
            }
        }
        if (last.isEmpty()) {
            runCatching {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - DAYS * 86_400_000L, now)
                    .filter { it.lastTimeUsed > 0 && it.totalTimeInForeground > 0 }
                    .forEach { s -> if (s.lastTimeUsed > (last[s.packageName] ?: 0L)) last[s.packageName] = s.lastTimeUsed }
            }
        }
        val pm = context.packageManager
        last.entries
            .asSequence()
            .filter { it.key != context.packageName && it.key !in hidden && InstalledApps.isInstalled(pm, it.key) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { (pkg, time) ->
                MediaItem(
                    id = pkg,
                    title = InstalledApps.label(pm, pkg) ?: pkg,
                    subtitle = ago(now - time),
                    progress = 0f,
                    thumbUrl = SCHEME + pkg,
                    backdropUrl = null,
                    source = Source.Recent,
                )
            }
            .toList()
    }

    private fun ago(ms: Long): String {
        val minutes = ms / 60_000L
        return when {
            minutes < 2 -> tr("Gerade eben", "Just now")
            minutes < 60 -> trf("Vor {0} Min.", "{0} min ago", minutes)
            minutes < 24 * 60 -> trf("Vor {0} Std.", "{0} h ago", minutes / 60)
            minutes < 2 * 24 * 60 -> tr("Gestern", "Yesterday")
            else -> trf("Vor {0} Tagen", "{0} days ago", minutes / (24 * 60))
        }
    }

    fun banner(context: Context, pkg: String): Bitmap? {
        val pm = context.packageManager
        InstalledApps.banner(pm, pkg)?.let { return it }
        val icon = InstalledApps.icon(pm, pkg) ?: return null
        val color = InstalledApps.guessColor(icon).toInt()
        val out = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = LinearGradient(0f, 0f, 480f, 270f, color, darker(color), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 480f, 270f, paint)
        paint.shader = null
        val size = 132f
        canvas.drawBitmap(icon, null, android.graphics.RectF(240 - size / 2, 135 - size / 2, 240 + size / 2, 135 + size / 2), paint)
        return out
    }

    private fun darker(color: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] *= 0.45f
        return android.graphics.Color.HSVToColor(hsv)
    }

    fun open(context: Context, item: MediaItem): Boolean {
        val intent = AppResolver.launchIntent(context.packageManager, item.id) ?: return false
        return AppLauncher.tryStart(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }
}
