package dev.hiarni.gimbo.launcher

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONObject
import java.io.File

object Backup {
    const val FILE_NAME = "Gimbo-Launcher-Sicherung.json"

    fun export(context: Context, config: LauncherConfig): Boolean = runCatching {
        val images = JSONObject()
        (config.tiles.mapNotNull { it.imagePath } + listOfNotNull(config.backdropImage)).forEach { path ->
            val file = File(path)
            if (file.exists()) images.put(file.name, Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
        }
        val json = JSONObject()
            .put("version", 1)
            .put("config", JSONObject(TileStore.toJson(config)))
            .put("images", images)
            .toString()
        write(context, json)
    }.getOrDefault(false)

    fun import(context: Context): LauncherConfig? = runCatching {
        val root = JSONObject(read(context) ?: return null)
        val config = TileStore.fromJson(root.getJSONObject("config").toString())
        val dir = File(context.filesDir, "tiles").apply { mkdirs() }
        val images = root.optJSONObject("images")
        images?.keys()?.forEach { name ->
            File(dir, File(name).name).writeBytes(Base64.decode(images.getString(name), Base64.DEFAULT))
        }
        fun relocate(path: String?) = path?.let { File(dir, File(it).name).absolutePath }
        config.copy(
            tiles = config.tiles.map { it.copy(imagePath = relocate(it.imagePath)) },
            backdropImage = relocate(config.backdropImage),
        )
    }.getOrNull()

    private fun write(context: Context, text: String): Boolean {
        if (Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, FILE_NAME).writeText(text)
            return true
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.delete(collection, "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(FILE_NAME))
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(collection, values) ?: return false
        resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return false
        return true
    }

    private fun read(context: Context): String? {
        if (Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
            return if (file.exists()) file.readText() else null
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(FILE_NAME),
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(0))
            return resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        }
        return null
    }
}
