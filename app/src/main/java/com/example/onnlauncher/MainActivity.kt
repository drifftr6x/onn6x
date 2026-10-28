package com.example.onnlauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Calendar
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private lateinit var prefs: LauncherPrefs

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LauncherPrefs(this)

        // Crash detection: this flag is cleared a few seconds after a healthy start. If it is still
        // set now, the last run died early, so come up in safe mode with the override off.
        val crashedLastTime = prefs.startupPending()
        prefs.setStartupPending(true)
        Handler(Looper.getMainLooper()).postDelayed({ prefs.setStartupPending(false) }, STARTUP_OK_MS)
        if (crashedLastTime) vm.enterCrashSafeMode()

        // First launch since a reboot: start the auto-start countdown (if an app is chosen).
        val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (bootCount != -1 && bootCount != prefs.lastBootCount()) {
            prefs.setLastBootCount(bootCount)
            val freshBoot = SystemClock.elapsedRealtime() < FRESH_BOOT_MS
            if (freshBoot && !crashedLastTime && savedInstanceState == null) vm.scheduleAutoStart()
        }

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            var tick by remember { mutableStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(60_000)
                    tick = System.currentTimeMillis()
                }
            }
            val s = state.settings
            val themeIndex = if (s.autoTheme) {
                val day = state.weather?.forecast?.isDaylight(tick / 1000)
                    ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it in 7..18 }
                if (day) s.dayTheme else s.nightTheme
            } else {
                s.themeIndex
            }
            val baseTheme = Themes.get(themeIndex)
            val cornerDp = Options.roundnessDp[s.roundness]
            val withCorner = if (cornerDp > 0) baseTheme.copy(corner = cornerDp.dp) else baseTheme
            // A font picked in Settings wins over a theme's built-in monospace.
            val family = AppFonts.family(s.fontIndex)
            val theme = if (family != null) withCorner.copy(monospace = false) else withCorner
            val accent = if (state.settings.accentIndex == 0) {
                theme.accent
            } else {
                Color(Options.accents[state.settings.accentIndex].second)
            }
            val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
            val scheme = if (theme.light) {
                lightColorScheme(primary = accent, onPrimary = onAccent, border = accent)
            } else {
                darkColorScheme(primary = accent, onPrimary = onAccent, border = accent)
            }
            MaterialTheme(colorScheme = scheme, typography = Typography().withFamily(family)) {
                // A home screen should never "go back" to anything.
                BackHandler(enabled = true) { }
                CompositionLocalProvider(
                    LocalLauncherTheme provides theme,
                    LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = family)),
                ) {
                HomeScreen(
                    vm = vm,
                    onLaunch = ::launch,
                    onQuickAction = ::onQuickAction,
                    onOpenAndroidSettings = { startSafely(Intent(Settings.ACTION_SETTINGS)) },
                    onOpenAccessibilitySettings = { startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenStockHome = ::openStockHome,
                    onAppInfo = { pkg ->
                        startSafely(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:$pkg"),
                            ),
                        )
                    },
                    onForceClose = ::forceClose,
                    onLaunchSearch = ::launchWithSearch,
                    nowPlayingEnabled = ::isNowPlayingOn,
                    onSetupNowPlaying = ::setupNowPlaying,
                    dnsEnabled = ::isDnsOn,
                    onToggleDns = ::toggleDns,
                    onUninstall = { pkg ->
                        startSafely(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))
                    },
                    onToast = ::toast,
                    onInstallApk = ::installApk,
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The accessibility service sets this when Home is pressed while we're already showing.
        if (intent.getBooleanExtra(EXTRA_QUICK_MENU, false)) {
            // A Home press that is really just waking the box (or dismissing the screensaver) must not open the menu.
            val doublePress = intent.getBooleanExtra(EXTRA_DOUBLE_PRESS, false)
            if (!doublePress && vm.homeIsWakeUp()) vm.wake() else vm.toggleQuickMenu()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        vm.setForeground(true)
        vm.refreshIfStale()
    }

    override fun onStop() {
        vm.setForeground(false)
        vm.stopEditor()
        runCatching { unregisterReceiver(packageReceiver) }
        super.onStop()
    }

    /** Any key press resets the idle timer. While the screensaver is up, the first key only wakes it. */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        vm.touch()
        if (event.action == KeyEvent.ACTION_DOWN) vm.keyPressed()
        if (event.action == KeyEvent.ACTION_DOWN && vm.cancelAutoStart()) return true
        if (vm.state.value.idle) {
            if (event.action == KeyEvent.ACTION_UP) vm.wake()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Holding Back on the home screen opens safe mode, so you can always get out. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            vm.openSafeMode()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    private fun launch(app: AppEntry) {
        vm.recordLaunch(app.packageName)
        startSafely(app.intent)
    }

    private fun onQuickAction(action: QuickAction) {
        when (action) {
            QuickAction.ASSISTANT -> openAssistant()
            QuickAction.WIFI -> openSettingsScreen("Wi-Fi", Intent(Settings.ACTION_WIFI_SETTINGS))
            QuickAction.BLUETOOTH -> openSettingsScreen(
                "Bluetooth",
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
                Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity"),
            )
            QuickAction.DISPLAY -> openSettingsScreen(
                "Display",
                Intent(Settings.ACTION_DISPLAY_SETTINGS),
                Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.display.DisplayActivity"),
            )
            QuickAction.SOUND -> openSettingsScreen("Sound", Intent(Settings.ACTION_SOUND_SETTINGS))
            QuickAction.DEVELOPER -> openSettingsScreen(
                "Developer options",
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            )
            QuickAction.SLEEP -> toast(vm.cycleSleep())
            QuickAction.DEVICE_INFO -> vm.openDeviceInfo()
            QuickAction.INSTALL_APK -> vm.openApkInstaller()
            QuickAction.PHONE_EDITOR -> vm.startEditor(::toast)
            QuickAction.FREE_MEMORY -> freeMemory()
        }
    }

    /** Tries the standard assistant intents. The remote's own mic button is still the surest way in. */
    private fun openAssistant() {
        val candidates = listOf(
            Intent(Intent.ACTION_ASSIST),
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent(Intent.ACTION_SEARCH_LONG_PRESS),
        )
        val target = candidates.firstOrNull { it.resolveActivity(packageManager) != null }
        if (target != null) {
            startSafely(target)
        } else {
            toast("No voice assistant answered. Use the mic button on your remote.")
        }
    }

    /** Turns our override off first (otherwise it would bounce straight back), then opens the stock home. */
    private fun openStockHome() {
        vm.setOverride(false)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        @Suppress("DEPRECATION")
        val stock = packageManager.queryIntentActivities(home, 0)
            .firstOrNull { it.activityInfo.packageName != packageName }
        val intent = if (stock != null) {
            Intent(home).setClassName(stock.activityInfo.packageName, stock.activityInfo.name)
        } else {
            home
        }
        startSafely(intent)
    }

    /**
     * Tries each intent in turn. Google TV's settings app doesn't answer some of the standard phone
     * intents (Bluetooth, Display), so if none works we open the main Settings screen instead.
     */
    private fun openSettingsScreen(name: String, vararg candidates: Intent) {
        for (candidate in candidates) {
            try {
                startActivity(candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // try the next one
            }
        }
        toast("$name has no direct shortcut on this box. Opening Settings; look for it there.")
        startSafely(Intent(Settings.ACTION_SETTINGS))
    }

    /** Hands an APK from the launcher's apks folder to the system installer. */
    /**
     * Stops the app's background processes. Android only lets a normal app do that much; a full
     * "Force stop" needs a system permission, so App info is still there for that.
     */
    private fun forceClose(pkg: String, label: String) {
        try {
            val am = getSystemService(android.app.ActivityManager::class.java)
            am.killBackgroundProcesses(pkg)
            toast("Closed $label. If it keeps running, use App info > Force stop.")
        } catch (e: Exception) {
            toast("Couldn't close $label: ${e.message}")
        }
    }

    /** Opens [app] on a search for [title] when we know that app's search link, otherwise just opens it. */
    private fun launchWithSearch(app: AppEntry, title: String) {
        val intent = DeepLinks.intentFor(app.packageName, title)
        if (intent == null) {
            launch(app)
            return
        }
        try {
            vm.recordLaunch(app.packageName)
            startActivity(intent)
        } catch (e: Exception) {
            toast("${app.label} didn't take the search, so I just opened it.")
            launch(app)
        }
    }

    /** Stops the background processes of every other app and reports roughly how much memory came back. */
    private fun freeMemory() {
        val am = getSystemService(android.app.ActivityManager::class.java)
        fun available(): Long {
            val info = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            return info.availMem
        }
        val before = available()
        vm.snapshotApps().forEach { app ->
            if (app.packageName != packageName) {
                try {
                    am.killBackgroundProcesses(app.packageName)
                } catch (e: Exception) {
                    // Ignore apps that refuse.
                }
            }
        }
        toast("Freeing memory...")
        Handler(Looper.getMainLooper()).postDelayed({
            val freedMb = (available() - before) / (1024 * 1024)
            toast(if (freedMb > 8) "Freed about $freedMb MB." else "Little to free: background apps were already closed.")
        }, 1500)
    }

    private fun isNowPlayingOn(): Boolean = try {
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty().contains(packageName)
    } catch (e: Exception) {
        false
    }

    /** Opens the notification-access screen if this box has one; Google TV often doesn't, so the adb command is the fallback. */
    private fun setupNowPlaying() {
        if (isNowPlayingOn()) {
            toast("What's playing is on.")
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("On your PC run: adb shell cmd notification allow_listener com.example.onnlauncher/com.example.onnlauncher.NowPlayingService")
        }
    }

    private fun isDnsOn(): Boolean = try {
        Settings.Global.getString(contentResolver, "private_dns_mode") == "hostname" &&
            Settings.Global.getString(contentResolver, "private_dns_specifier") == ADGUARD_DNS
    } catch (e: Exception) {
        false
    }

    /** Switches the box's Private DNS to AdGuard (blocks many ad and tracker domains) or back to automatic. */
    private fun toggleDns() {
        try {
            if (isDnsOn()) {
                Settings.Global.putString(contentResolver, "private_dns_mode", "opportunistic")
                toast("Ad-blocking DNS is off.")
            } else {
                Settings.Global.putString(contentResolver, "private_dns_specifier", ADGUARD_DNS)
                Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
                toast("Ad-blocking DNS is on (AdGuard). It does not block YouTube's own ads.")
            }
        } catch (e: SecurityException) {
            toast("Needs a one-time permission. On your PC run: adb shell pm grant com.example.onnlauncher android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    private fun installApk(file: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            toast("Allow \"Install unknown apps\" for onn6x Launcher, then try again.")
            startSafely(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
            )
            return
        }
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startSafely(intent)
        } catch (e: Exception) {
            toast("Couldn't start the installer: ${e.message}")
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Couldn't open that: ${e.message}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** Set by the accessibility service when Home is pressed on the home screen. */
        const val EXTRA_DOUBLE_PRESS = "double_press"
        const val EXTRA_QUICK_MENU = "quick_menu"
        private const val STARTUP_OK_MS = 4_000L
        private const val ADGUARD_DNS = "dns.adguard-dns.com"
        private const val FRESH_BOOT_MS = 10 * 60_000L
    }
}
