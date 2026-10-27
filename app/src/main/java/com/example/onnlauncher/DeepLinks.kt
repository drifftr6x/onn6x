package com.example.onnlauncher

import android.content.Intent
import android.net.Uri

/**
 * Search links for a few streaming apps, so "Search for it in Netflix" can land on the right results
 * instead of just opening the app. Apps change these without notice, so every use falls back to a
 * plain open if the app doesn't take the link.
 */
object DeepLinks {

    fun intentFor(packageName: String, title: String): Intent? {
        val q = Uri.encode(title)
        val uri = when (packageName) {
            "com.netflix.ninja" -> "https://www.netflix.com/search?q=$q"
            "com.google.android.youtube.tv" -> "https://www.youtube.com/results?search_query=$q"
            "com.amazon.amazonvideo.livingroom" -> "https://app.primevideo.com/search?phrase=$q"
            else -> return null
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun supports(packageName: String): Boolean = intentFor(packageName, "x") != null
}
