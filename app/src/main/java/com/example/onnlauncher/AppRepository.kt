package com.example.onnlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Finds every launchable app, TV-style (leanback) and phone-style, so Kodi, emulators and sideloads all show up. */
class AppRepository(private val context: Context) {

    private val pm = context.packageManager

    @Suppress("DEPRECATION")
    fun loadApps(): List<AppEntry> {
        // Leanback first, so a TV entry point wins when an app has both.
        val byPackage = LinkedHashMap<String, ResolveInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue
                byPackage.putIfAbsent(pkg, info)
            }
        }
        return byPackage.values
            .mapNotNull { runCatching { toEntry(it) }.getOrNull() }
            .sortedBy { it.label.lowercase() }
    }

    private fun toEntry(info: ResolveInfo): AppEntry {
        val activity = info.activityInfo
        val launch = Intent(Intent.ACTION_MAIN)
            .setClassName(activity.packageName, activity.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        val banner = activity.loadBanner(pm)?.toBitmap(320, 180)?.asImageBitmap()
        val icon = activity.loadIcon(pm).toBitmap(128, 128).asImageBitmap()
        @Suppress("DEPRECATION")
        val isGame = activity.applicationInfo.category == ApplicationInfo.CATEGORY_GAME

        return AppEntry(
            packageName = activity.packageName,
            label = info.loadLabel(pm).toString(),
            intent = launch,
            banner = banner,
            icon = icon,
            isGame = isGame,
        )
    }
}
