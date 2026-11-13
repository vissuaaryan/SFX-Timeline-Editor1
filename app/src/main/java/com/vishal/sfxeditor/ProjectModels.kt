package com.vishal.sfxeditor

import android.net.Uri
import org.json.JSONObject

/**
 * A project asset can point to an item already stored in the offline library.
 * Library IDs are optional so older project JSON files remain compatible.
 */
data class AssetSlot(
    val id: String,
    val filename: String,
    val type: String,
    val description: String,
    val required: Boolean,
    val searchPhrase: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val uri: Uri? = null,
    val libraryAssetId: String? = null,
    val libraryCategoryId: String? = null
)

data class AudioEvent(
    val id: String,
    val assetId: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val layer: Int,
    val volume: Float,
    val fadeInMs: Long,
    val fadeOutMs: Long,
    val pan: Float,
    val visualAction: String,
    val reason: String
)

data class SfxProject(
    val name: String,
    val durationMs: Long,
    val fps: Double,
    val preserveOriginalAudio: Boolean,
    val assets: List<AssetSlot>,
    val events: List<AudioEvent>
)

object ProjectParser {
    fun parse(raw: String): SfxProject {
        val root = JSONObject(raw)
        require(root.optString("schema_version").isNotBlank()) {
            "schema_version missing"
        }

        val project = root.getJSONObject("project")
        val settings = root.optJSONObject("audio_settings") ?: JSONObject()
        val assetArray = root.getJSONArray("assets")
        val timelineArray = root.getJSONArray("timeline")

        val assets = mutableListOf<AssetSlot>()
        val assetIds = mutableSetOf<String>()

        for (index in 0 until assetArray.length()) {
            val item = assetArray.getJSONObject(index)
            val id = item.optString("id").trim()
            val filename = item.optString("filename").trim()

            require(id.isNotBlank() && filename.isNotBlank()) {
                "Asset #${index + 1}: id/filename missing"
            }
            require(assetIds.add(id)) { "Duplicate asset ID $id" }

            val libraryAssetId = item.optionalString("library_asset_id")
            val libraryCategoryId = item.optionalString("library_category_id")
                ?: item.optionalString("category_id")

            assets += AssetSlot(
                id = id,
                filename = filename,
                type = item.optString("type", "sfx"),
                description = item.optString("description", ""),
                required = item.optBoolean("required", true),
                searchPhrase = item.optString("search_phrase", ""),
                sourceName = item.optString("source_name", ""),
                sourceUrl = item.optString("source_url", ""),
                libraryAssetId = libraryAssetId,
                libraryCategoryId = libraryCategoryId
            )
        }

        val events = mutableListOf<AudioEvent>()
        val eventIds = mutableSetOf<String>()

        for (index in 0 until timelineArray.length()) {
            val item = timelineArray.getJSONObject(index)
            val id = item.optString("event_id").trim()
            val assetId = item.optString("asset_id").trim()
            val startMs = item.optLong("start_ms", -1L)
            val endMs = item.optLong("end_ms", -1L)
            val durationMs = item.optLong("duration_ms", endMs - startMs)

            require(id.isNotBlank() && eventIds.add(id)) {
                "Missing/duplicate event ID at row ${index + 1}"
            }
            require(assetIds.contains(assetId)) {
                "$id references unknown asset $assetId"
            }
            require(startMs >= 0L && endMs > startMs && durationMs == endMs - startMs) {
                "Invalid timing in $id"
            }

            val volume = item.optDouble("volume", 1.0)
            val pan = item.optDouble("pan", 0.0)
            require(volume in 0.0..1.0 && pan in -1.0..1.0) {
                "Invalid volume/pan in $id"
            }

            val fadeInMs = item.optLong("fade_in_ms", 0L)
            val fadeOutMs = item.optLong("fade_out_ms", 0L)
            require(fadeInMs >= 0L && fadeOutMs >= 0L && fadeInMs + fadeOutMs <= durationMs) {
                "Fades exceed event duration in $id"
            }

            events += AudioEvent(
                id = id,
                assetId = assetId,
                startMs = startMs,
                endMs = endMs,
                durationMs = durationMs,
                layer = item.optInt("layer", 1),
                volume = volume.toFloat(),
                fadeInMs = fadeInMs,
                fadeOutMs = fadeOutMs,
                pan = pan.toFloat(),
                visualAction = item.optString("visual_action", ""),
                reason = item.optString("reason", "")
            )
        }

        val videoDuration = project.optLong("video_duration_ms", 0L)
        require(videoDuration > 0L) {
            "project.video_duration_ms must be > 0"
        }
        require(events.all { it.endMs <= videoDuration }) {
            "Event extends beyond video duration"
        }

        return SfxProject(
            name = project.optString("project_name", "SFX Project"),
            durationMs = videoDuration,
            fps = project.optDouble("fps", 30.0),
            preserveOriginalAudio = settings.optBoolean("preserve_original_audio", true),
            assets = assets,
            events = events
        )
    }

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() && !it.equals("null", true) }
    }
}
