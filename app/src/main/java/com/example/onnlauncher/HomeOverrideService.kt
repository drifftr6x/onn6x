package com.example.onnlauncher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * The one accessibility service this launcher uses. It does four jobs:
 *
 * 1. Home button: when the stock Google TV launcher's window comes to the front, start our
 *    MainActivity on top of it. The stock launcher stays installed and enabled.
 * 2. Home pressed while already on our home screen: same bounce, but with an extra that opens
 *    the quick menu. (Android never delivers a long-press of Home to apps or services, so this is
 *    the closest reliable gesture.)
 * 3. Remote button remapping: buttons mapped in Settings launch an app instead.
 * 4. Sleep timer: locks the screen after a delay (best effort, unverified on Google TV).
 *
 * Safety:
 *  - The on/off toggle on our home screen, turning the service off in Accessibility settings,
 *    or `adb uninstall com.example.onnlauncher` restores the stock Home button instantly.
 *  - If we bounce too many times too quickly (a crash loop), the override switches itself off.
 *
 * Watch what it sees with:  adb logcat -s OnnLauncher
 */
class HomeOverrideService : AccessibilityService() {

    private val recentBounces = ArrayDeque<Long>()
    private val handler = Handler(Looper.getMainLooper())
    private var ourForeground = false
    private var lastBounce = 0L
    private var ourSince = 0L
    private var sleepEndsAt = 0L
    private var screenChangedAt = 0L

    /** Remembers when the screen last went off or on, so the press that wakes the box never counts as "Home on the launcher". */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenChangedAt = SystemClock.elapsedRealtime()
            if (intent?.action == Intent.ACTION_SCREEN_OFF) ourForeground = false
            Log.d(TAG, "screen event: ${intent?.action}")
        }
    }

    private val sleepRunnable = Runnable {
        sleepEndsAt = 0L
        val ok = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        Log.d(TAG, "Sleep timer fired, lock screen action accepted = $ok")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            })
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't watch screen state: ${e.message}")
        }
        Log.d(TAG, "Service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(sleepRunnable)
        sleepEndsAt = 0L
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // not registered
        }
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) {
            if (!ourForeground) ourSince = SystemClock.elapsedRealtime()
            ourForeground = true
            return
        }
        if (pkg !in STOCK_LAUNCHER_PACKAGES) {
            if (pkg !in OVERLAY_PACKAGES) ourForeground = false
            return
        }

        val cls = event.className?.toString().orEmpty()
        Log.d(TAG, "stock launcher window: $pkg / $cls (ourForeground=$ourForeground)")

        val prefs = LauncherPrefs(this)
        if (!prefs.overrideEnabled()) return
        if (EXCLUDED_CLASS_FRAGMENTS.any { cls.contains(it, ignoreCase = true) }) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastBounce < DEBOUNCE_MS) return

        // Home pressed while we're already showing: open the quick menu instead of a plain bounce.
        // Right after we come to the front, the stock launcher often reports a late window event.
        // That is not a Home press, so ignore anything that arrives too soon.
        // If the press lands inside that window we can't tell a late event from a quick second press, so
        // we still bring our screen to the front (harmless if it is already there) but skip the menu.
        val quickMenu = ourForeground
        val withinGrace = quickMenu && (now - ourSince < QUICK_MENU_GRACE_MS || now - lastBounce < QUICK_MENU_GRACE_MS)
        val justWoke = now - screenChangedAt < WAKE_GRACE_MS
        // A second Home press a few seconds after the first is deliberate, even if no other button was pressed.
        val doublePress = (now - lastBounce) in QUICK_MENU_GRACE_MS..DOUBLE_PRESS_MS
        val openMenu = quickMenu && !withinGrace && !justWoke
        if (!quickMenu) {
            while (recentBounces.isNotEmpty() && now - recentBounces.first() > LOOP_WINDOW_MS) {
                recentBounces.removeFirst()
            }
            if (recentBounces.size >= LOOP_LIMIT) {
                Log.w(TAG, "Bouncing too fast, switching Home override off to be safe")
                prefs.setOverrideEnabled(false)
                recentBounces.clear()
                return
            }
            recentBounces.addLast(now)
        }
        lastBounce = now

        try {
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
            if (openMenu) {
                intent.putExtra(MainActivity.EXTRA_QUICK_MENU, true)
                intent.putExtra(MainActivity.EXTRA_DOUBLE_PRESS, doublePress)
            }
            startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start launcher: ${e.message}")
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code in KeyBus.BLOCKED) return false

        if (KeyBus.learning) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) KeyBus.emit(code)
            return true // swallow it so it doesn't also do its normal job while you're learning it
        }

        val target = LauncherPrefs(this).keymap()[code] ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) launchApp(target)
        return true
    }

    private fun launchApp(packageName: String) {
        val pm = packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Log.w(TAG, "No launch intent for $packageName")
            return
        }
        try {
            LauncherPrefs(this).pushRecent(packageName)
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't launch $packageName: ${e.message}")
        }
    }

    /** 0 cancels. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepRunnable)
        if (minutes > 0) {
            sleepEndsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
            handler.postDelayed(sleepRunnable, minutes * 60_000L)
        } else {
            sleepEndsAt = 0L
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val TAG = "OnnLauncher"

        @Volatile
        var instance: HomeOverrideService? = null

        /** Minutes left on the sleep timer, rounded up, or 0 if none is running. */
        fun sleepRemainingMinutes(): Int {
            val service = instance ?: return 0
            val left = service.sleepEndsAt - SystemClock.elapsedRealtime()
            return if (left > 0) ((left + 59_999) / 60_000).toInt() else 0
        }

        /** Stock launcher packages whose windows we bounce. */
        private val STOCK_LAUNCHER_PACKAGES = setOf(
            "com.google.android.apps.tv.launcherx", // Google TV home
            "com.google.android.tvlauncher", // older Android TV home
        )

        /** Overlays that appear over our home screen without meaning we've been left. */
        private val OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.katniss", // Google voice assistant / Gemini
            "com.google.android.gms",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
        )

        /**
         * If part of the stock launcher is something you still want to reach (a profile picker,
         * its own settings...), run `adb logcat -s OnnLauncher`, open it, and add a fragment of the
         * class name logged here. Empty by default because I don't know those names for your build.
         */
        private val EXCLUDED_CLASS_FRAGMENTS = emptyList<String>()

        private const val DEBOUNCE_MS = 600L
        private const val QUICK_MENU_GRACE_MS = 2_000L
        private const val WAKE_GRACE_MS = 8_000L
        private const val DOUBLE_PRESS_MS = 10_000L
        private const val LOOP_LIMIT = 5
        private const val LOOP_WINDOW_MS = 10_000L
    }
}
