package com.example.onnlauncher

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow

/** What is playing right now, as reported by whichever app holds the active media session. */
object NowPlaying {
    data class Info(val pkg: String, val title: String, val subtitle: String, val playing: Boolean)

    val state = MutableStateFlow<Info?>(null)
}

/**
 * Reads other apps' media sessions (Netflix, YouTube, Plex...). Android only lets an app do that
 * once it is a notification listener, so this needs a one-time grant:
 * adb shell cmd notification allow_listener com.example.onnlauncher/com.example.onnlauncher.NowPlayingService
 * It never reads notification contents.
 */
class NowPlayingService : NotificationListenerService() {

    private var manager: MediaSessionManager? = null
    private val attached = ArrayList<Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            val m = getSystemService(MediaSessionManager::class.java)
            manager = m
            val me = ComponentName(this, NowPlayingService::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, me)
            attach(m.getActiveSessions(me))
        } catch (e: Exception) {
            NowPlaying.state.value = null
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        detach()
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // already gone
        }
        NowPlaying.state.value = null
    }

    private fun detach() {
        attached.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (e: Exception) {
                // session already ended
            }
        }
        attached.clear()
    }

    private fun attach(controllers: List<MediaController>) {
        detach()
        for (controller in controllers) {
            if (controller.packageName == packageName) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                override fun onSessionDestroyed() = refresh()
            }
            try {
                controller.registerCallback(callback)
                attached.add(controller to callback)
            } catch (e: Exception) {
                // skip sessions we can't watch
            }
        }
        refresh()
    }

    /** Picks the session that is playing, or failing that the paused one, and publishes it. */
    private fun refresh() {
        var best: NowPlaying.Info? = null
        for ((controller, _) in attached) {
            val state = controller.playbackState?.state
            val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
            val paused = state == PlaybackState.STATE_PAUSED
            if (!playing && !paused) continue
            val meta = controller.metadata ?: continue
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: continue
            if (title.isBlank()) continue
            val subtitle = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
            val info = NowPlaying.Info(controller.packageName, title, subtitle, playing)
            if (best == null || (playing && !best.playing)) best = info
        }
        NowPlaying.state.value = best
    }
}
