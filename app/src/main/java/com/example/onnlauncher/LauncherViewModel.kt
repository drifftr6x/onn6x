package com.example.onnlauncher

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RowState(val name: String, val apps: List<AppEntry>)

data class UiState(
    /** Rows to show on the home screen (seeded rows that are empty are left out). */
    val rows: List<RowState> = emptyList(),
    /** Every row name, including empty ones, for the "Add to row" menu. */
    val rowNames: List<String> = emptyList(),
    /** Row name -> package names in that row (unfiltered), for menu logic. */
    val rowContents: Map<String, List<String>> = emptyMap(),
    val allApps: List<AppEntry> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val showHidden: Boolean = false,
    val recents: List<AppEntry> = emptyList(),
    val settings: AppSettings = AppSettings(),
    /** Remote button key code -> package name. */
    val keymap: Map<Int, String> = emptyMap(),
    val weather: WeatherState? = null,
    val crypto: List<CryptoClient.Coin> = emptyList(),
    /** Whether the Home-button override service is allowed to bounce the stock launcher. */
    val overrideEnabled: Boolean = true,
    val sleepLabel: String = "Off",
    val quickMenu: Boolean = false,
    val safeMode: Boolean = false,
    val safeReason: String = "",
    /** True while the idle screensaver is showing. */
    val idle: Boolean = false,
    // ---- names and pictures ----
    val renamedPackages: Set<String> = emptySet(),
    val customImagePackages: Set<String> = emptySet(),
    // ---- smart home ----
    val smartTiles: List<SmartTile> = emptyList(),
    // ---- auto-start after boot ----
    val autoStartPackage: String = "",
    val autoStartLabel: String = "",
    val autoStartDelay: Int = 8,
    val autoStart: AutoStartState? = null,
    // ---- dialogs opened from outside the compose tree ----
    val deviceInfoOpen: Boolean = false,
    val apkInstallerOpen: Boolean = false,
    val editor: EditorInfo? = null,
    val editorDialog: Boolean = false,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(application)
    private val prefs = LauncherPrefs(application)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    @Volatile
    private var installed: List<AppEntry> = emptyList()

    @Volatile
    private var customBanners: Map<String, ImageBitmap> = emptyMap()

    @Volatile
    private var customImagePackages: Set<String> = emptySet()

    private var showHidden = false
    private var weather: WeatherState? = null
    private var crypto: List<CryptoClient.Coin> = emptyList()
    private var cryptoJob: Job? = null
    private var quickMenu = false
    private var safeMode = false
    private var safeReason = ""
    private var idle = false
    private var sleepChoice = 0
    private var weatherJob: Job? = null
    private var autoStart: AutoStartState? = null
    private var autoStartJob: Job? = null
    private var deviceInfoOpen = false
    private var apkInstallerOpen = false
    private var editorServer: WebEditorServer? = null
    private var editor: EditorInfo? = null
    private var editorDialog = false
    private var editorStopJob: Job? = null

    @Volatile
    private var lastTouch = SystemClock.elapsedRealtime()

    @Volatile
    private var foreground = false

    @Volatile
    private var busy = false

    @Volatile
    private var lastRefresh = 0L

    init {
        refresh()
        startWeather()
        startCrypto()
        startIdleWatcher()
    }

    // ---- loading ----

    /** Re-scan installed apps and custom tile pictures. Called on start and when packages change. */
    fun refresh() {
        lastRefresh = SystemClock.elapsedRealtime()
        viewModelScope.launch(Dispatchers.Default) {
            installed = repo.loadApps()
            loadTileImages()
            if (!prefs.hasRows()) seedDefaultRows()
            publish()
        }
    }

    /** Cheap to call often: only rescans if the last scan was a while ago. Package changes call [refresh] directly. */
    fun refreshIfStale() {
        if (SystemClock.elapsedRealtime() - lastRefresh > STALE_MS) refresh()
    }

    private fun loadTileImages() {
        val context = getApplication<Application>()
        customBanners = TileImages.loadAll(context)
        customImagePackages = TileImages.packagesWithImages(context)
    }

    /** Called after a tile picture is added or removed. */
    fun reloadTileImages() {
        viewModelScope.launch(Dispatchers.Default) {
            loadTileImages()
            publish()
        }
    }

    /** The apps as Android reports them, before any custom names or pictures. Used by the phone editor. */
    fun snapshotApps(): List<AppEntry> = installed

    /** Re-publish after something outside the view model changed the saved data. */
    fun reload() = publish()

    private fun seedDefaultRows() {
        val installedPackages = installed.map { it.packageName }.toSet()
        val names = DefaultRows.rows.keys.toList()
        prefs.setRowNames(names)
        for ((name, wanted) in DefaultRows.rows) {
            val picked = wanted.filter { it in installedPackages }.toMutableList()
            if (name == DefaultRows.GAMES_ROW) {
                installed.filter { it.isGame && it.packageName !in picked }
                    .forEach { picked.add(it.packageName) }
            }
            prefs.setRowApps(name, picked.distinct())
        }
    }

    private fun publish() {
        val labels = prefs.appLabels()
        val banners = customBanners
        val decorated = installed.map { app ->
            val name = labels[app.packageName]
            val picture = banners[app.packageName]
            if (name == null && picture == null) app else app.copy(label = name ?: app.label, banner = picture ?: app.banner)
        }
        val byPackage = decorated.associateBy { it.packageName }
        val hidden = prefs.hidden()
        val userRows = prefs.userRows()
        val names = prefs.rowNames()
        val contents = names.associateWith { prefs.rowApps(it) }
        val rows = names.map { name ->
            RowState(
                name = name,
                apps = contents.getValue(name)
                    .mapNotNull { byPackage[it] }
                    .filter { it.packageName !in hidden },
            )
        }.filter { it.apps.isNotEmpty() || it.name in userRows || it.name == DefaultRows.GAMES_ROW }

        val autoStartPackage = prefs.autoStartPackage()

        _state.value = UiState(
            rows = rows,
            rowNames = names,
            rowContents = contents,
            allApps = decorated.filter { it.packageName !in hidden }.sortedBy { it.label.lowercase() },
            hiddenApps = decorated.filter { it.packageName in hidden }.sortedBy { it.label.lowercase() },
            showHidden = showHidden,
            recents = prefs.recents().mapNotNull { byPackage[it] }.filter { it.packageName !in hidden },
            settings = prefs.settings(),
            keymap = prefs.keymap(),
            weather = weather,
            crypto = crypto,
            overrideEnabled = prefs.overrideEnabled(),
            sleepLabel = sleepLabel(),
            quickMenu = quickMenu,
            safeMode = safeMode,
            safeReason = safeReason,
            idle = idle,
            renamedPackages = labels.keys,
            customImagePackages = customImagePackages,
            smartTiles = SmartHome.parse(prefs.smartTilesJson()),
            autoStartPackage = autoStartPackage,
            autoStartLabel = byPackage[autoStartPackage]?.label.orEmpty(),
            autoStartDelay = prefs.autoStartDelay(),
            autoStart = autoStart,
            deviceInfoOpen = deviceInfoOpen,
            apkInstallerOpen = apkInstallerOpen,
            editor = editor,
            editorDialog = editorDialog,
        )
    }

    // ---- app placement ----

    fun recordLaunch(packageName: String) {
        prefs.pushRecent(packageName)
        publish()
    }

    fun addToRow(packageName: String, row: String) {
        val current = prefs.rowApps(row)
        if (packageName !in current) prefs.setRowApps(row, current + packageName)
        publish()
    }

    fun removeFromRow(packageName: String, row: String) {
        prefs.setRowApps(row, prefs.rowApps(row) - packageName)
        publish()
    }

    /** delta = -1 moves left, +1 moves right. */
    fun move(packageName: String, row: String, delta: Int) {
        val list = prefs.rowApps(row).toMutableList()
        val index = list.indexOf(packageName)
        val target = index + delta
        if (index < 0 || target !in list.indices) return
        list.removeAt(index)
        list.add(target, packageName)
        prefs.setRowApps(row, list)
        publish()
    }

    fun hide(packageName: String) {
        prefs.setHidden(prefs.hidden() + packageName)
        publish()
    }

    fun unhide(packageName: String) {
        prefs.setHidden(prefs.hidden() - packageName)
        publish()
    }

    fun toggleShowHidden() {
        showHidden = !showHidden
        publish()
    }

    // ---- custom names and pictures ----

    /** A null or blank [name] goes back to the app's own name. */
    fun setAppLabel(packageName: String, name: String?) {
        prefs.setAppLabel(packageName, name)
        publish()
    }

    fun removeTileImage(packageName: String) {
        TileImages.delete(getApplication(), packageName)
        reloadTileImages()
    }

    // ---- row management ----

    /** Returns false if the name is empty, reserved, or already used. */
    fun addRow(rawName: String): Boolean {
        val name = cleanRowName(rawName, ignore = null) ?: return false
        prefs.setRowNames(prefs.rowNames() + name)
        prefs.setRowApps(name, emptyList())
        prefs.setUserRows(prefs.userRows() + name)
        publish()
        return true
    }

    /** Renames a row in place, keeping its position and apps. Returns false for a bad or taken name. */
    fun renameRow(old: String, rawNew: String): Boolean {
        val name = cleanRowName(rawNew, ignore = old) ?: return false
        if (name == old) return true
        val apps = prefs.rowApps(old)
        prefs.setRowNames(prefs.rowNames().map { if (it == old) name else it })
        prefs.setRowApps(name, apps)
        prefs.removeRow(old)
        val user = prefs.userRows()
        if (old in user) prefs.setUserRows(user - old + name)
        publish()
        return true
    }

    /** Deletes the row only. Its apps stay installed and still appear in "All apps". */
    fun deleteRow(name: String) {
        prefs.setRowNames(prefs.rowNames() - name)
        prefs.removeRow(name)
        prefs.setUserRows(prefs.userRows() - name)
        publish()
    }

    /** delta = -1 moves the row up, +1 moves it down. Counts all rows, including hidden-because-empty ones. */
    fun moveRow(name: String, delta: Int) {
        val names = prefs.rowNames().toMutableList()
        val index = names.indexOf(name)
        val target = index + delta
        if (index < 0 || target !in names.indices) return
        names.removeAt(index)
        names.add(target, name)
        prefs.setRowNames(names)
        publish()
    }

    private fun cleanRowName(raw: String, ignore: String?): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(MAX_ROW_NAME)
        if (name.isEmpty()) return null
        if (RESERVED_NAMES.any { it.equals(name, ignoreCase = true) }) return null
        val clash = prefs.rowNames().any { it != ignore && it.equals(name, ignoreCase = true) }
        return if (clash) null else name
    }

    // ---- settings ----

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val old = prefs.settings()
        val new = change(old)
        prefs.setSettings(new)
        val weatherChanged = old.showWeather != new.showWeather ||
            old.fahrenheit != new.fahrenheit ||
            old.weatherName != new.weatherName ||
            old.weatherLat.toString() != new.weatherLat.toString() ||
            old.weatherLon.toString() != new.weatherLon.toString()
        if (weatherChanged) startWeather()
        if (old.showCrypto != new.showCrypto || old.cryptoCoins != new.cryptoCoins) startCrypto()
        publish()
    }

    /** Looks up [query] as a place name and, if found, uses it for the weather. */
    fun setWeatherLocation(query: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { WeatherClient.geocode(query.trim()) }
            if (place == null) {
                onResult("Couldn't find \"${query.trim()}\". Check the spelling and the internet connection.")
                return@launch
            }
            updateSettings {
                it.copy(
                    weatherName = place.name,
                    weatherLat = place.lat,
                    weatherLon = place.lon,
                    showWeather = true,
                )
            }
            onResult("Weather set to ${place.name}")
        }
    }

    private fun startWeather() {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val s = prefs.settings()
                var ok = true
                if (s.showWeather && s.hasLocation) {
                    val snap = WeatherClient.load(s.weatherLat, s.weatherLon, s.fahrenheit)
                    if (snap != null) {
                        weather = WeatherState(snap.text, s.weatherName, snap.forecast)
                        publish()
                    } else {
                        ok = false
                    }
                } else if (weather != null) {
                    weather = null
                    publish()
                }
                delay(if (ok) WEATHER_REFRESH_MS else WEATHER_RETRY_MS)
            }
        }
    }

    /** Sets the coins shown on the markets card. Accepts symbols ("btc, eth") or CoinGecko ids. */
    fun setCryptoCoins(input: String, onResult: (String) -> Unit) {
        val ids = CryptoClient.normalize(input)
        if (ids.isEmpty()) {
            onResult("Type at least one coin, like btc, eth, sol")
            return
        }
        updateSettings { it.copy(cryptoCoins = ids, showCrypto = true) }
        onResult("Markets: $ids")
    }

    private fun startCrypto() {
        cryptoJob?.cancel()
        cryptoJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val s = prefs.settings()
                var ok = true
                if (s.showCrypto) {
                    val list = CryptoClient.prices(s.cryptoCoins)
                    if (list != null) {
                        crypto = list
                        publish()
                    } else {
                        ok = false
                    }
                } else if (crypto.isNotEmpty()) {
                    crypto = emptyList()
                    publish()
                }
                delay(if (ok) CRYPTO_REFRESH_MS else CRYPTO_RETRY_MS)
            }
        }
    }

    // ---- remote button remapping ----

    fun setKeyMapping(keyCode: Int, packageName: String) {
        prefs.setKeymap(prefs.keymap() + (keyCode to packageName))
        publish()
    }

    fun clearKeyMapping(keyCode: Int) {
        prefs.setKeymap(prefs.keymap() - keyCode)
        publish()
    }

    // ---- Home override ----

    fun toggleOverride() = setOverride(!prefs.overrideEnabled())

    fun setOverride(enabled: Boolean) {
        prefs.setOverrideEnabled(enabled)
        publish()
    }

    // ---- quick menu (opened by pressing Home while already on the home screen) ----

    fun toggleQuickMenu() {
        quickMenu = !quickMenu
        publish()
    }

    fun closeQuickMenu() {
        quickMenu = false
        publish()
    }

    // ---- sleep timer (runs in the accessibility service) ----

    /** Cycles Off, 15, 30, 60, 90 minutes. Returns a message to show the user. */
    fun cycleSleep(): String {
        val service = HomeOverrideService.instance
            ?: return "Turn on the accessibility service first (see the README)."
        val choices = listOf(0, 15, 30, 60, 90)
        sleepChoice = choices[(choices.indexOf(sleepChoice) + 1) % choices.size]
        service.setSleepTimer(sleepChoice)
        publish()
        return if (sleepChoice == 0) {
            "Sleep timer off"
        } else {
            "Locking the screen in $sleepChoice minutes (best effort on Google TV)"
        }
    }

    private fun sleepLabel(): String {
        val minutes = HomeOverrideService.sleepRemainingMinutes()
        return if (minutes > 0) "$minutes min left" else "Off"
    }

    // ---- smart home ----

    /** Runs a smart-home tile off the main thread and reports a short message. */
    fun runSmartTile(tile: SmartTile, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) { SmartHome.run(prefs.haUrl(), prefs.haToken(), tile) }
            onResult(message)
        }
    }

    // ---- auto-start after boot ----

    /** An empty [packageName] turns auto-start off. */
    fun setAutoStart(packageName: String, delaySeconds: Int) {
        prefs.setAutoStart(packageName, delaySeconds.coerceIn(0, 60))
        publish()
    }

    /**
     * Counts down, then opens the chosen app. Meant for the first launch after a reboot.
     * Any button press during the countdown cancels it.
     */
    fun scheduleAutoStart() {
        val packageName = prefs.autoStartPackage()
        if (packageName.isBlank()) return
        autoStartJob?.cancel()
        autoStartJob = viewModelScope.launch {
            var waited = 0
            while (installed.isEmpty() && waited < 50) {
                delay(100)
                waited++
            }
            val app = installed.firstOrNull { it.packageName == packageName } ?: return@launch
            val label = prefs.appLabels()[packageName] ?: app.label
            var left = prefs.autoStartDelay()
            while (left > 0) {
                if (safeMode) {
                    autoStart = null
                    publish()
                    return@launch
                }
                autoStart = AutoStartState(label, left)
                publish()
                delay(1_000)
                left--
            }
            autoStart = null
            prefs.pushRecent(packageName)
            publish()
            getApplication<Application>().startActivity(app.intent)
        }
    }

    /** Returns true if a countdown was running (so the key press that cancelled it can be swallowed). */
    fun cancelAutoStart(): Boolean {
        if (autoStart == null) return false
        autoStartJob?.cancel()
        autoStart = null
        publish()
        return true
    }

    // ---- device info and APK installer dialogs ----

    fun openDeviceInfo() {
        deviceInfoOpen = true
        publish()
    }

    fun closeDeviceInfo() {
        deviceInfoOpen = false
        publish()
    }

    fun openApkInstaller() {
        apkInstallerOpen = true
        publish()
    }

    fun closeApkInstaller() {
        apkInstallerOpen = false
        publish()
    }

    // ---- phone editor ----

    /** Starts the web editor (or just re-opens its info if it's already running). Errors go to [onError]. */
    fun startEditor(onError: (String) -> Unit) {
        if (editorServer != null) {
            editorDialog = true
            publish()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val pin = (100_000..999_999).random().toString()
            val server = WebEditorServer(getApplication(), this@LauncherViewModel, pin)
            if (!server.start()) {
                withContext(Dispatchers.Main) { onError("Couldn't start the phone editor (no free port).") }
                return@launch
            }
            val ip = DeviceInfo.ipAddresses().firstOrNull()?.second
            editorServer = server
            editor = EditorInfo(url = "http://${ip ?: "<box address>"}:${server.port}", pin = pin)
            editorDialog = true
            publish()

            editorStopJob?.cancel()
            editorStopJob = launch {
                delay(EDITOR_MAX_MS)
                stopEditor()
            }
        }
    }

    /** Hides the info card but leaves the editor running. */
    fun closeEditorDialog() {
        editorDialog = false
        publish()
    }

    fun stopEditor() {
        editorStopJob?.cancel()
        editorStopJob = null
        editorServer?.stop()
        editorServer = null
        editor = null
        editorDialog = false
        publish()
    }

    // ---- safe mode ----

    fun openSafeMode(reason: String = "") {
        safeMode = true
        safeReason = reason
        publish()
    }

    fun closeSafeMode() {
        safeMode = false
        safeReason = ""
        publish()
    }

    /** Called when the previous launch never got far enough to clear its "starting" flag. */
    fun enterCrashSafeMode() {
        prefs.setOverrideEnabled(false)
        openSafeMode("The launcher didn't start cleanly last time, so the Home override was switched off.")
    }

    // ---- idle screensaver ----

    fun touch() {
        lastTouch = SystemClock.elapsedRealtime()
    }

    fun setForeground(isForeground: Boolean) {
        foreground = isForeground
        touch()
        if (!isForeground && idle) {
            idle = false
            publish()
        }
    }

    /** Dialogs open: don't start the screensaver underneath them. */
    fun setBusy(isBusy: Boolean) {
        busy = isBusy
        if (!isBusy) touch()
    }

    fun wake() {
        touch()
        if (idle) {
            idle = false
            publish()
        }
    }

    private fun startIdleWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(IDLE_CHECK_MS)
                val minutes = prefs.settings().idleMinutes
                val quiet = SystemClock.elapsedRealtime() - lastTouch > minutes * 60_000L
                if (minutes > 0 && foreground && !busy && !idle && quiet) {
                    idle = true
                    publish()
                }
            }
        }
    }

    // ---- layout backup ----

    private fun backupFile(): File? =
        getApplication<Application>().getExternalFilesDir("backup")?.let { File(it, "layout.json") }

    /** Writes rows, hidden apps, names, settings and mappings to a file. Returns a message to show. */
    fun backup(): String {
        val file = backupFile() ?: return "No storage available for the backup."
        return try {
            file.writeText(prefs.exportJson())
            "Saved. Copy it off with: adb pull ${file.path}"
        } catch (e: Exception) {
            "Backup failed: ${e.message}"
        }
    }

    /** Replaces the current layout and settings with the saved backup. Returns a message to show. */
    fun restore(): String {
        val file = backupFile()
        if (file == null || !file.exists()) return "No backup found. Make one first, or adb push layout.json to the backup folder."
        val text = try {
            file.readText()
        } catch (e: Exception) {
            return "That backup file couldn't be read. Nothing was changed."
        }
        return if (restoreFromJson(text)) "Layout restored." else "That backup file couldn't be read. Nothing was changed."
    }

    /** Used by [restore] and by the phone editor's upload. */
    fun restoreFromJson(text: String): Boolean {
        if (!prefs.importJson(text)) return false
        showHidden = false
        startWeather()
        startCrypto()
        publish()
        return true
    }

    override fun onCleared() {
        editorServer?.stop()
        super.onCleared()
    }

    private companion object {
        const val MAX_ROW_NAME = 30
        val RESERVED_NAMES = listOf("All apps", "Hidden apps", "Recent", "Quick settings", "Smart home")
        const val WEATHER_REFRESH_MS = 30 * 60_000L
        const val WEATHER_RETRY_MS = 2 * 60_000L
        const val CRYPTO_REFRESH_MS = 5 * 60_000L
        const val CRYPTO_RETRY_MS = 2 * 60_000L
        const val IDLE_CHECK_MS = 3_000L
        const val EDITOR_MAX_MS = 30 * 60_000L
        const val STALE_MS = 10 * 60_000L
    }
}
