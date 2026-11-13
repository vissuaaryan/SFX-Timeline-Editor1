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
                        gainBuilder.addFadeAt(
                            0L,
                            event.fadeInMs.coerceAtMost(event.durationMs) * 1_000L,
                            DefaultGainProvider.FADE_IN_LINEAR
                        )
                    }
                    if (event.fadeOutMs > 0L) {
                        val fadeStartMs = (event.durationMs - event.fadeOutMs).coerceAtLeast(0L)
                        gainBuilder.addFadeAt(
                            fadeStartMs * 1_000L,
                            event.fadeOutMs.coerceAtMost(event.durationMs) * 1_000L,
                            DefaultGainProvider.FADE_OUT_LINEAR
                        )
                    }

                    val audioProcessors = mutableListOf(
                        GainProcessor(gainBuilder.build())
                    )
                    if (event.pan != 0f) {
                        audioProcessors += createPanProcessor(event.pan)
                    }

                    val processedClip = EditedMediaItem.Builder(clip)
                        .setEffects(Effects(audioProcessors, emptyList()))
                        .build()
                    sequenceItems += processedClip
                    sequences += EditedMediaItemSequence.withAudioFrom(sequenceItems)
                }

            val composition = Composition.Builder(sequences).build()
            val output = File(
                context.cacheDir,
                "SFX_Final_${System.currentTimeMillis()}.mp4"
            )
            activeOutput = output

            transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult
                    ) {
                        val completedFile = activeOutput
                        transformer = null
                        cleanupTemporaryFiles()
                        if (completedFile != null && completedFile.exists() && completedFile.length() > 0L) {
                            callback.onProgress(100)
                            callback.onComplete(completedFile)
                        } else {
                            callback.onError("Export finished but output file is empty")
                        }
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        transformer = null
                        activeOutput?.delete()
                        activeOutput = null
                        cleanupTemporaryFiles()
                        callback.onError(exportException.message ?: "Media3 render failed")
                    }
                })
                .build()

            transformer?.start(composition, output.absolutePath)
            callback.onProgress(1)
        } catch (error: Exception) {
            transformer = null
            activeOutput?.delete()
            activeOutput = null
            cleanupTemporaryFiles()
            callback.onError(error.message ?: "Could not start render")
        }
    }

    @Synchronized
    fun cancel() {
        transformer?.cancel()
        transformer = null
        activeOutput?.delete()
        activeOutput = null
        cleanupTemporaryFiles()
    }

    private fun validateProject(project: SfxProject) {
        require(project.durationMs > 0L) { "Project duration must be greater than zero" }
        val assets = project.assets.associateBy { it.id }
        require(assets.size == project.assets.size) { "Duplicate asset IDs found" }
        val eventIds = mutableSetOf<String>()
        project.events.forEach { event ->
            require(eventIds.add(event.id)) { "Duplicate timeline event ID: ${event.id}" }
            require(assets.containsKey(event.assetId)) {
                "Event ${event.id} references unknown asset ${event.assetId}"
            }
            require(event.startMs >= 0L && event.durationMs > 0L) {
                "Event ${event.id} has invalid start/duration"
            }
            require(event.endMs == event.startMs + event.durationMs) {
                "Event ${event.id} has inconsistent end time"
            }
            require(event.endMs <= project.durationMs) {
                "Event ${event.id} extends beyond the video duration"
            }
            require(event.volume in 0f..1f && event.pan in -1f..1f) {
                "Event ${event.id} has invalid volume or pan"
            }
            require(event.fadeInMs >= 0L && event.fadeOutMs >= 0L &&
                event.fadeInMs + event.fadeOutMs <= event.durationMs) {
                "Event ${event.id} has fades longer than its duration"
            }
        }
    }

    /**
     * Pan is constant-power for mono sources. For stereo sources it behaves
     * like a balance control, attenuating the opposite side without swapping
     * channels. The matrices are registered for mono and stereo PCM inputs.
     */
    private fun createPanProcessor(rawPan: Float): ChannelMixingAudioProcessor {
        val pan = rawPan.coerceIn(-1f, 1f)
        val angle = (pan + 1f) * (PI.toFloat() / 4f)
        val monoLeft = cos(angle)
        val monoRight = sin(angle)
        val stereoLeft = if (pan > 0f) cos(pan * PI.toFloat() / 2f) else 1f
        val stereoRight = if (pan < 0f) cos(-pan * PI.toFloat() / 2f) else 1f

        return ChannelMixingAudioProcessor().apply {
            putChannelMixingMatrix(
                ChannelMixingMatrix(1, 2, floatArrayOf(monoLeft, monoRight))
            )
            putChannelMixingMatrix(
                ChannelMixingMatrix(
                    2,
                    2,
                    floatArrayOf(stereoLeft, 0f, 0f, stereoRight)
                )
            )
        }
    }

    private fun writeSilence(file: File, durationMs: Long) {
        val sampleRate = 48_000
        val channels = 2
        val bytesLong = durationMs.coerceAtLeast(0L) * sampleRate * channels * 2L / 1_000L
        require(bytesLong <= Int.MAX_VALUE - 36L) { "Timeline offset is too long to render" }
        val dataBytes = bytesLong.toInt()

        FileOutputStream(file).use { output ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            output.write(header.array())

            val zeroBuffer = ByteArray(64 * 1024)
            var remaining = dataBytes
            while (remaining > 0) {
                val count = minOf(remaining, zeroBuffer.size)
                output.write(zeroBuffer, 0, count)
                remaining -= count
            }
        }
    }

    private fun cleanupTemporaryFiles() {
        temporaryFiles.forEach { it.delete() }
        temporaryFiles.clear()
    }
}
