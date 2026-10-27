package com.example.onnlauncher

/**
 * EDIT THIS FILE to change the rows a fresh install starts with.
 *
 * Each row is a name plus a list of package names. Packages that aren't installed
 * are simply skipped, so it's safe to list apps you might install later.
 * After first run, rows are changed from the long-press menu on any app tile.
 *
 * To see exact package names on your box:  adb shell pm list packages
 * (Some of the names below are best guesses; wrong ones are harmlessly ignored.)
 */
object DefaultRows {

    /** Row name that also auto-collects every installed app flagged as a game. */
    const val GAMES_ROW = "Games & Emulators"

    val rows: LinkedHashMap<String, List<String>> = linkedMapOf(
        "Favorites" to listOf(
            "org.xbmc.kodi",
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
        ),
        "Streaming" to listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.disney.disneyplus",
            "com.hulu.livingroomplus",
            "com.amazon.amazonvideo.livingroom",
            "com.cbs.ott",
            "com.tubitv",
            "com.wbd.stream",
            "com.peacocktv.peacockandroid",
            "com.apple.atve.androidtv.appletv",
            "com.crunchyroll.crunchyroid",
            "com.spotify.tv.android",
        ),
        "Media" to listOf(
            "org.xbmc.kodi",
            "org.jellyfin.androidtv",
            "com.plexapp.android",
            "org.videolan.vlc",
            "com.liskovsoft.smarttubetv",
            "com.liskovsoft.smarttubetv.beta",
        ),
        GAMES_ROW to listOf(
            "com.retroarch",
            "com.retroarch.aarch64",
            "org.ppsspp.ppsspp",
            "org.ppsspp.ppssppgold",
            "org.dolphinemu.dolphinemu",
            "com.limelight",
            "com.valvesoftware.steamlink",
        ),
    )
}
