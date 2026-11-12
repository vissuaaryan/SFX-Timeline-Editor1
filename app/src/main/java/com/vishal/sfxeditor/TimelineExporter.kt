package com.vishal.sfxeditor

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class TimelineExporter(private val context:Context) {
 interface Callback { fun onProgress(percent:Int); fun onComplete(file:File); fun onError(message:String) }
 private var transformer:Transformer?=null
 fun export(project:SfxProject,videoUri:Uri,callback:Callback) {
  try {
   val video=EditedMediaItem.Builder(MediaItem.fromUri(videoUri)).setRemoveAudio(!project.preserveOriginalAudio).build()
   val sequences=mutableListOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(video)))
   val assetMap=project.assets.associateBy{it.id}
   project.events.forEachIndexed { index,event ->
    val asset=assetMap[event.assetId]?:error("Missing asset ${event.assetId}")
    val audioUri=asset.uri?:error("Upload required asset: ${asset.filename}")
    val silence=File(context.cacheDir,"silence_${index}_${event.startMs}.wav"); writeSilence(silence,event.startMs)
    val silenceItem=EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(silence))).build()
    val clip=MediaItem.Builder().setUri(audioUri).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(event.durationMs).build()).build()
    val gain=DefaultGainProvider.Builder(event.volume)
    if(event.fadeInMs>0) gain.addFadeAt(0,event.fadeInMs*1000,DefaultGainProvider.FADE_IN_LINEAR)
    if(event.fadeOutMs>0) gain.addFadeAt((event.durationMs-event.fadeOutMs).coerceAtLeast(0)*1000,event.fadeOutMs*1000,DefaultGainProvider.FADE_OUT_LINEAR)
    val processed=EditedMediaItem.Builder(clip).setEffects(Effects(listOf(GainProcessor(gain.build())), emptyList())).build()
    val audioSequenceItems = if (event.startMs > 0) listOf(silenceItem, processed) else listOf(processed)
    sequences+=EditedMediaItemSequence.withAudioFrom(audioSequenceItems)
   }
   val composition=Composition.Builder(sequences).build()
   val out=File(context.cacheDir,"SFX_Final_${System.currentTimeMillis()}.mp4")
   transformer=Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
    .addListener(object:Transformer.Listener {
     override fun onCompleted(composition:Composition,exportResult:ExportResult){callback.onProgress(100);callback.onComplete(out)}
     override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException){callback.onError(exportException.message?:"Media3 render failed")}
    }).build()
   transformer!!.start(composition,out.absolutePath); callback.onProgress(1)
  } catch(e:Exception){callback.onError(e.message?:"Could not start render")}
 }
 fun cancel(){transformer?.cancel()}
 private fun writeSilence(file:File,ms:Long){
  val rate=48000; val channels=2; val bytes=(ms.coerceAtLeast(0)*rate*channels*2/1000).toInt()
  FileOutputStream(file).use { out ->
   val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
   h.put("RIFF".toByteArray());h.putInt(36+bytes);h.put("WAVE".toByteArray());h.put("fmt ".toByteArray());h.putInt(16);h.putShort(1);h.putShort(channels.toShort());h.putInt(rate);h.putInt(rate*channels*2);h.putShort((channels*2).toShort());h.putShort(16);h.put("data".toByteArray());h.putInt(bytes);out.write(h.array())
   val zero=ByteArray(65536);var left=bytes;while(left>0){val n=minOf(left,zero.size);out.write(zero,0,n);left-=n}
  }
 }
}
