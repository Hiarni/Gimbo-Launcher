package dev.hiarni.gimbo.launcher

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

// Erkennung der Amazon-Startseite
class HomeWatchService : Service() {

    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var process: Process? = null
    private val main = Handler(Looper.getMainLooper())
    private var lastTrigger = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canReadLogs(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!hasLogGroup() && restartAllowed()) {
            restartProcess()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            thread = Thread(::watchLoop, "gimbo-home-watch").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        process?.destroy()
        thread?.interrupt()
        super.onDestroy()
    }

    private fun hasLogGroup(): Boolean = runCatching {
        java.io.File("/proc/self/status").readLines()
            .firstOrNull { it.startsWith("Groups:") }
            ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))
            ?.contains(AID_LOG.toString())
            ?: true
    }.getOrDefault(true)

    private fun restartAllowed(): Boolean {
        val prefs = getSharedPreferences("gimbo", Context.MODE_PRIVATE)
        val boot = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
        val sameBoot = kotlin.math.abs(prefs.getLong("watch_restart_boot", 0L) - boot) <= 1
        val count = if (sameBoot) prefs.getInt("watch_restart_count", 0) else 0
        if (count >= 2) return false
        prefs.edit().putLong("watch_restart_boot", boot).putInt("watch_restart_count", count + 1).commit()
        return true
    }

    private fun restartProcess() {
        val flags = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val reopen = MainActivity.visible
        val pending = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, RestartReceiver::class.java).setAction(ACTION_RESTART).putExtra(EXTRA_REOPEN, reopen),
            flags or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1_500, pending)
        Log.i(TAG, "Prozess ohne Log-Gruppe – Neustart")
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun watchLoop() {
        while (running) {
            try {
                val p = ProcessBuilder("logcat", "-v", "brief", "-T", "1", "ActivityManager:I", "ActivityTaskManager:I", "*:S")
                    .redirectErrorStream(true)
                    .start()
                process = p
                p.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        if (isAmazonHomeStart(line)) onAmazonHome()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "logcat beendet: ${e.message}")
            }
            if (running) SystemClock.sleep(1_000)
        }
    }

    private fun isAmazonHomeStart(line: String): Boolean {
        if (!line.contains("START")) return false
        if (line.contains(packageName)) return false
        return line.contains("com.amazon.tv.launcher") &&
            line.contains("act=android.intent.action.MAIN") &&
            line.contains("android.intent.category.HOME")
    }

    private fun onAmazonHome() {
        val now = SystemClock.uptimeMillis()
        if (now < allowAmazonUntil || now - lastTrigger < 400) return
        lastTrigger = now
        main.post {
            HomeOverlay.show(this)
            openLauncher(this)
        }
    }

    private fun buildNotification(): Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_MIN)
                .setName(tr("Home-Taste", "Home button"))
                .build(),
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_settings)
            .setContentTitle("Gimbo Launcher")
            .setContentText(tr("Home-Taste führt zum Launcher", "Home button opens the launcher"))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "GimboHomeWatch"
        private const val CHANNEL_ID = "home_watch"
        private const val NOTIFICATION_ID = 1
        private const val AID_LOG = 1007
        const val ACTION_RESTART = "dev.hiarni.gimbo.launcher.RESTART_WATCH"
        const val EXTRA_REOPEN = "reopen"

        @Volatile var allowAmazonUntil = 0L

        fun canReadLogs(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_LOGS) ==
                PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            if (!canReadLogs(context)) return
            val intent = Intent(context, HomeWatchService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun openLauncher(context: Context) {
            val intent = Intent(context, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            )
            runCatching { context.startActivity(intent) }
        }
    }
}

// Start nach dem Hochfahren
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        HomeWatchService.start(context)
        val prefs = context.getSharedPreferences("gimbo", Context.MODE_PRIVATE)
        val updated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && prefs.getBoolean(Updater.KEY_REOPEN, false)
        if (updated) prefs.edit().remove(Updater.KEY_REOPEN).apply()
        val boot = intent.action == Intent.ACTION_BOOT_COMPLETED && !MainActivity.created
        if (boot || (updated && !MainActivity.visible)) HomeWatchService.openLauncher(context)
    }
}

// Neustart nach fehlender Log-Berechtigung
class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HomeWatchService.ACTION_RESTART) return
        HomeWatchService.start(context)
        if (intent.getBooleanExtra(HomeWatchService.EXTRA_REOPEN, false)) HomeWatchService.openLauncher(context)
    }
}
