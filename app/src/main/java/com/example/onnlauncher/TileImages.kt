package com.example.onnlauncher

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * Custom tile pictures are plain files named after the app's package, in this app's own folder:
 *   /sdcard/Android/data/com.example.onnlauncher/files/tiles/com.some.app.png
 * The phone editor writes them there, and `adb push` works too. A 16:9 picture fits the tile best.
 */
object TileImages {

    val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun dir(context: Context): File? = context.getExternalFilesDir("tiles")

    /** Package name -> decoded picture, for every valid file in the folder. */
    fun loadAll(context: Context): Map<String, ImageBitmap> {
        val files = dir(context)?.listFiles() ?: return emptyMap()
        val result = HashMap<String, ImageBitmap>()
        for (file in files) {
            if (!file.isFile || file.extension.lowercase() !in EXTENSIONS) continue
            val bitmap = WallpaperStore.load(file, 640, 360) ?: continue
            result[file.nameWithoutExtension] = bitmap
        }
        return result
    }

    /** Package names that currently have a custom picture. */
    fun packagesWithImages(context: Context): Set<String> =
        dir(context)?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            .orEmpty()

    fun delete(context: Context, packageName: String) {
        dir(context)?.listFiles()
            ?.filter { it.nameWithoutExtension == packageName }
            ?.forEach { it.delete() }
    }
}
