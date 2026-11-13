
package com.vishal.sfxeditor.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_assets",
    indices = [
        Index(value = ["sha256"]),
        Index(value = ["categoryId"]),
        Index(value = ["assetType"]),
        Index(value = ["fileName"])
    ]
)
data class AudioAssetEntity(
    @PrimaryKey
    val id: String,
    val fileName: String,
    val assetType: String,
    val relativePath: String,
    val categoryId: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val sha256: String,
    val extension: String,
    val tagsJson: String = "[]",
    val isFavorite: Boolean = false,
    val importedAt: Long,
    val missingOnDisk: Boolean = false
)

@Entity(
    tableName = "audio_categories",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["assetType"])
    ]
)
data class AudioCategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val assetType: String,
    val parentId: String? = null,
    val sortOrder: Int = 0,
    val isCustom: Boolean = false,
    val createdAt: Long
)
