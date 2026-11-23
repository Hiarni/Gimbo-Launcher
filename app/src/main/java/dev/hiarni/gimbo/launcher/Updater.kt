package dev.hiarni.gimbo.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Updates von GitHub
data class UpdateInfo(val version: String, val url: String, val size: Long, val notes: String)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo?) : UpdateState
}

object Updater {
    private const val API = "https://api.github.com/repos/Hiarni/Gimbo-Launcher/releases/latest"
    private const val PREFS = "gimbo"
    private const val KEY_CHECKED = "update_checked_at"
    private const val KEY_INFO = "update_info"
    const val KEY_REOPEN = "update_reopen"
    private const val ACTION_STATUS = "dev.hiarni.gimbo.launcher.UPDATE_STATUS"
    private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val available: UpdateInfo?
        get() = when (val s = state) {
            is UpdateState.Available -> s.info
            is UpdateState.Downloading -> s.info
            is UpdateState.Installing -> s.info
            is UpdateState.Failed -> s.info
            else -> null
        }

    val busy: Boolean get() = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Installing

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun restore(context: Context) {
        if (state != UpdateState.Idle) return
        val json = prefs(context).getString(KEY_INFO, null) ?: return
        val info = runCatching {
            val o = JSONObject(json)
            UpdateInfo(o.getString("version"), o.getString("url"), o.getLong("size"), o.optString("notes"))
        }.getOrNull() ?: return
        if (isNewer(info.version, currentVersion(context))) state = UpdateState.Available(info)
    }

    fun dueForCheck(context: Context): Boolean =
        !busy && System.currentTimeMillis() - prefs(context).getLong(KEY_CHECKED, 0L) > CHECK_INTERVAL_MS

    suspend fun check(context: Context): UpdateInfo? {
        if (busy) return available
        val previous = available
        state = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        val info = result.getOrNull()
        if (result.isFailure) {
            state = previous?.let { UpdateState.Available(it) }
                ?: UpdateState.Failed(tr("Keine Verbindung zu GitHub", "Can't reach GitHub"), null)
            return previous
        }
        val edit = prefs(context).edit().putLong(KEY_CHECKED, System.currentTimeMillis())
        if (info != null && isNewer(info.version, currentVersion(context))) {
            edit.putString(
                KEY_INFO,
                JSONObject().put("version", info.version).put("url", info.url).put("size", info.size).put("notes", info.notes).toString(),
            ).apply()
            state = UpdateState.Available(info)
            return info
        }
        edit.remove(KEY_INFO).apply()
        state = UpdateState.UpToDate
        return null
    }

    suspend fun install(context: Context) {
        val info = available ?: return
        if (state is UpdateState.Downloading || state is UpdateState.Installing) return
        state = UpdateState.Downloading(info, 0f)
        val app = context.applicationContext
        val file = withContext(Dispatchers.IO) { runCatching { download(app, info) }.getOrNull() }
        if (file == null) {
            state = UpdateState.Failed(tr("Download fehlgeschlagen", "Download failed"), info)
            return
        }
        if (!withContext(Dispatchers.IO) { verify(app, file) }) {
            file.delete()
            state = UpdateState.Failed(tr("Die Datei ist kein gültiges Update", "The file isn't a valid update"), info)
            return
        }
        state = UpdateState.Installing(info)
        val ok = withContext(Dispatchers.IO) { runCatching { commit(app, file) }.isSuccess }
        if (!ok) state = UpdateState.Failed(tr("Installation fehlgeschlagen", "Installation failed"), info)
    }

    fun onInstallStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { fail(context, tr("Installation fehlgeschlagen", "Installation failed")) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> fail(context, tr("Installation abgebrochen", "Installation cancelled"))
            else -> fail(context, tr("Installation fehlgeschlagen", "Installation failed"))
        }
    }

    private fun fail(context: Context, message: String) {
        prefs(context).edit().remove(KEY_REOPEN).apply()
        state = UpdateState.Failed(message, available)
    }

    private fun fetchLatest(): UpdateInfo? {
        val c = open(API)
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            kotlin.check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val assets = root.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
            return UpdateInfo(
                version = root.getString("tag_name").removePrefix("v"),
                url = apk.getString("browser_download_url"),
                size = apk.optLong("size"),
                notes = root.optString("body"),
            )
        } finally {
            c.disconnect()
        }
    }

    private fun download(context: Context, info: UpdateInfo): File {
        val dir = File(context.cacheDir, "update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Gimbo-Launcher-${info.version}.apk")
        val c = open(info.url)
        try {
            kotlin.check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val total = if (info.size > 0) info.size else c.contentLength.toLong()
            var done = 0L
            var shown = -1
            c.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct != shown) {
                            shown = pct
                            state = UpdateState.Downloading(info, pct / 100f)
                        }
                    }
                }
            }
            kotlin.check(info.size <= 0 || file.length() == info.size) { "size" }
            return file
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    private fun verify(context: Context, file: File): Boolean = runCatching {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, 0) ?: return false
        val installed = pm.getPackageInfo(context.packageName, 0)
        archive.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed)
    }.getOrDefault(false)

    private fun commit(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            prefs(context).edit().putBoolean(KEY_REOPEN, true).commit()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val status = PendingIntent.getBroadcast(
                context, id, Intent(context, UpdateReceiver::class.java).setAction(ACTION_STATUS), flags,
            )
            session.commit(status.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Gimbo-Launcher")
        }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notesFor(info: UpdateInfo): List<String> = info.notes.lines()
        .map { it.trim() }
        .filter { it.startsWith("- ") }
        .map { line ->
            val text = line.removePrefix("- ")
            val parts = text.split(" · ")
            if (parts.size == 2) (if (I18n.code == "de") parts[0] else parts[1]) else text
        }
}

// Rückmeldung der Installation
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Updater.onInstallStatus(context, intent)
}
