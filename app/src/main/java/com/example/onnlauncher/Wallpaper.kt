package com.example.onnlauncher

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Wallpapers are just image files in this app's own folder, so no storage permission is needed.
 * Put one or many in it and they rotate every few minutes:
 *
 *   adb push photo.jpg /sdcard/Android/data/com.example.onnlauncher/files/wallpapers/
 *
 * Remove them all to go back to the plain gradient.
 */
object WallpaperStore {

    private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun list(context: Context): List<File> =
        context.getExternalFilesDir("wallpapers")
            ?.listFiles { f -> f.isFile && f.extension.lowercase() in EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    /** Decodes at roughly screen size so a big photo doesn't eat the box's memory. */
    fun load(file: File, maxWidth: Int = 1920, maxHeight: Int = 1080): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }.getOrNull()
}

private const val RESCAN_MS = 30_000L

/** [intervalMinutes] is how often to switch pictures when there is more than one. */
@Composable
fun WallpaperBackground(intervalMinutes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val rotateMs = intervalMinutes.coerceAtLeast(1) * 60_000L

    LaunchedEffect(rotateMs) {
        var index = 0
        var shownKey: String? = null
        while (true) {
            val files = withContext(Dispatchers.IO) { WallpaperStore.list(context) }
            if (files.isEmpty()) {
                image = null
                shownKey = null
                delay(RESCAN_MS)
                continue
            }
            val file = files[index % files.size]
            val key = file.path + ":" + file.lastModified()
            // Don't reload (and re-crossfade) the same picture on every rescan.
            if (key != shownKey) {
                image = withContext(Dispatchers.IO) { WallpaperStore.load(file) }
                shownKey = key
            }
            if (files.size > 1) {
                index++
                delay(rotateMs)
            } else {
                delay(RESCAN_MS)
            }
        }
    }

    Crossfade(targetState = image, modifier = modifier.fillMaxSize(), label = "wallpaper") { bitmap ->
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
