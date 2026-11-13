
package com.vishal.sfxeditor.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AudioPreviewController(context: Context) {

    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    var currentAssetId: String? = null
        private set

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    init {
        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                onPlaybackStateChanged?.invoke(isPlaying)
            }
        })
    }

    fun play(asset: AudioAssetEntity): Boolean {
        val file = File(appContext.filesDir, asset.relativePath)

        if (!file.exists() || !file.isFile) {
            return false
        }

        if (currentAssetId == asset.id) {
            player.play()
            return true
        }

        currentAssetId = asset.id

        player.setMediaItem(
            MediaItem.fromUri(Uri.fromFile(file))
        )

        player.prepare()
        player.play()

        return true
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun stop() {
        player.stop()
        currentAssetId = null
    }

    fun release() {
        player.release()
        currentAssetId = null
    }
}
