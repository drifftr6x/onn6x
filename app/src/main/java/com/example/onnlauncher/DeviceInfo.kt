package com.example.onnlauncher

import java.util.Locale
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Facts about the box, with a focus on "can I still reach it with ADB?". Blocking: call off the main thread. */
object DeviceInfo {

    /** (interface name, IPv4 address) pairs, e.g. wlan0 / 192.168.1.50. */
    fun ipAddresses(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filter { it is Inet4Address && !it.isLoopbackAddress }
                    .map { nif.name to (it.hostAddress ?: "") }
            }
            .filter { it.second.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    fun collect(context: Context): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        val ips = ipAddresses()
        lines += "IP address" to
            ips.joinToString("   ") { "${it.second} (${it.first})" }.ifEmpty { "not connected" }

        val resolver = context.contentResolver
        val usb = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1
        lines += "USB debugging" to if (usb) "On" else "Off"
        val wireless = Settings.Global.getInt(resolver, "adb_wifi_enabled", -1)
        lines += "Wireless debugging" to when (wireless) {
            1 -> "On"
            0 -> "Off"
            else -> "Unknown (this build doesn't let apps read it)"
        }
        val firstIp = ips.firstOrNull()?.second
        lines += "ADB on port 5555" to
            if (firstIp != null && portOpen(firstIp, 5555)) "Listening" else "Not listening"

        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            lines += "Storage free" to "${formatBytes(stat.availableBytes)} of ${formatBytes(stat.totalBytes)}"
        } catch (e: Exception) {
            lines += "Storage free" to "unknown"
        }

        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        lines += "Memory free" to "${formatBytes(memory.availMem)} of ${formatBytes(memory.totalMem)}"

        lines += "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        lines += "Device" to "${Build.MANUFACTURER} ${Build.MODEL}"
        lines += "Up for" to formatUptime(SystemClock.elapsedRealtime())
        return lines
    }

    private fun portOpen(host: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(host, port), 400) }
        true
    } catch (e: Exception) {
        false
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1) String.format(Locale.US, "%.1f GB", gb) else String.format(Locale.US, "%d MB", bytes / 1_048_576)
    }

    private fun formatUptime(millis: Long): String {
        val minutes = millis / 60_000
        val days = minutes / (60 * 24)
        val hours = (minutes / 60) % 24
        val mins = minutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }
}
