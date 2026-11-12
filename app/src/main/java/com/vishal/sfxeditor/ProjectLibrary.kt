package com.vishal.sfxeditor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

 data class SavedProject(
    val id: String,
    val name: String,
    val savedAt: Long,
    val videoUri: String,
    val videoName: String,
    val project: SfxProject,
    val assets: List<AssetSlot>,
    val events: List<AudioEvent>
 )

object ProjectLibrary {
    private const val PREFS = "sfx_project_library"
    private const val KEY = "projects"

    fun load(context: Context): List<SavedProject> {
        return try {
            val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
            (0 until array.length()).mapNotNull { i -> runCatching { decode(array.getJSONObject(i)) }.getOrNull() }
                .sortedByDescending { it.savedAt }
        } catch (_: Exception) { emptyList() }
    }

    fun upsert(context: Context, item: SavedProject) {
        val all = load(context).filterNot { it.id == item.id }.toMutableList()
        all.add(item)
        persist(context, all)
    }

    fun delete(context: Context, id: String) = persist(context, load(context).filterNot { it.id == id })

    private fun persist(context: Context, items: List<SavedProject>) {
        val arr = JSONArray()
        items.forEach { arr.put(encode(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    private fun encode(s: SavedProject): JSONObject {
        val assets = JSONArray(); s.assets.forEach { a -> assets.put(JSONObject().put("id",a.id).put("filename",a.filename).put("type",a.type).put("description",a.description).put("required",a.required).put("searchPhrase",a.searchPhrase).put("sourceName",a.sourceName).put("sourceUrl",a.sourceUrl).put("uri",a.uri?.toString() ?: "")) }
        val events = JSONArray(); s.events.forEach { e -> events.put(JSONObject().put("id",e.id).put("assetId",e.assetId).put("startMs",e.startMs).put("endMs",e.endMs).put("durationMs",e.durationMs).put("layer",e.layer).put("volume",e.volume.toDouble()).put("fadeInMs",e.fadeInMs).put("fadeOutMs",e.fadeOutMs).put("pan",e.pan.toDouble()).put("visualAction",e.visualAction).put("reason",e.reason)) }
        return JSONObject().put("id",s.id).put("name",s.name).put("savedAt",s.savedAt).put("videoUri",s.videoUri).put("videoName",s.videoName)
            .put("project",JSONObject().put("name",s.project.name).put("durationMs",s.project.durationMs).put("fps",s.project.fps).put("preserveOriginalAudio",s.project.preserveOriginalAudio))
            .put("assets",assets).put("events",events)
    }

    private fun decode(o: JSONObject): SavedProject {
        val p=o.getJSONObject("project")
        val assetsJson=o.getJSONArray("assets")
        val assets=(0 until assetsJson.length()).map { i -> val a=assetsJson.getJSONObject(i); AssetSlot(a.getString("id"),a.getString("filename"),a.optString("type","sfx"),a.optString("description"),a.optBoolean("required",true),a.optString("searchPhrase"),a.optString("sourceName"),a.optString("sourceUrl"),a.optString("uri").takeIf{it.isNotBlank()}?.let{Uri.parse(it)}) }
        val eventsJson=o.getJSONArray("events")
        val events=(0 until eventsJson.length()).map { i -> val e=eventsJson.getJSONObject(i); AudioEvent(e.getString("id"),e.getString("assetId"),e.getLong("startMs"),e.getLong("endMs"),e.getLong("durationMs"),e.optInt("layer",1),e.optDouble("volume",1.0).toFloat(),e.optLong("fadeInMs"),e.optLong("fadeOutMs"),e.optDouble("pan",0.0).toFloat(),e.optString("visualAction"),e.optString("reason")) }
        return SavedProject(o.getString("id"),o.getString("name"),o.optLong("savedAt"),o.optString("videoUri"),o.optString("videoName"),SfxProject(p.getString("name"),p.getLong("durationMs"),p.optDouble("fps",30.0),p.optBoolean("preserveOriginalAudio",true),assets,events),assets,events)
    }
}
