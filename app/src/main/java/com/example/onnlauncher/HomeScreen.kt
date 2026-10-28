@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.example.onnlauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import java.io.File
import java.text.DateFormat
import java.util.SimpleTimeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MenuTarget(val app: AppEntry, val row: String?, val isHidden: Boolean)

/** A name prompt: [onConfirm] returns false to keep the dialog open and show an error. */
private data class NameDialog(
    val title: String,
    val initial: String,
    val confirmLabel: String,
    val onConfirm: (String) -> Boolean,
)

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    onLaunch: (AppEntry) -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenStockHome: () -> Unit,
    onAppInfo: (String) -> Unit,
    onForceClose: (String, String) -> Unit,
    onLaunchSearch: (AppEntry, String) -> Unit,
    nowPlayingEnabled: () -> Boolean,
    onSetupNowPlaying: () -> Unit,
    dnsEnabled: () -> Boolean,
    onToggleDns: () -> Unit,
    onUninstall: (String) -> Unit,
    onToast: (String) -> Unit,
    onInstallApk: (File) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val theme = LocalLauncherTheme.current
    val tileWidth = Options.tileWidths[settings.tileSize].dp

    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var remapOpen by remember { mutableStateOf(false) }
    var pickAutoStart by remember { mutableStateOf(false) }

    // While any dialog is up, keep the idle screensaver from starting underneath it.
    val anyDialog = menu != null || nameDialog != null || searchOpen || settingsOpen || remapOpen ||
        pickAutoStart || state.quickMenu || state.safeMode || state.deviceInfoOpen ||
        state.apkInstallerOpen || state.editorDialog || state.titleDetail != null || state.tasteOpen
    LaunchedEffect(anyDialog) { vm.setBusy(anyDialog) }

    val heroes = state.trending.filter { it.backdrop != null }
    val heroOn = settings.showTrending && settings.showHero && heroes.isNotEmpty()
    val heroFull = heroOn && settings.heroFull
    val heroIndex = rememberHeroIndex(heroes.size)

    Box(
        Modifier
            .fillMaxSize()
            .background(theme.backdrop),
    ) {
        WallpaperBackground(settings.wallpaperMinutes)
        if (heroFull) FullHeroBackdrop(heroes[heroIndex])
        // Darken the wallpaper so text and tiles stay readable.
        if (!heroFull) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(theme.washTop, theme.washBottom))),
            )
        }

        ThemePattern(theme.pattern)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "top") {
                TopBar(
                    weather = state.weather,
                    overrideEnabled = state.overrideEnabled,
                    onSearch = { searchOpen = true },
                    onToggleOverride = vm::toggleOverride,
                    onSettings = { settingsOpen = true },
                )
            }
            state.nowPlaying?.let { playing ->
                item(key = "nowplaying") {
                    val entry = state.allApps.firstOrNull { it.packageName == playing.pkg }
                        ?: state.hiddenApps.firstOrNull { it.packageName == playing.pkg }
                    NowPlayingBar(playing, entry?.label ?: playing.pkg) { entry?.let(onLaunch) }
                }
            }
            if (heroFull) {
                item(key = "herofull") { HeroInfo(heroes[heroIndex], vm::openTitle) }
            }
            val forecast = state.weather?.forecast
            val showForecastCard = settings.showForecast && forecast != null
            val showMarketsCard = settings.showCrypto && state.crypto.isNotEmpty()
            val hasInfo = showForecastCard || showMarketsCard
            val emitInfo: LazyListScope.() -> Unit = {
                if (hasInfo) {
                    item(key = "info") {
                        val shownForecast = forecast.takeIf { showForecastCard }
                        val shownCoins = state.crypto.takeIf { showMarketsCard }.orEmpty()
                        if (settings.dashboard) {
                            DashboardCards(shownForecast, shownCoins, state.weather?.place.orEmpty(), settings.fahrenheit)
                        } else {
                            InfoCard(shownForecast, shownCoins)
                        }
                    }
                }
            }
            emitInfo()
            if (settings.showRecents && state.recents.isNotEmpty() && !settings.dashboard) {
                item(key = "recent") {
                    AppRow("Recent", state.recents, tileWidth, onLaunch) { OkGuard.arm(); menu = MenuTarget(it, null, false) }
                }
            }
            state.rows.forEachIndexed { index, row ->
                item(key = "row:" + row.name) {
                    val dockStyle = if (theme.dock && index == 0) {
                        Modifier
                            .background(theme.dockColor, RoundedCornerShape(32.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    } else {
                        Modifier
                    }
                    AppRow(row.name, row.apps, tileWidth, onLaunch, dockStyle) { OkGuard.arm(); menu = MenuTarget(it, row.name, false) }
                }
                if (index == 0) {
                    if (heroOn && !settings.heroFull) {
                        item(key = "hero") { HeroBanner(heroes[heroIndex], vm::openTitle) }
                    }
                    if (settings.showTrending && state.trending.isNotEmpty()) {
                        item(key = "trending") {
                            TrendingRow(Options.titleModes[settings.titleMode], state.trending, tileWidth, vm::openTitle)
                        }
                    }
                    if (state.continueWatching.isNotEmpty()) {
                        item(key = "continue") {
                            TrendingRow("Continue watching", state.continueWatching, tileWidth, vm::openTitle)
                        }
                    }
                    if (state.watchlist.isNotEmpty()) {
                        item(key = "mylist") {
                            TrendingRow("My list", state.watchlist, tileWidth, vm::openTitle)
                        }
                    }
                }
                if (settings.dashboard && index == 0) {
                    item(key = "quick") {
                        QuickRow(state.sleepLabel, tileWidth, compact = true, onQuickAction = onQuickAction)
                    }
                    if (settings.showRecents && state.recents.isNotEmpty()) {
                        item(key = "recent") {
                            AppRow("Recent", state.recents, tileWidth, onLaunch) { OkGuard.arm(); menu = MenuTarget(it, null, false) }
                        }
                    }
                }
            }
            if (state.smartTiles.isNotEmpty()) {
                item(key = "smart") {
                    SmartRow(state.smartTiles, tileWidth) { vm.runSmartTile(it, onToast) }
                }
            }
            if (!settings.dashboard || state.rows.isEmpty()) {
                item(key = "quick") {
                    QuickRow(state.sleepLabel, tileWidth, compact = false, onQuickAction = onQuickAction)
                }
            }
            item(key = "all") {
                AppRow("All apps", state.allApps, tileWidth, onLaunch) { OkGuard.arm(); menu = MenuTarget(it, null, false) }
            }
            if (state.showHidden && state.hiddenApps.isNotEmpty()) {
                item(key = "hidden") {
                    AppRow("Hidden apps", state.hiddenApps, tileWidth, onLaunch) {
                        OkGuard.arm()
                        menu = MenuTarget(it, null, true)
                    }
                }
            }
        }

        if (state.idle) Screensaver(settings.wallpaperMinutes, state.weather, if (settings.idleBackdrops) heroes else emptyList())

        menu?.let { target ->
            val pkg = target.app.packageName
            val actions = buildList<Pair<String, () -> Unit>> {
                add("Open" to { onLaunch(target.app) })
                target.row?.let { row ->
                    add("Remove from $row" to { vm.removeFromRow(pkg, row) })
                    add("Move left" to { vm.move(pkg, row, -1) })
                    add("Move right" to { vm.move(pkg, row, +1) })
                    add("Move row \"$row\" up" to { vm.moveRow(row, -1) })
                    add("Move row \"$row\" down" to { vm.moveRow(row, +1) })
                    add(
                        "Rename row \"$row\"" to {
                            nameDialog = NameDialog("Rename row", row, "Rename") { vm.renameRow(row, it) }
                        },
                    )
                    add("Delete row \"$row\"" to { vm.deleteRow(row) })
                }
                for (name in state.rowNames) {
                    if (pkg !in state.rowContents[name].orEmpty()) {
                        add("Add to $name" to { vm.addToRow(pkg, name) })
                    }
                }
                if (target.isHidden) add("Unhide" to { vm.unhide(pkg) }) else add("Hide" to { vm.hide(pkg) })
                add(
                    "Rename app..." to {
                        nameDialog = NameDialog("Rename app", target.app.label, "Save") {
                            vm.setAppLabel(pkg, it)
                            true
                        }
                    },
                )
                if (pkg in state.renamedPackages) add("Reset app name" to { vm.setAppLabel(pkg, null) })
                if (pkg in state.customImagePackages) {
                    add("Remove custom tile picture" to { vm.removeTileImage(pkg) })
                }
                if (pkg != "com.example.onnlauncher") add("Force close" to { onForceClose(pkg, target.app.label) })
                add("App info" to { onAppInfo(pkg) })
                add("Uninstall" to { onUninstall(pkg) })
            }
            AppMenu(title = target.app.label, actions = actions, onDismiss = { menu = null })
        }

        nameDialog?.let { dialog ->
            NameInputDialog(
                title = dialog.title,
                initial = dialog.initial,
                confirmLabel = dialog.confirmLabel,
                onConfirm = dialog.onConfirm,
                onDismiss = { nameDialog = null },
            )
        }

        if (searchOpen) {
            SearchDialog(
                apps = state.allApps,
                tileWidth = tileWidth,
                onLaunch = onLaunch,
                onDismiss = { searchOpen = false },
            )
        }

        if (settingsOpen) {
            SettingsDialog(
                settings = settings,
                overrideEnabled = state.overrideEnabled,
                showHidden = state.showHidden,
                hasHidden = state.hiddenApps.isNotEmpty(),
                onChange = vm::updateSettings,
                onToggleOverride = vm::toggleOverride,
                onToggleHidden = vm::toggleShowHidden,
                onNewRow = { nameDialog = NameDialog("New row", "", "Create") { vm.addRow(it) } },
                autoStartLabel = state.autoStartLabel,
                autoStartDelay = state.autoStartDelay,
                onPickAutoStart = {
                    settingsOpen = false
                    pickAutoStart = true
                },
                onCycleAutoStartDelay = {
                    vm.setAutoStart(state.autoStartPackage, Options.next(Options.autoStartDelays, state.autoStartDelay))
                },
                onPhoneEditor = {
                    settingsOpen = false
                    vm.startEditor(onToast)
                },
                onDeviceInfo = {
                    settingsOpen = false
                    vm.openDeviceInfo()
                },
                onInstallApks = {
                    settingsOpen = false
                    vm.openApkInstaller()
                },
                onSetLocation = {
                    nameDialog = NameDialog("Weather location (city name)", settings.weatherName, "Save") { text ->
                        if (text.isBlank()) {
                            false
                        } else {
                            vm.setWeatherLocation(text, onToast)
                            true
                        }
                    }
                },
                onTasteTest = {
                    settingsOpen = false
                    vm.openTaste(onToast)
                },
                nowPlayingOn = nowPlayingEnabled(),
                onSetupNowPlaying = {
                    settingsOpen = false
                    onSetupNowPlaying()
                },
                dnsOn = dnsEnabled(),
                onToggleDns = {
                    settingsOpen = false
                    onToggleDns()
                },
                onCheckUpdate = {
                    onToast("Checking for updates...")
                    vm.checkUpdate(onToast)
                },
                hasTmdbKey = state.hasTmdbKey,
                onSetTmdbKey = {
                    nameDialog = NameDialog("TMDB API key (paste it)", "", "Save") { text ->
                        vm.setTmdbKey(text, onToast)
                        text.isNotBlank()
                    }
                },
                onSetCoins = {
                    nameDialog = NameDialog("Coins (like btc, eth, sol)", settings.cryptoCoins, "Save") { text ->
                        vm.setCryptoCoins(text, onToast)
                        text.isNotBlank()
                    }
                },
                onRemap = { remapOpen = true },
                onBackup = { onToast(vm.backup()) },
                onRestore = { onToast(vm.restore()) },
                onSafeMode = { vm.openSafeMode() },
                onAndroidSettings = onOpenAndroidSettings,
                onDismiss = { settingsOpen = false },
            )
        }

        if (pickAutoStart) {
            PickAppDialog(
                title = "Open at startup",
                apps = state.allApps,
                noneLabel = "Don't open anything",
                onPick = { app -> vm.setAutoStart(app?.packageName.orEmpty(), state.autoStartDelay) },
                onDismiss = { pickAutoStart = false },
            )
        }

        if (state.deviceInfoOpen) DeviceInfoDialog(onDismiss = vm::closeDeviceInfo)

        if (state.tasteOpen) TasteDialog(state.tastePool, onFinish = vm::finishTaste, onDismiss = vm::closeTaste)

        state.titleDetail?.let { detail ->
            TitleDialog(
                detail = detail,
                region = Options.regions[settings.tmdbRegion],
                onLaunch = { app ->
                    vm.noteWatched(detail.title)
                    vm.closeTitle()
                    onLaunch(app)
                },
                onSearch = { app ->
                    vm.noteWatched(detail.title)
                    vm.closeTitle()
                    onLaunchSearch(app, detail.title.name)
                },
                inList = state.watchlist.any { it.id == detail.title.id && it.type == detail.title.type },
                onToggleList = { vm.toggleWatchlist(detail.title) },
                onDismiss = vm::closeTitle,
            )
        }

        if (state.apkInstallerOpen) {
            ApkInstallDialog(onInstall = onInstallApk, onDismiss = vm::closeApkInstaller)
        }

        state.editor?.let { info ->
            if (state.editorDialog) {
                PhoneEditorDialog(info = info, onClose = vm::closeEditorDialog, onStop = vm::stopEditor)
            }
        }

        state.autoStart?.let { countdown ->
            AutoStartBanner(countdown, Modifier.align(Alignment.BottomCenter))
        }

        if (remapOpen) {
            RemapDialog(
                keymap = state.keymap,
                apps = state.allApps,
                onSet = vm::setKeyMapping,
                onClear = vm::clearKeyMapping,
                onDismiss = { remapOpen = false },
            )
        }

        if (state.quickMenu) {
            QuickMenuDialog(
                recents = state.recents,
                sleepLabel = state.sleepLabel,
                tileWidth = tileWidth,
                onLaunch = onLaunch,
                onQuickAction = { action ->
                    onQuickAction(action)
                    if (action != QuickAction.SLEEP) vm.closeQuickMenu()
                },
                onSearch = {
                    vm.closeQuickMenu()
                    searchOpen = true
                },
                onSettings = {
                    vm.closeQuickMenu()
                    settingsOpen = true
                },
                onDismiss = vm::closeQuickMenu,
            )
        }

        if (state.safeMode) {
            SafeModeDialog(
                reason = state.safeReason,
                overrideEnabled = state.overrideEnabled,
                onToggleOverride = vm::toggleOverride,
                onOpenStockHome = onOpenStockHome,
                onOpenAccessibility = onOpenAccessibilitySettings,
                onOpenAndroidSettings = onOpenAndroidSettings,
                onDismiss = vm::closeSafeMode,
            )
        }
    }
}

@Composable
private fun rememberNow(): Date {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    return now
}

@Composable
private fun TopBar(
    weather: WeatherState?,
    overrideEnabled: Boolean,
    onSearch: () -> Unit,
    onToggleOverride: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        ClockBlock(weather)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSearch) { Text("Search") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onToggleOverride) {
                Text(if (overrideEnabled) "Home override: ON" else "Home override: OFF")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSettings) { Text("Settings") }
        }
    }
}

@Composable
private fun DashboardCards(
    forecast: WeatherClient.Forecast?,
    coins: List<CryptoClient.Coin>,
    place: String,
    fahrenheit: Boolean,
) {
    val theme = LocalLauncherTheme.current
    val shape = RoundedCornerShape(theme.corner + 10.dp)
    val mono = if (theme.monospace) FontFamily.Monospace else null
    val zone = remember(forecast?.utcOffset) { SimpleTimeZone((forecast?.utcOffset ?: 0) * 1000, "place") }
    val hourFormat = remember(zone) { SimpleDateFormat("h a", Locale.getDefault()).apply { timeZone = zone } }
    val sunFormat = remember(zone) { SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = zone } }
    val cardColor = theme.shortcut

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (forecast != null) {
            val today = forecast.today(System.currentTimeMillis() / 1000)
            Row(
                Modifier
                    .weight(1.7f)
                    .background(cardColor, shape)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.width(150.dp)) {
                    Text("Weather", color = theme.subtle, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(WeatherClient.icon(forecast.nowCode), fontSize = 26.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("${forecast.nowTemp}°", color = theme.text, fontSize = 44.sp, fontFamily = mono)
                    }
                    Text(forecast.nowText, color = theme.text, fontSize = 15.sp)
                    if (today != null) {
                        Text("H:${today.hi}°  L:${today.lo}°", color = theme.subtle, fontSize = 13.sp)
                    }
                    if (place.isNotBlank()) {
                        Text(place, color = theme.subtle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        forecast.hours.take(if (coins.isEmpty()) 8 else 6).forEach { hour ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(hourFormat.format(Date(hour.time * 1000)).uppercase(), color = theme.subtle, fontSize = 12.sp)
                                Text(WeatherClient.icon(hour.code), fontSize = 20.sp)
                                Text("${hour.temp}°", color = theme.text, fontSize = 14.sp, fontFamily = mono)
                            }
                        }
                    }
                    if (today != null && today.sunrise > 0 && today.sunset > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            Text("🌅 Sunrise ${sunFormat.format(Date(today.sunrise * 1000))}", color = theme.subtle, fontSize = 12.sp)
                            Text("🌇 Sunset ${sunFormat.format(Date(today.sunset * 1000))}", color = theme.subtle, fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val dayFormat = remember(zone) { SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = zone } }
                        forecast.days.drop(1).take(5).forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayFormat.format(Date(day.time * 1000)), color = theme.subtle, fontSize = 12.sp)
                                Text(WeatherClient.icon(day.code), fontSize = 16.sp)
                                Text("${day.hi}°/${day.lo}°", color = theme.text, fontSize = 12.sp, fontFamily = mono)
                            }
                        }
                    }
                }
            }
        }
        if (coins.isNotEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .background(cardColor, shape)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Crypto", color = theme.subtle, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("24h change", color = theme.subtle, fontSize = 12.sp)
                }
                coins.take(4).forEach { coin ->
                    val up = coin.change24h >= 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(coin.symbol, color = theme.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(coin.priceText, color = theme.text, fontSize = 15.sp, fontFamily = mono)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            coin.changeText,
                            color = if (up) Color(0xFF1E8E3E) else Color(0xFFC62828),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(
                                    if (up) Color(0x334CD964) else Color(0x33FF6B6B),
                                    RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(forecast: WeatherClient.Forecast?, coins: List<CryptoClient.Coin>) {
    val theme = LocalLauncherTheme.current
    val zone = remember(forecast?.utcOffset) { SimpleTimeZone((forecast?.utcOffset ?: 0) * 1000, "place") }
    val hourFormat = remember(zone) { SimpleDateFormat("h a", Locale.getDefault()).apply { timeZone = zone } }
    val dayFormat = remember(zone) { SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = zone } }
    val sunFormat = remember(zone) { SimpleDateFormat("h:mm a", Locale.getDefault()).apply { timeZone = zone } }
    val mono = if (theme.monospace) FontFamily.Monospace else null

    Column(
        Modifier
            .fillMaxWidth()
            .background(theme.shortcut, RoundedCornerShape(theme.corner + 8.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (forecast != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                forecast.hours.forEach { hour ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(hourFormat.format(Date(hour.time * 1000)).lowercase(), color = theme.subtle, fontSize = 13.sp)
                        Text(WeatherClient.icon(hour.code), fontSize = 20.sp)
                        Text("${hour.temp}°", color = theme.text, fontSize = 15.sp, fontFamily = mono)
                    }
                }
                Spacer(Modifier.weight(1f))
                val today = forecast.today(System.currentTimeMillis() / 1000)
                if (today != null && today.sunrise > 0 && today.sunset > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("🌅 ${sunFormat.format(Date(today.sunrise * 1000))}", color = theme.text, fontSize = 14.sp)
                        Text("🌇 ${sunFormat.format(Date(today.sunset * 1000))}", color = theme.text, fontSize = 14.sp)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                forecast.days.take(5).forEachIndexed { index, day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (index == 0) "Today" else dayFormat.format(Date(day.time * 1000)),
                            color = theme.subtle,
                            fontSize = 13.sp,
                        )
                        Text(WeatherClient.icon(day.code), fontSize = 20.sp)
                        Text("${day.hi}° / ${day.lo}°", color = theme.text, fontSize = 14.sp, fontFamily = mono)
                    }
                }
            }
        }
        if (coins.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                coins.forEach { coin ->
                    Column {
                        Text(coin.symbol, color = theme.subtle, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(coin.priceText, color = theme.text, fontSize = 18.sp, fontFamily = mono)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                coin.changeText,
                                color = if (coin.change24h >= 0) Color(0xFF4CD964) else Color(0xFFFF6B6B),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClockBlock(weather: WeatherState?) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val now = rememberNow()
    val theme = LocalLauncherTheme.current

    Column {
        Text(
            text = timeFormat.format(now),
            style = MaterialTheme.typography.displaySmall,
            color = LocalLauncherTheme.current.text,
            fontFamily = if (theme.monospace) FontFamily.Monospace else null,
        )
        val line = buildString {
            append(dateFormat.format(now))
            if (weather != null) {
                append("   ·   ")
                append(weather.text)
                if (weather.place.isNotBlank()) {
                    append("   ·   ")
                    append(weather.place)
                }
            }
        }
        Text(text = line, color = theme.subtle, fontSize = 16.sp)
    }
}

@Composable
private fun Screensaver(wallpaperMinutes: Int, weather: WeatherState?, backdrops: List<TrendingTitle>) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
    val now = rememberNow()
    val theme = LocalLauncherTheme.current

    // Slowly moving gradient: shown when there are no wallpaper photos, and under them while they load.
    val drift = rememberInfiniteTransition(label = "drift")
    val shift by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val top = lerp(theme.accent, Color.Black, 0.72f)
    val bottom = lerp(theme.accent, Color.Black, 0.93f)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .background(
                Brush.radialGradient(
                    colors = listOf(top, bottom, Color.Black),
                    center = Offset(1920f * (0.25f + 0.5f * shift), 1080f * (0.3f + 0.2f * shift)),
                    radius = 1800f,
                ),
            ),
    ) {
        if (backdrops.isEmpty()) WallpaperBackground(wallpaperMinutes) else BackdropShow(backdrops)
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                // Moves a little every minute so a still clock never burns into the panel.
                .padding(
                    start = (56 + (now.time / 60_000 % 9).toInt() * 12).dp,
                    end = 56.dp,
                    top = 40.dp,
                    bottom = (40 + (now.time / 60_000 % 5).toInt() * 10).dp,
                ),
        ) {
            Column {
                Text(
                    text = timeFormat.format(now),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontFamily = if (theme.monospace) FontFamily.Monospace else null,
                )
                Text(text = dateFormat.format(now), color = Color(0xFFD5DDEB), fontSize = 20.sp)
                if (weather != null) {
                    Text(
                        text = weather.text + if (weather.place.isNotBlank()) "   ·   " + weather.place else "",
                        color = Color(0xFFB8C2D4),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

/** Idle backdrops: a new movie or show every 20 seconds, crossfading while slowly zooming in. */
@Composable
private fun BackdropShow(titles: List<TrendingTitle>) {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(titles.size) {
        while (titles.isNotEmpty()) {
            delay(20_000)
            index = (index + 1) % titles.size
        }
    }
    Crossfade(
        targetState = titles[index % titles.size],
        animationSpec = tween(2_000),
        label = "idlebackdrop",
    ) { title ->
        val zoom = rememberInfiniteTransition(label = "zoom")
        val scale by zoom.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(22_000, easing = LinearEasing), RepeatMode.Restart),
            label = "scale",
        )
        Box(Modifier.fillMaxSize()) {
            val backdrop = title.backdrop
            if (backdrop != null) {
                Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                )
            }
            Text(
                text = title.name + if (title.year.isNotBlank()) " (${title.year})" else "",
                color = Color(0xCCFFFFFF),
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 56.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun AutoStartBanner(countdown: AutoStartState, modifier: Modifier = Modifier) {
    val theme = LocalLauncherTheme.current
    Box(
        modifier
            .padding(bottom = 32.dp)
            .background(theme.panel.copy(alpha = 0.92f), RoundedCornerShape(theme.corner + 4.dp))
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Opening ${countdown.label} in ${countdown.secondsLeft}...  Press any button to cancel.",
            color = LocalLauncherTheme.current.text,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun SmartRow(
    tiles: List<SmartTile>,
    tileWidth: Dp,
    onRun: (SmartTile) -> Unit,
) {
    Column {
        RowTitle("Smart home")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(tiles, key = { it.id }) { tile ->
                ShortcutTile(label = tile.label, tileWidth = tileWidth, onClick = { onRun(tile) })
            }
        }
    }
}

/** Index of the hero title on show; advances every 15 seconds. Shared so the backdrop and the info stay in step. */
@Composable
private fun rememberHeroIndex(size: Int): Int {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(size) {
        while (size > 0) {
            delay(15_000)
            index = (index + 1) % size
        }
    }
    return if (size == 0) 0 else index % size
}

/** Full-screen mode: the backdrop fills the screen behind everything, darkened so rows stay readable. */
@Composable
private fun FullHeroBackdrop(shown: TrendingTitle) {
    Crossfade(targetState = shown, label = "herobg") { title ->
        Box(Modifier.fillMaxSize()) {
            val backdrop = title.backdrop
            if (backdrop != null) {
                Image(
                    bitmap = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(0f to Color(0xCC000000), 0.65f to Color.Transparent)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0f to Color(0x55000000), 0.4f to Color(0x99000000), 1f to Color(0xF0000000))),
            )
        }
    }
}

/** Full-screen mode: logo or title, rating and overview sit over the backdrop, with a "More info" button. */
@Composable
private fun HeroInfo(shown: TrendingTitle, onClick: (TrendingTitle) -> Unit) {
    val theme = LocalLauncherTheme.current
    Column(
        Modifier
            .fillMaxWidth()
            .height(300.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Crossfade(targetState = shown, label = "heroinfo") { title ->
            Column(Modifier.fillMaxWidth(0.5f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val logo = title.logo
                if (logo != null) {
                    Image(
                        bitmap = logo,
                        contentDescription = title.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.height(84.dp).widthIn(max = 380.dp),
                    )
                } else {
                    Text(title.name, color = Color.White, fontSize = 38.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    text = listOf(title.year, "★ " + String.format("%.1f", title.rating), if (title.type == "tv") "Series" else "Movie")
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    color = Color(0xFFE0E6F0),
                    fontSize = 16.sp,
                )
                if (title.overview.isNotBlank()) {
                    Text(title.overview, color = Color(0xFFE0E6F0), fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            onClick = { onClick(shown) },
            shape = CardDefaults.shape(RoundedCornerShape(theme.corner)),
            border = tileBorder(theme.corner),
            modifier = Modifier.width(170.dp),
        ) {
            Box(Modifier.background(theme.tile).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text("More info", color = theme.text, fontSize = 16.sp)
            }
        }
    }
}

/** What another app is playing right now. Select it to jump back to that app. */
@Composable
private fun NowPlayingBar(info: NowPlaying.Info, appLabel: String, onResume: () -> Unit) {
    val theme = LocalLauncherTheme.current
    Card(
        onClick = onResume,
        shape = CardDefaults.shape(RoundedCornerShape(theme.corner)),
        border = tileBorder(theme.corner),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.background(theme.panel).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (info.playing) "▶" else "⏸", color = theme.text, fontSize = 20.sp)
            Column(Modifier.weight(1f)) {
                Text(info.title, color = theme.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(info.subtitle, appLabel).filter { it.isNotBlank() }.joinToString("  ·  ")
                Text(sub, color = theme.subtle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("Resume", color = theme.subtle, fontSize = 14.sp)
        }
    }
}

/** Banner mode: a card under the first row with the backdrop, logo, rating and a short overview. Select it for details. */
@Composable
private fun HeroBanner(shown: TrendingTitle, onClick: (TrendingTitle) -> Unit) {
    val theme = LocalLauncherTheme.current
    val corner = theme.corner + 8.dp
    Card(
        onClick = { onClick(shown) },
        shape = CardDefaults.shape(RoundedCornerShape(corner)),
        border = tileBorder(corner),
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        Crossfade(targetState = shown, label = "hero") { title ->
            Box(Modifier.fillMaxSize().background(theme.tile)) {
                val backdrop = title.backdrop
                if (backdrop != null) {
                    Image(
                        bitmap = backdrop,
                        contentDescription = title.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color(0xDD000000), Color.Transparent))),
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(0.55f)
                        .padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val logo = title.logo
                    if (logo != null) {
                        Image(
                            bitmap = logo,
                            contentDescription = title.name,
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                            modifier = Modifier.height(64.dp).widthIn(max = 300.dp),
                        )
                    } else {
                        Text(title.name, color = Color.White, fontSize = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = listOf(title.year, "★ " + String.format("%.1f", title.rating), if (title.type == "tv") "Series" else "Movie")
                            .filter { it.isNotBlank() }.joinToString("  ·  "),
                        color = Color(0xFFE0E6F0),
                        fontSize = 14.sp,
                    )
                    if (title.overview.isNotBlank()) {
                        Text(title.overview, color = Color(0xFFE0E6F0), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingRow(heading: String, titles: List<TrendingTitle>, tileWidth: Dp, onClick: (TrendingTitle) -> Unit) {
    val theme = LocalLauncherTheme.current
    val width = tileWidth * 0.75f
    Column {
        RowTitle(heading)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(titles, key = { it.type + it.id }) { title ->
                Column(Modifier.width(width)) {
                    Card(
                        onClick = { onClick(title) },
                        shape = CardDefaults.shape(RoundedCornerShape(theme.corner)),
                        border = tileBorder(theme.corner),
                        modifier = Modifier
                            .width(width)
                            .height(width * 3 / 2),
                    ) {
                        Box(Modifier.fillMaxSize().background(theme.tile), contentAlignment = Alignment.Center) {
                            if (title.poster != null) {
                                Image(
                                    bitmap = title.poster,
                                    contentDescription = title.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(title.name, color = theme.text, fontSize = 13.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Text(
                        text = title.name,
                        color = theme.text,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickRow(
    sleepLabel: String,
    tileWidth: Dp,
    compact: Boolean,
    onQuickAction: (QuickAction) -> Unit,
) {
    // In the Cards layout the shortcuts become small tiles right under your first row.
    val width = if (compact) maxOf(tileWidth * 0.62f, 112.dp) else tileWidth
    Column {
        RowTitle("Quick settings")
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(QuickAction.entries, key = { it.name }) { action ->
                ShortcutTile(
                    label = if (action == QuickAction.SLEEP) "Sleep timer: $sleepLabel" else action.label,
                    tileWidth = width,
                    onClick = { onQuickAction(action) },
                    icon = action.icon(),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    title: String,
    apps: List<AppEntry>,
    tileWidth: Dp,
    onClick: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (AppEntry) -> Unit,
) {
    val theme = LocalLauncherTheme.current
    Column(modifier) {
        RowTitle(title)
        if (apps.isEmpty()) {
            Text(
                text = "Empty. Hold OK on any app and choose \"Add to $title\".",
                color = theme.subtle.copy(alpha = 0.8f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppTile(app, tileWidth, { onClick(app) }, { onLongClick(app) })
                }
            }
        }
    }
}

@Composable
internal fun AppTile(
    app: AppEntry,
    tileWidth: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLauncherTheme.current
    Column(Modifier.width(tileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CardDefaults.shape(RoundedCornerShape(theme.corner)),
            border = tileBorder(theme.corner),
            modifier = modifier
                .width(tileWidth)
                .height(tileWidth * 9 / 16),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(theme.tile),
                contentAlignment = Alignment.Center,
            ) {
                if (app.banner != null) {
                    Image(
                        bitmap = app.banner,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(tileWidth / 3),
                    )
                }
            }
        }
        Text(
            text = app.label,
            color = LocalLauncherTheme.current.text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        )
    }
}

/** Focus outline that follows the tile's rounded corners instead of the default square one. */
@Composable
private fun tileBorder(corner: Dp) = CardDefaults.border(
    focusedBorder = Border(
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.border),
        shape = RoundedCornerShape(corner),
    ),
)

/** The picture shown above each Quick settings label. */
internal fun QuickAction.icon(): ImageVector = when (this) {
    QuickAction.ASSISTANT -> Icons.Filled.Mic
    QuickAction.WIFI -> Icons.Filled.Wifi
    QuickAction.BLUETOOTH -> Icons.Filled.Bluetooth
    QuickAction.DISPLAY -> Icons.Filled.Tv
    QuickAction.SOUND -> Icons.Filled.VolumeUp
    QuickAction.DEVELOPER -> Icons.Filled.Code
    QuickAction.SLEEP -> Icons.Filled.Timer
    QuickAction.DEVICE_INFO -> Icons.Filled.Info
    QuickAction.INSTALL_APK -> Icons.Filled.Download
    QuickAction.PHONE_EDITOR -> Icons.Filled.PhoneAndroid
    QuickAction.FREE_MEMORY -> Icons.Filled.Memory
}

/** A tile for shortcuts that aren't apps: a label, with an optional icon above it. */
@Composable
internal fun ShortcutTile(
    label: String,
    tileWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val theme = LocalLauncherTheme.current
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(RoundedCornerShape(theme.corner)),
        border = tileBorder(theme.corner),
        modifier = modifier
            .width(tileWidth)
            .height(if (icon != null) tileWidth * 0.7f else tileWidth * 9 / 16),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(theme.shortcut)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = theme.text,
                        modifier = Modifier.size(if (tileWidth < 150.dp) 22.dp else 30.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = label,
                    color = LocalLauncherTheme.current.text,
                    fontSize = if (icon != null) 13.sp else 14.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (icon != null) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


/** Row heading, styled by the current theme. */
@Composable
private fun RowTitle(title: String) {
    val theme = LocalLauncherTheme.current
    Text(
        text = if (theme.upperTitles) title.uppercase() else title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontFamily = if (theme.monospace) FontFamily.Monospace else null,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
