package com.vishal.sfxeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Exports the source video together with all timeline audio events.
 * Each event is a separate audio sequence, so events on different layers can
 * overlap. Event gain automation supplies the requested fade-in/fade-out and
 * a channel-mixing processor applies left/right pan.
 */
@OptIn(UnstableApi::class)
class TimelineExporter(private val context: Context) {
    interface Callback {
        fun onProgress(percent: Int)
        fun onComplete(file: File)
        fun onError(message: String)
    }

    private var transformer: Transformer? = null
    private var activeOutput: File? = null
    private val temporaryFiles = mutableListOf<File>()

    @Synchronized
    fun export(project: SfxProject, videoUri: Uri, callback: Callback) {
        if (transformer != null) {
            callback.onError("An export is already running")
            return
        }

        try {
            validateProject(project)
            temporaryFiles.clear()

            val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(videoUri))
                .setRemoveAudio(!project.preserveOriginalAudio)
                .build()
            val sequences = mutableListOf(
                EditedMediaItemSequence.withAudioAndVideoFrom(listOf(videoItem))
            )
            val assetMap = project.assets.associateBy { it.id }

            project.events.sortedWith(compareBy<AudioEvent> { it.layer }.thenBy { it.startMs })
                .forEachIndexed { index, event ->
                    val asset = assetMap[event.assetId]
                        ?: error("Event ${event.id}: asset ${event.assetId} is missing")
                    val audioUri = asset.uri
                        ?: error("Audio file missing for ${asset.filename}. Relink it before export.")

                    val sequenceItems = mutableListOf<EditedMediaItem>()
                    if (event.startMs > 0L) {
                        val silence = File(
                            context.cacheDir,
                            "sfx_silence_${event.id.hashCode()}_${System.nanoTime()}.wav"
                        )
                        writeSilence(silence, event.startMs)
                        temporaryFiles += silence
                        sequenceItems += EditedMediaItem.Builder(
                            MediaItem.fromUri(Uri.fromFile(silence))
                        ).build()
                    }

                    val clip = MediaItem.Builder()
                        .setUri(audioUri)
                        .setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(0L)
                                .setEndPositionMs(event.durationMs)
                                .build()
                        )
                        .build()

                    val gainBuilder = DefaultGainProvider.Builder(event.volume.coerceIn(0f, 1f))
                    if (event.fadeInMs > 0L) {
