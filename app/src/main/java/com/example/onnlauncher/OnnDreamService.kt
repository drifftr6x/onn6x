package com.example.onnlauncher

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * System screensaver: runs from anywhere on the box (not just while the launcher is open) once
 * you select it with ADB. Shows your wallpaper photos, the clock, the date and the weather.
 * Built from plain Android views so it needs no extra setup.
 */
class OnnDreamService : DreamService() {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var photo: ImageView
    private lateinit var textBox: LinearLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView

    private var index = 0
    private var running = false
    private var rotateMs = 5 * 60_000L

    private val timeFormat by lazy { android.text.format.DateFormat.getTimeFormat(this) }
    private val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        setContentView(buildLayout())
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        running = true
        val prefs = LauncherPrefs(this)
        rotateMs = prefs.settings().wallpaperMinutes.coerceAtLeast(1) * 60_000L
        tick.run()
        showNextPhoto()
        loadWeather(prefs)
    }

    override fun onDreamingStopped() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        worker.shutdownNow()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val prefs = LauncherPrefs(this)
        val accent = Themes.get(prefs.settings().themeIndex).accent
        val a = accent.toArgb()

        val root = FrameLayout(this)
        // Slow-moving look when there are no photos: a dark gradient tinted by the theme accent.
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(blend(a, Color.BLACK, 0.72f), blend(a, Color.BLACK, 0.92f), Color.BLACK),
        )

        photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        root.addView(photo, FrameLayout.LayoutParams(-1, -1))

        val scrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, 0xAA000000.toInt()),
            )
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, dp(320), Gravity.BOTTOM))

        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 80f
        }
        date = TextView(this).apply {
            setTextColor(0xFFD5DDEB.toInt())
            textSize = 22f
        }
        weather = TextView(this).apply {
            setTextColor(0xFFB8C2D4.toInt())
            textSize = 17f
        }
        textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock)
            addView(date)
            addView(weather)
        }
        root.addView(textBox, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        return root
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        fun mix(a: Int, b: Int) = (a + (b - a) * amount).toInt()
        return Color.rgb(
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to)),
        )
    }

    /** Updates the clock and nudges it a little each minute so it never burns into the panel. */
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val now = Date()
            clock.text = timeFormat.format(now)
            date.text = dateFormat.format(now)
            val minute = (now.time / 60_000).toInt()
            textBox.setPadding(dp(56 + (minute % 9) * 12), 0, dp(56), dp(40 + (minute % 5) * 10))
            handler.postDelayed(this, 15_000)
        }
    }

    private fun showNextPhoto() {
        if (!running) return
        worker.execute {
            val files = WallpaperStore.list(this)
            var bitmap: Bitmap? = null
            if (files.isNotEmpty()) {
                val file = files[index % files.size]
                index++
                bitmap = WallpaperStore.load(file)?.let { androidBitmap(it) }
            }
            handler.post {
                if (!running) return@post
                if (bitmap == null) {
                    photo.animate().alpha(0f).setDuration(600).start()
                } else {
                    photo.animate().alpha(0f).setDuration(500).withEndAction {
                        photo.setImageBitmap(bitmap)
                        photo.animate().alpha(1f).setDuration(900).start()
                    }.start()
                }
                handler.postDelayed({ showNextPhoto() }, if (files.size > 1) rotateMs else 60_000L)
            }
        }
    }

    private fun androidBitmap(image: androidx.compose.ui.graphics.ImageBitmap): Bitmap = image.asAndroidBitmap()

    private fun loadWeather(prefs: LauncherPrefs) {
        val s = prefs.settings()
        if (!s.showWeather || !s.hasLocation) return
        worker.execute {
            val snap = WeatherClient.load(s.weatherLat, s.weatherLon, s.fahrenheit)
            if (snap != null) {
                handler.post {
                    if (running) {
                        weather.text = snap.text + if (s.weatherName.isNotBlank()) "   ·   " + s.weatherName else ""
                    }
                }
            }
        }
    }
}
