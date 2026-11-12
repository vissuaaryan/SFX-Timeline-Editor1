package com.vishal.sfxeditor

import android.net.Uri
import org.json.JSONObject

data class AssetSlot(val id:String,val filename:String,val type:String,val description:String,val required:Boolean,val searchPhrase:String="",val sourceName:String="",val sourceUrl:String="",val uri:Uri?=null)
data class AudioEvent(val id:String,val assetId:String,val startMs:Long,val endMs:Long,val durationMs:Long,val layer:Int,val volume:Float,val fadeInMs:Long,val fadeOutMs:Long,val pan:Float,val visualAction:String,val reason:String)
data class SfxProject(val name:String,val durationMs:Long,val fps:Double,val preserveOriginalAudio:Boolean,val assets:List<AssetSlot>,val events:List<AudioEvent>)
object ProjectParser {
 fun parse(raw:String):SfxProject {
  val root=JSONObject(raw); require(root.optString("schema_version").isNotBlank()){"schema_version missing"}
  val p=root.getJSONObject("project"); val settings=root.optJSONObject("audio_settings")?:JSONObject()
  val a=root.getJSONArray("assets"); val t=root.getJSONArray("timeline")
  val assets=mutableListOf<AssetSlot>(); val ids=mutableSetOf<String>()
  for(i in 0 until a.length()) { val x=a.getJSONObject(i); val id=x.optString("id"); val file=x.optString("filename"); require(id.isNotBlank()&&file.isNotBlank()){"Asset #${i+1}: id/filename missing"}; require(ids.add(id)){"Duplicate asset ID $id"}; assets+=AssetSlot(id,file,x.optString("type","sfx"),x.optString("description"),x.optBoolean("required",true),x.optString("search_phrase"),x.optString("source_name"),x.optString("source_url")) }
  val events=mutableListOf<AudioEvent>(); val eventIds=mutableSetOf<String>()
  for(i in 0 until t.length()) { val x=t.getJSONObject(i); val id=x.optString("event_id"); val asset=x.optString("asset_id"); val start=x.optLong("start_ms",-1); val end=x.optLong("end_ms",-1); val dur=x.optLong("duration_ms",end-start); require(id.isNotBlank()&&eventIds.add(id)){"Missing/duplicate event ID at row ${i+1}"}; require(ids.contains(asset)){"$id references unknown asset $asset"}; require(start>=0&&end>start&&dur==end-start){"Invalid timing in $id"}; val vol=x.optDouble("volume",1.0); val pan=x.optDouble("pan",0.0); require(vol in 0.0..1.0&&pan in -1.0..1.0){"Invalid volume/pan in $id"}; val fi=x.optLong("fade_in_ms",0); val fo=x.optLong("fade_out_ms",0); require(fi>=0&&fo>=0&&fi+fo<=dur){"Fades exceed event duration in $id"}; events+=AudioEvent(id,asset,start,end,dur,x.optInt("layer",1),vol.toFloat(),fi,fo,pan.toFloat(),x.optString("visual_action"),x.optString("reason")) }
  val duration=p.optLong("video_duration_ms",0); require(duration>0){"project.video_duration_ms must be > 0"}; require(events.all{it.endMs<=duration}){"Event extends beyond video duration"}
  return SfxProject(p.optString("project_name","SFX Project"),duration,p.optDouble("fps",30.0),settings.optBoolean("preserve_original_audio",true),assets,events)
 }
}
