package com.vishal.sfxeditor.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a portable description of the user's offline audio library.
 * It intentionally excludes absolute device paths and audio file contents.
 */
object LibraryManifestBuilder {

    fun build(
        assets: List<AudioAssetEntity>,
        categories: List<AudioCategoryEntity>
    ): JSONObject {
        val categoryById = categories.associateBy { it.id }

        val categoryJson = JSONArray()
        categories
            .sortedWith(compareBy<AudioCategoryEntity> { it.assetType }.thenBy { it.sortOrder }.thenBy { it.name.lowercase() })
            .forEach { category ->
                categoryJson.put(
                    JSONObject()
                        .put("category_id", category.id)
                        .put("name", category.name)
                        .put("type", category.assetType.lowercase())
                        .put("parent_id", category.parentId ?: JSONObject.NULL)
                        .put("folder_path", folderPath(category, categoryById))
                        .put("is_custom", category.isCustom)
                        .put("sort_order", category.sortOrder)
                )
            }

        val assetJson = JSONArray()
        assets
            .sortedWith(compareBy<AudioAssetEntity> { it.assetType }.thenBy { it.fileName.lowercase() })
            .forEach { asset ->
                val category = asset.categoryId?.let(categoryById::get)
                assetJson.put(
                    JSONObject()
                        .put("asset_id", asset.id)
                        .put("filename", asset.fileName)
                        .put("type", asset.assetType.lowercase())
                        .put("category_id", asset.categoryId ?: JSONObject.NULL)
                        .put("category", category?.name ?: "Uncategorized")
                        .put("folder_path", category?.let { folderPath(it, categoryById) } ?: "")
                        .put("duration_ms", asset.durationMs)
                        .put("extension", asset.extension)
                        .put("size_bytes", asset.sizeBytes)
                        .put("tags", parseTags(asset.tagsJson))
                        .put("favorite", asset.isFavorite)
                        .put("available_offline", !asset.missingOnDisk)
                )
            }

        val sfxCount = assets.count { it.assetType.equals(AudioType.SFX.name, true) }
        val musicCount = assets.count { it.assetType.equals(AudioType.MUSIC.name, true) }

        return JSONObject()
            .put("manifest_version", "1.0")
            .put("library_summary", JSONObject()
                .put("total_assets", assets.size)
                .put("sfx_assets", sfxCount)
                .put("music_assets", musicCount)
                .put("total_folders", categories.size)
            )
            .put("folders", categoryJson)
            .put("assets", assetJson)
    }

    private fun folderPath(
        category: AudioCategoryEntity,
        categoryById: Map<String, AudioCategoryEntity>
    ): String {
        val parts = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current: AudioCategoryEntity? = category

        while (current != null && visited.add(current.id)) {
            parts.add(current.name)
            current = current.parentId?.let(categoryById::get)
        }

        return parts.asReversed().joinToString("/")
    }

    private fun parseTags(raw: String): JSONArray {
        return try {
            val parsed = JSONArray(raw)
            JSONArray().also { copy ->
                for (index in 0 until parsed.length()) {
                    val value = parsed.optString(index).trim()
                    if (value.isNotEmpty()) copy.put(value)
                }
            }
        } catch (_: Exception) {
            JSONArray()
        }
    }
}
