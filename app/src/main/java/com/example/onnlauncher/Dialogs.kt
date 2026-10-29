@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.example.onnlauncher

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.mutableStateListOf
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import android.view.KeyEvent as AndroidKeyEvent

/**
 * Holding OK on a tile opens a menu while OK is still down. Without this, letting go of OK would
 * "click" whatever the menu focused first. [arm] is called when a hold opens a menu, and every
 * dialog window swallows the matching release.
 */
object OkGuard {
    @Volatile
    private var until = 0L

    fun arm() {
        until = SystemClock.uptimeMillis() + 5_000L
    }

    /** Returns true if this key event should be ignored. */
    fun swallow(event: AndroidKeyEvent): Boolean {
        if (SystemClock.uptimeMillis() > until) return false
        val isOk = event.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
            event.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        if (!isOk) return false
        if (event.action == AndroidKeyEvent.ACTION_UP) {
            until = 0L
            return true
        }
        if (event.repeatCount > 0) return true
        until = 0L // a fresh press: the hold is over
        return false
    }
}

private val ErrorText = Color(0xFFFF8A80)

/** A centered dialog card. Pass a width in [modifier], e.g. Modifier.width(520.dp). */
@Composable
fun Panel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier
                .onPreviewKeyEvent { OkGuard.swallow(it.nativeKeyEvent) }
                .clip(RoundedCornerShape(LocalLauncherTheme.current.corner + 4.dp))
                .background(LocalLauncherTheme.current.panel)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = LocalLauncherTheme.current.text)
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

/** A scrolling column of buttons. The first one takes focus when the list appears. */
@Composable
fun ActionList(actions: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    LazyColumn(
        modifier = modifier.heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(actions) { index, action ->
            Button(
                onClick = action.second,
                modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
            ) {
                Text(action.first)
            }
        }
    }
}

/** Long-press menu for an app tile. Every action closes the menu after it runs. */
@Composable
fun AppMenu(
    title: String,
    actions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    Panel(title, onDismiss, Modifier.width(440.dp)) {
        ActionList(
            actions.map { action ->
                action.first to {
                    // Run first: an action may open another dialog, which onDismiss must not close.
                    action.second()
                    onDismiss()
                }
            },
        )
    }
}

/** Prompt for a name. [onConfirm] returns false to keep the dialog open and show an error. */
@Composable
fun NameInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { field.requestFocus() }
    }

    Panel(title, onDismiss, Modifier.width(460.dp)) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it.take(40)
                error = false
            },
            singleLine = true,
            textStyle = TextStyle(color = LocalLauncherTheme.current.text, fontSize = 20.sp),
            cursorBrush = SolidColor(LocalLauncherTheme.current.text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LocalLauncherTheme.current.field)
                .padding(12.dp)
                .focusRequester(field),
        )
        if (error) {
            Text(
                text = "That didn't work: it may be empty, reserved, already used, or not found.",
                color = ErrorText,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.size(16.dp))
        Row {
            Button(
                onClick = {
                    if (onConfirm(text)) {
                        onDismiss()
                    } else {
                        error = true
                    }
                },
            ) { Text(confirmLabel) }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    }
}

/** Filter all apps by name. The remote's mic button works in the text field if Gboard is set up for voice. */
@Composable
fun SearchDialog(
    apps: List<AppEntry>,
    tileWidth: Dp,
    onLaunch: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { field.requestFocus() }
    }
    val query = text.trim()
    val results = remember(query, apps) {
        if (query.isEmpty()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    Panel("Search apps", onDismiss, Modifier.fillMaxWidth(0.9f)) {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(color = LocalLauncherTheme.current.text, fontSize = 22.sp),
            cursorBrush = SolidColor(LocalLauncherTheme.current.text),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(LocalLauncherTheme.current.field)
                .padding(14.dp)
                .focusRequester(field),
        )
        Spacer(Modifier.size(16.dp))
        if (results.isEmpty()) {
            Text("No apps match \"$query\".", color = LocalLauncherTheme.current.subtle)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(results, key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        tileWidth = tileWidth,
                        onClick = {
                            onLaunch(app)
                            onDismiss()
                        },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    settings: AppSettings,
    overrideEnabled: Boolean,
    showHidden: Boolean,
    hasHidden: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onToggleOverride: () -> Unit,
    onToggleHidden: () -> Unit,
    onNewRow: () -> Unit,
    onSetLocation: () -> Unit,
    onSetCoins: () -> Unit,
    hasTmdbKey: Boolean,
    onSetTmdbKey: () -> Unit,
    wallpaperSource: String,
    onSetWallpaperSource: () -> Unit,
    onSyncWallpapers: () -> Unit,
    onTasteTest: () -> Unit,
    nowPlayingOn: Boolean,
    onSetupNowPlaying: () -> Unit,
    dnsOn: Boolean,
    onToggleDns: () -> Unit,
    onCheckUpdate: () -> Unit,
    autoStartLabel: String,
    autoStartDelay: Int,
    onPickAutoStart: () -> Unit,
    onCycleAutoStartDelay: () -> Unit,
    onPhoneEditor: () -> Unit,
    onDeviceInfo: () -> Unit,
    onInstallApks: () -> Unit,
    onRemap: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSafeMode: () -> Unit,
    onAndroidSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun onOff(value: Boolean) = if (value) "On" else "Off"

    val actions = buildList<Pair<String, () -> Unit>> {
        add(
            (if (settings.autoTheme) "Theme (when auto is off): " else "Theme: ") +
                Themes.get(settings.themeIndex).name to {
                onChange { it.copy(themeIndex = (it.themeIndex + 1) % Themes.all.size) }
            },
        )
        add(
            "Auto day/night theme: ${onOff(settings.autoTheme)}" to {
                onChange { it.copy(autoTheme = !it.autoTheme) }
            },
        )
        if (settings.autoTheme) {
            add(
                "Day theme: ${Themes.get(settings.dayTheme).name}" to {
                    onChange { it.copy(dayTheme = (it.dayTheme + 1) % Themes.all.size) }
                },
            )
            add(
                "Night theme: ${Themes.get(settings.nightTheme).name}" to {
                    onChange { it.copy(nightTheme = (it.nightTheme + 1) % Themes.all.size) }
                },
            )
        }
        add(
            "Tile size: ${Options.tileSizeNames[settings.tileSize]}" to {
                onChange { it.copy(tileSize = (it.tileSize + 1) % Options.tileWidths.size) }
            },
        )
        add(
            "Font: ${AppFonts.names[settings.fontIndex]}" to {
                onChange { it.copy(fontIndex = (it.fontIndex + 1) % AppFonts.names.size) }
            },
        )
        add(
            "Tile corners: ${Options.roundnessNames[settings.roundness]}" to {
                onChange { it.copy(roundness = (it.roundness + 1) % Options.roundnessDp.size) }
            },
        )
        add(
            "Accent color: ${Options.accents[settings.accentIndex].first}" to {
                onChange { it.copy(accentIndex = (it.accentIndex + 1) % Options.accents.size) }
            },
        )
        add(
            "Wallpaper changes every: ${settings.wallpaperMinutes} min" to {
                onChange { it.copy(wallpaperMinutes = Options.next(Options.wallpaperMinutes, it.wallpaperMinutes)) }
            },
        )
        add("Wallpaper source (NAS): ${wallpaperSource.ifBlank { "not set" }}" to onSetWallpaperSource)
        if (wallpaperSource.isNotBlank()) add("Sync wallpapers now" to onSyncWallpapers)
        add(
            "Screensaver after: ${if (settings.idleMinutes == 0) "Off" else "${settings.idleMinutes} min"}" to {
                onChange { it.copy(idleMinutes = Options.next(Options.idleMinutes, it.idleMinutes)) }
            },
        )
        add(
            "Recently used row: ${onOff(settings.showRecents)}" to {
                onChange { it.copy(showRecents = !it.showRecents) }
            },
        )
        add("Weather: ${onOff(settings.showWeather)}" to { onChange { it.copy(showWeather = !it.showWeather) } })
        add(
            "Temperature units: ${if (settings.fahrenheit) "°F" else "°C"}" to {
                onChange { it.copy(fahrenheit = !it.fahrenheit) }
            },
        )
        add("Weather location: ${settings.weatherName.ifBlank { "not set" }}" to onSetLocation)
        add("Trending row (TMDB): ${onOff(settings.showTrending)}" to { onChange { it.copy(showTrending = !it.showTrending) } })
        add("Hero banner: ${onOff(settings.showHero)}" to { onChange { it.copy(showHero = !it.showHero) } })
        add("Hero style: ${if (settings.heroFull) "Full screen" else "Banner"}" to { onChange { it.copy(heroFull = !it.heroFull) } })
        add("Screensaver backdrops: ${onOff(settings.idleBackdrops)}" to { onChange { it.copy(idleBackdrops = !it.idleBackdrops) } })
        add("What's playing: ${if (nowPlayingOn) "on" else "needs setup"}" to onSetupNowPlaying)
        add(
            "Titles shown: ${Options.titleModes[settings.titleMode]}" to {
                onChange { it.copy(titleMode = (it.titleMode + 1) % Options.titleModes.size) }
            },
        )
        add("Take the taste test..." to onTasteTest)
        add("TMDB API key: ${if (hasTmdbKey) "saved" else "not set (easier from your phone)"}" to onSetTmdbKey)
        add(
            "Where to watch region: ${Options.regions[settings.tmdbRegion]}" to {
                onChange { it.copy(tmdbRegion = (it.tmdbRegion + 1) % Options.regions.size) }
            },
        )
        add("Ad-blocking DNS (AdGuard): ${onOff(dnsOn)}" to onToggleDns)
        add("Check for updates" to onCheckUpdate)
        add("Home layout: ${if (settings.dashboard) "Cards" else "Compact"}" to { onChange { it.copy(dashboard = !it.dashboard) } })
        add("Forecast card: ${onOff(settings.showForecast)}" to { onChange { it.copy(showForecast = !it.showForecast) } })
        add("Markets card: ${onOff(settings.showCrypto)}" to { onChange { it.copy(showCrypto = !it.showCrypto) } })
        add("Markets coins: ${settings.cryptoCoins.ifBlank { "none" }}" to onSetCoins)
        add("New row..." to onNewRow)
        add("Edit from your phone..." to onPhoneEditor)
        add("Device info (IP, ADB, storage)" to onDeviceInfo)
        add("Install APKs..." to onInstallApks)
        add("Open at startup: ${autoStartLabel.ifBlank { "nothing" }}" to onPickAutoStart)
        if (autoStartLabel.isNotBlank()) {
            add("Startup wait: ${if (autoStartDelay == 0) "none" else "$autoStartDelay s"}" to onCycleAutoStartDelay)
        }
        if (hasHidden || showHidden) {
            add((if (showHidden) "Hide the hidden-apps row" else "Show the hidden-apps row") to onToggleHidden)
        }
        add("Home override: ${onOff(overrideEnabled)}" to onToggleOverride)
        add("Remap remote buttons..." to onRemap)
        add("Back up layout now" to onBackup)
        add("Restore layout from backup" to onRestore)
        add("Safe mode / recovery..." to onSafeMode)
        add("Android settings" to onAndroidSettings)
        add("Close" to onDismiss)
    }

    Panel("Settings", onDismiss, Modifier.width(560.dp)) {
        ActionList(actions)
    }
}

private enum class RemapMode { LIST, LEARN, PICK }

/** Assign remote buttons to apps. Needs the accessibility service for the buttons to actually act. */
@Composable
fun RemapDialog(
    keymap: Map<Int, String>,
    apps: List<AppEntry>,
    onSet: (keyCode: Int, packageName: String) -> Unit,
    onClear: (keyCode: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(RemapMode.LIST) }
    var learned by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }
    val labels = remember(apps) { apps.associate { it.packageName to it.label } }

    val title = when (mode) {
        RemapMode.LIST -> "Remote buttons"
        RemapMode.LEARN -> "Press a button"
        RemapMode.PICK -> "Choose an app"
    }

    Panel(
        title = title,
        onDismiss = {
            if (mode == RemapMode.LIST) {
                onDismiss()
            } else {
                mode = RemapMode.LIST
            }
        },
        modifier = Modifier.width(600.dp),
    ) {
        when (mode) {
            RemapMode.LIST -> {
                Text(
                    "A mapped button opens its app instead of its normal action. It needs the accessibility " +
                        "service, and only works for buttons Android hands to apps. Some dedicated app " +
                        "buttons may be handled by the system first and can't be remapped.",
                    color = LocalLauncherTheme.current.subtle,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.size(12.dp))
                val actions = buildList<Pair<String, () -> Unit>> {
                    for ((code, pkg) in keymap) {
                        add("${KeyBus.name(code)}  ->  ${labels[pkg] ?: pkg}   (press to remove)" to { onClear(code) })
                    }
                    add(
                        "Add a mapping..." to {
                            note = ""
                            mode = RemapMode.LEARN
                        },
                    )
                    add("Close" to onDismiss)
                }
                ActionList(actions)
            }

            RemapMode.LEARN -> {
                DisposableEffect(Unit) {
                    KeyBus.learning = true
                    onDispose { KeyBus.learning = false }
                }
                val handle = { code: Int ->
                    if (mode == RemapMode.LEARN) {
                        if (code in KeyBus.BLOCKED) {
                            note = "${KeyBus.name(code)} can't be remapped. Try another button."
                        } else {
                            learned = code
                            mode = RemapMode.PICK
                        }
                    }
                }
                LaunchedEffect(Unit) { KeyBus.events.collect { handle(it) } }

                val catcher = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(100)
                    runCatching { catcher.requestFocus() }
                }

                Text(
                    "Press the remote button you want to remap. Back cancels.",
                    color = LocalLauncherTheme.current.text,
                    fontSize = 18.sp,
                )
                if (note.isNotEmpty()) {
                    Text(note, color = ErrorText, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                // Invisible, focused, and swallowing every key except Back so it can be read.
                Box(
                    Modifier
                        .size(1.dp)
                        .focusRequester(catcher)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            if (event.type == KeyEventType.KeyDown && code != AndroidKeyEvent.KEYCODE_BACK) {
                                handle(code)
                                true
                            } else {
                                false
                            }
                        },
                )
            }

            RemapMode.PICK -> {
                Text(
                    "Open which app when you press ${KeyBus.name(learned)}?",
                    color = LocalLauncherTheme.current.text,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.size(12.dp))
                val choices = apps.map { app ->
                    app.label to {
                        onSet(learned, app.packageName)
                        mode = RemapMode.LIST
                    }
                }
                ActionList(choices + ("Cancel" to { mode = RemapMode.LIST }))
            }
        }
    }
}

/** Recovery options that don't depend on the launcher itself working properly. */
@Composable
fun SafeModeDialog(
    reason: String,
    overrideEnabled: Boolean,
    onToggleOverride: () -> Unit,
    onOpenStockHome: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Panel("Safe mode", onDismiss, Modifier.width(560.dp)) {
        if (reason.isNotBlank()) {
            Text(reason, color = Color(0xFFFFCA28), fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            "Use this if the launcher is ever in your way. The stock Google TV launcher is never disabled, " +
                "so turning the Home override off puts the Home button back to normal.",
            color = LocalLauncherTheme.current.subtle,
            fontSize = 14.sp,
        )
        Spacer(Modifier.size(12.dp))
        ActionList(
            listOf(
                (if (overrideEnabled) "Turn Home override OFF" else "Turn Home override ON") to onToggleOverride,
                "Open the stock Google TV home" to onOpenStockHome,
                "Accessibility settings" to onOpenAccessibility,
                "Android settings" to onOpenAndroidSettings,
                "Close" to onDismiss,
            ),
        )
    }
}

/** Opened by pressing Home while already on the home screen. */
@Composable
fun QuickMenuDialog(
    recents: List<AppEntry>,
    sleepLabel: String,
    tileWidth: Dp,
    onLaunch: (AppEntry) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstShortcut = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstShortcut.requestFocus() }
    }

    Panel("Quick menu", onDismiss, Modifier.fillMaxWidth(0.85f)) {
        Text("Recent apps", color = LocalLauncherTheme.current.subtle, fontSize = 16.sp)
        if (recents.isEmpty()) {
            Text(
                "Nothing yet. Apps you open from this launcher show up here.",
                color = LocalLauncherTheme.current.subtle,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(recents.take(8), key = { it.packageName }) { app ->
                    AppTile(
                        app = app,
                        tileWidth = tileWidth,
                        onClick = {
                            onLaunch(app)
                            onDismiss()
                        },
                        onLongClick = {},
                    )
                }
            }
        }

        Spacer(Modifier.size(8.dp))
        Text("Shortcuts", color = LocalLauncherTheme.current.subtle, fontSize = 16.sp)
        val actions = QuickAction.entries
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(actions) { index, action ->
                ShortcutTile(
                    label = if (action == QuickAction.SLEEP) "Sleep timer: $sleepLabel" else action.label,
                    tileWidth = tileWidth,
                    onClick = { onQuickAction(action) },
                    icon = action.icon(),
                    modifier = if (index == 0) Modifier.focusRequester(firstShortcut) else Modifier,
                )
            }
        }

        Spacer(Modifier.size(8.dp))
        Row {
            Button(onClick = onSearch) { Text("Search apps") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSettings) { Text("Settings") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onDismiss) { Text("Close") }
        }
    }
}


/** Facts about the box, with a focus on whether ADB can still reach it. */
@Composable
fun DeviceInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    val close = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        lines = withContext(Dispatchers.IO) { DeviceInfo.collect(context) }
        delay(100)
        runCatching { close.requestFocus() }
    }

    Panel("Device info", onDismiss, Modifier.width(620.dp)) {
        val shown = lines
        if (shown == null) {
            Text("Checking...", color = LocalLauncherTheme.current.subtle)
        } else {
            for ((name, value) in shown) {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(name, color = LocalLauncherTheme.current.subtle, fontSize = 16.sp, modifier = Modifier.width(200.dp))
                    Text(value, color = LocalLauncherTheme.current.text, fontSize = 16.sp)
                }
            }
            Text(
                "If ADB on port 5555 says Not listening, turn wireless debugging back on in Developer options.",
                color = LocalLauncherTheme.current.subtle,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(close)) { Text("Close") }
    }
}

/** Lists APK files in the launcher's apks folder and hands one to the system installer. */
@Composable
fun ApkInstallDialog(
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    fun listApks(): List<File> =
        context.getExternalFilesDir("apks")
            ?.listFiles { f -> f.isFile && f.extension.equals("apk", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    var files by remember { mutableStateOf(listApks()) }

    Panel("Install APKs", onDismiss, Modifier.width(620.dp)) {
        if (files.isEmpty()) {
            Text(
                "No APK files yet. Upload one from the phone editor (Files tab), or from your PC:",
                color = LocalLauncherTheme.current.subtle,
                fontSize = 15.sp,
            )
            Text(
                "adb push app.apk /sdcard/Android/data/com.example.onnlauncher/files/apks/",
                color = LocalLauncherTheme.current.text,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Text("Pick a file to install. Android will ask you to confirm.", color = LocalLauncherTheme.current.subtle, fontSize = 15.sp)
            Spacer(Modifier.size(8.dp))
        }
        val actions = buildList<Pair<String, () -> Unit>> {
            for (file in files) {
                val mb = file.length() / 1_048_576
                add("Install ${file.name} (${mb} MB)" to {
                    onInstall(file)
                    onDismiss()
                })
                add("Delete ${file.name}" to {
                    file.delete()
                    files = listApks()
                })
            }
            add("Close" to onDismiss)
        }
        ActionList(actions)
    }
}

/** Shows the address and PIN for the phone editor. */
@Composable
fun PhoneEditorDialog(
    info: EditorInfo,
    onClose: () -> Unit,
    onStop: () -> Unit,
) {
    Panel("Edit from your phone", onClose, Modifier.width(620.dp)) {
        Text(
            "On a phone or laptop on the same Wi-Fi, open this address and enter the PIN:",
            color = LocalLauncherTheme.current.subtle,
            fontSize = 15.sp,
        )
        Spacer(Modifier.size(12.dp))
        Text(info.url, color = LocalLauncherTheme.current.text, fontSize = 30.sp)
        Spacer(Modifier.size(4.dp))
        Text("PIN  ${info.pin}", color = Color(0xFFFF5252), fontSize = 30.sp)
        Text(
            "It stops by itself after an hour (the phone can extend it), or when you leave the launcher.",
            color = LocalLauncherTheme.current.subtle,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.size(16.dp))
        ActionList(
            listOf(
                "Close (keep it running)" to onClose,
                "Stop the editor" to onStop,
            ),
        )
    }
}

/** Choose one app from the list. [onPick] gets null for the "none" choice. */
@Composable
fun PickAppDialog(
    title: String,
    apps: List<AppEntry>,
    noneLabel: String?,
    onPick: (AppEntry?) -> Unit,
    onDismiss: () -> Unit,
) {
    Panel(title, onDismiss, Modifier.width(520.dp)) {
        val choices = buildList<Pair<String, () -> Unit>> {
            if (noneLabel != null) {
                add(noneLabel to {
                    onPick(null)
                    onDismiss()
                })
            }
            for (app in apps) {
                add(app.label to {
                    onPick(app)
                    onDismiss()
                })
            }
            add("Cancel" to onDismiss)
        }
        ActionList(choices)
    }
}


/** Details for one trending title, with buttons for the installed apps that stream it. */
@Composable
fun TitleDialog(
    detail: TitleDetail,
    region: String,
    onLaunch: (AppEntry) -> Unit,
    onSearch: (AppEntry) -> Unit,
    inList: Boolean,
    onToggleList: () -> Unit,
    onTrailer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val t = detail.title
    val heading = if (t.year.isNotBlank()) "${t.name} (${t.year})" else t.name
    val actions = buildList<Pair<String, () -> Unit>> {
        if (detail.trailerKey.isNotBlank()) add("Watch trailer" to { onTrailer(detail.trailerKey) })
        add((if (inList) "Remove from my list" else "Add to my list") to onToggleList)
        detail.apps.forEach { app ->
            if (DeepLinks.supports(app.packageName)) add("Search for it in ${app.label}" to { onSearch(app) })
            add("Open ${app.label}" to { onLaunch(app) })
        }
        add("Close" to onDismiss)
    }

    Panel(heading, onDismiss, Modifier.width(760.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val poster = detail.bigPoster ?: t.poster
            if (poster != null) {
                Image(
                    bitmap = poster,
                    contentDescription = t.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(150.dp)
                        .height(225.dp)
                        .clip(RoundedCornerShape(theme.corner)),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${if (t.type == "tv") "Series" else "Movie"}  ·  ★ ${String.format("%.1f", t.rating)}",
                    color = theme.subtle,
                    fontSize = 14.sp,
                )
                if (t.overview.isNotBlank()) {
                    Text(t.overview, color = theme.text, fontSize = 14.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                val where = when {
                    detail.loading -> "Checking where to watch..."
                    detail.streaming.isNotEmpty() -> "Streaming in $region on: " + detail.streaming.joinToString(", ")
                    detail.rentOrBuy.isNotEmpty() -> "Not on a streaming service in $region. Rent or buy: " +
                        detail.rentOrBuy.take(4).joinToString(", ")
                    else -> "No streaming information for $region."
                }
                Text(where, color = theme.text, fontSize = 14.sp)
                if (!detail.loading && detail.streaming.isNotEmpty() && detail.apps.isEmpty()) {
                    Text("None of those apps are installed on this box.", color = theme.subtle, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ActionList(actions)
        Spacer(Modifier.height(8.dp))
        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability by JustWatch.",
            color = theme.subtle,
            fontSize = 11.sp,
        )
    }
}


/** Three rounds of six posters: pick the ones you'd watch and the launcher learns your genres. */
@Composable
fun TasteDialog(
    pool: List<TrendingTitle>,
    onFinish: (List<TrendingTitle>) -> Unit,
    onDismiss: () -> Unit,
) {
    val theme = LocalLauncherTheme.current
    val rounds = remember(pool) { pool.chunked(6).filter { it.size >= 3 }.take(3) }
    var round by remember { mutableStateOf(0) }
    val picked = remember { mutableStateListOf<Int>() }
    val first = remember(round) { FocusRequester() }
    LaunchedEffect(round) {
        delay(100)
        runCatching { first.requestFocus() }
    }
    if (rounds.isEmpty()) {
        onDismiss()
        return
    }
    val current = rounds[round]
    val last = round == rounds.lastIndex

    Panel("Taste test: round ${round + 1} of ${rounds.size}", onDismiss, Modifier.width(900.dp)) {
        Text(
            "Pick the ones you'd actually watch. OK selects or unselects.",
            color = theme.subtle,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            current.forEachIndexed { i, t ->
                val on = t.id in picked
                val shape = RoundedCornerShape(theme.corner)
                Column(Modifier.width(120.dp)) {
                    Card(
                        onClick = { if (on) picked.remove(t.id) else picked.add(t.id) },
                        shape = CardDefaults.shape(shape),
                        border = CardDefaults.border(
                            border = if (on) Border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary), shape = shape) else Border.None,
                            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.border), shape = shape),
                        ),
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
                            .let { if (i == 0) it.focusRequester(first) else it },
                    ) {
                        Box(Modifier.fillMaxSize().background(theme.tile)) {
                            val poster = t.poster
                            if (poster != null) {
                                Image(
                                    bitmap = poster,
                                    contentDescription = t.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            if (on) {
                                Text(
                                    "✓ Picked",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        t.name,
                        color = theme.text,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                if (last) onFinish(pool.filter { it.id in picked }) else round++
            }) { Text(if (last) "Finish" else "Next round") }
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    }
}
