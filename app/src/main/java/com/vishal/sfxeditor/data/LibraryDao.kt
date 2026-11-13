
package com.vishal.sfxeditor.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    // Audio assets
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAsset(asset: AudioAssetEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssets(assets: List<AudioAssetEntity>)

    @Update
    suspend fun updateAsset(asset: AudioAssetEntity)

    @Delete
    suspend fun deleteAsset(asset: AudioAssetEntity)

    @Query("SELECT * FROM audio_assets ORDER BY fileName COLLATE NOCASE ASC")
    fun getAllAssets(): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE assetType = :type ORDER BY fileName COLLATE NOCASE ASC")
    fun getAssetsByType(type: String): Flow<List<AudioAssetEntity>>

    @Query("""
        SELECT * FROM audio_assets
        WHERE fileName LIKE '%' || :query || '%'
        OR tagsJson LIKE '%' || :query || '%'
        ORDER BY fileName COLLATE NOCASE ASC
    """)
    fun searchAssets(query: String): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE categoryId = :categoryId ORDER BY fileName COLLATE NOCASE ASC")
    fun getAssetsByCategory(categoryId: String): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE isFavorite = 1 ORDER BY fileName COLLATE NOCASE ASC")
    fun getFavorites(): Flow<List<AudioAssetEntity>>

    @Query("SELECT * FROM audio_assets WHERE sha256 = :hash LIMIT 1")
    suspend fun findByHash(hash: String): AudioAssetEntity?

    @Query("SELECT * FROM audio_assets WHERE id = :id LIMIT 1")
    suspend fun getAssetById(id: String): AudioAssetEntity?

    @Query("SELECT COUNT(*) FROM audio_assets")
    suspend fun getAssetCount(): Int

    // Categories
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: AudioCategoryEntity)

    @Update
    suspend fun updateCategory(category: AudioCategoryEntity)

    @Delete
    suspend fun deleteCategory(category: AudioCategoryEntity)

    @Query("SELECT * FROM audio_categories ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getAllCategories(): Flow<List<AudioCategoryEntity>>

    @Query("SELECT * FROM audio_categories WHERE assetType = :type ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getCategoriesByType(type: String): Flow<List<AudioCategoryEntity>>

    @Query("SELECT * FROM audio_categories WHERE parentId = :parentId ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun getChildCategories(parentId: String): Flow<List<AudioCategoryEntity>>

    @Query("SELECT * FROM audio_categories WHERE id = :id LIMIT 1")
    suspend fun getCategoryById(id: String): AudioCategoryEntity?
}
