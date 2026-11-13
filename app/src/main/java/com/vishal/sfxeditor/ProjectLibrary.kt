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

    /** Normal project list; the reserved recovery checkpoint is hidden. */
    fun load(context: Context): List<SavedProject> =
        loadAll(context).filterNot { it.id == ProjectRecoveryManager.RECOVERY_ID }

    /** Read only the reserved crash-recovery checkpoint. */
    fun loadRecoveryCheckpoint(context: Context): SavedProject? =
        loadAll(context).firstOrNull { it.id == ProjectRecoveryManager.RECOVERY_ID }

    /** Internal complete list including the recovery checkpoint. */
    private fun loadAll(context: Context): List<SavedProject> {
        return try {
            val array = JSONArray(
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]")
            )
            (0 until array.length())
                .mapNotNull { index ->
                    runCatching { decode(array.getJSONObject(index)) }.getOrNull()
                }
                .sortedByDescending { it.savedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsert(context: Context, item: SavedProject) {
        val all = loadAll(context)
            .filterNot { it.id == item.id }
            .toMutableList()
        all.add(item)
        persist(context, all)
    }

    fun delete(context: Context, id: String) {
        persist(context, loadAll(context).filterNot { it.id == id })
    }

    /**
     * Rename an existing project without changing its timeline or media links.
     * Returns false when the project does not exist or the name is blank.
     */
    fun rename(context: Context, id: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false

        // Use the complete stored list here, not load(): load() intentionally
        // hides the reserved recovery checkpoint. Persisting only the visible
        // projects would otherwise erase that checkpoint during a rename.
        val allProjects = loadAll(context).toMutableList()
        val index = allProjects.indexOfFirst { it.id == id }
        if (index < 0) return false

        allProjects[index] = allProjects[index].copy(
            name = cleanName,
            savedAt = System.currentTimeMillis()
        )
        persist(context, allProjects)
        return true
    }

    /**
     * Creates an independent project-library entry while preserving the
     * source project's asset IDs, timeline event IDs, timing and mix settings.
     */
    fun duplicate(context: Context, id: String): SavedProject? {
        val source = load(context).firstOrNull { it.id == id } ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = uniqueCopyName(source.name, load(context).map { it.name }),
            savedAt = now
        )
        upsert(context, copy)
        return copy
    }

    private fun uniqueCopyName(original: String, existingNames: List<String>): String {
        val existing = existingNames.map { it.trim().lowercase() }.toSet()
        var candidate = "$original Copy"
        var suffix = 2
        while (candidate.lowercase() in existing) {
            candidate = "$original Copy $suffix"
            suffix++
        }
        return candidate
    }

    private fun persist(context: Context, items: List<SavedProject>) {
        val array = JSONArray()
        items.forEach { array.put(encode(it)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun encode(saved: SavedProject): JSONObject {
        val assetsJson = JSONArray()
        saved.assets.forEach { asset ->
            assetsJson.put(
                JSONObject()
                    .put("id", asset.id)
                    .put("filename", asset.filename)
                    .put("type", asset.type)
                    .put("description", asset.description)
                    .put("required", asset.required)
                    .put("searchPhrase", asset.searchPhrase)
                    .put("sourceName", asset.sourceName)
                    .put("sourceUrl", asset.sourceUrl)
                    .put("uri", asset.uri?.toString() ?: "")
                    .put("libraryAssetId", asset.libraryAssetId ?: JSONObject.NULL)
                    .put("libraryCategoryId", asset.libraryCategoryId ?: JSONObject.NULL)
            )
        }

        val eventsJson = JSONArray()
        saved.events.forEach { event ->
            eventsJson.put(
                JSONObject()
                    .put("id", event.id)
                    .put("assetId", event.assetId)
                    .put("startMs", event.startMs)
                    .put("endMs", event.endMs)
                    .put("durationMs", event.durationMs)
                    .put("layer", event.layer)
                    .put("volume", event.volume.toDouble())
                    .put("fadeInMs", event.fadeInMs)
                    .put("fadeOutMs", event.fadeOutMs)
                    .put("pan", event.pan.toDouble())
                    .put("visualAction", event.visualAction)
                    .put("reason", event.reason)
            )
        }

        return JSONObject()
            .put("id", saved.id)
            .put("name", saved.name)
            .put("savedAt", saved.savedAt)
            .put("videoUri", saved.videoUri)
            .put("videoName", saved.videoName)
            .put(
                "project",
                JSONObject()
                    .put("name", saved.project.name)
                    .put("durationMs", saved.project.durationMs)
                    .put("fps", saved.project.fps)
                    .put("preserveOriginalAudio", saved.project.preserveOriginalAudio)
            )
            .put("assets", assetsJson)
            .put("events", eventsJson)
    }

    private fun decode(json: JSONObject): SavedProject {
        val projectJson = json.getJSONObject("project")
        val assetsJson = json.getJSONArray("assets")

        val assets = (0 until assetsJson.length()).map { index ->
            val asset = assetsJson.getJSONObject(index)
            AssetSlot(
                id = asset.getString("id"),
                filename = asset.getString("filename"),
                type = asset.optString("type", "sfx"),
                description = asset.optString("description", ""),
                required = asset.optBoolean("required", true),
                searchPhrase = asset.optString("searchPhrase", ""),
                sourceName = asset.optString("sourceName", ""),
                sourceUrl = asset.optString("sourceUrl", ""),
                uri = asset.optString("uri")
                    .takeIf { it.isNotBlank() }
                    ?.let(Uri::parse),
                libraryAssetId = asset.optionalNullableString("libraryAssetId"),
                libraryCategoryId = asset.optionalNullableString("libraryCategoryId")
            )
        }

        val eventsJson = json.getJSONArray("events")
        val events = (0 until eventsJson.length()).map { index ->
            val event = eventsJson.getJSONObject(index)
            AudioEvent(
                id = event.getString("id"),
                assetId = event.getString("assetId"),
                startMs = event.getLong("startMs"),
                endMs = event.getLong("endMs"),
                durationMs = event.getLong("durationMs"),
                layer = event.optInt("layer", 1),
                volume = event.optDouble("volume", 1.0).toFloat(),
                fadeInMs = event.optLong("fadeInMs", 0L),
                fadeOutMs = event.optLong("fadeOutMs", 0L),
                pan = event.optDouble("pan", 0.0).toFloat(),
                visualAction = event.optString("visualAction", ""),
                reason = event.optString("reason", "")
            )
        }

        return SavedProject(
            id = json.getString("id"),
            name = json.getString("name"),
            savedAt = json.optLong("savedAt", 0L),
            videoUri = json.optString("videoUri", ""),
            videoName = json.optString("videoName", ""),
            project = SfxProject(
                name = projectJson.getString("name"),
                durationMs = projectJson.getLong("durationMs"),
                fps = projectJson.optDouble("fps", 30.0),
                preserveOriginalAudio = projectJson.optBoolean("preserveOriginalAudio", true),
                assets = assets,
                events = events
            ),
            assets = assets,
            events = events
        )
    }

    private fun JSONObject.optionalNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key)
            .trim()
            .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }
}
