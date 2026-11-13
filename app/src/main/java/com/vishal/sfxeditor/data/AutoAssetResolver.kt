package com.vishal.sfxeditor.data

import android.content.Context
import android.net.Uri
import com.vishal.sfxeditor.AssetSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Resolves project asset slots against the user's offline audio library.
 * This class never accesses the network and never downloads audio.
 * Matching is deliberately conservative: only an exact normalized filename
 * or exact normalized search phrase/tag match is accepted.
 */
object AutoAssetResolver {

    data class Result(
        val assets: List<AssetSlot>,
        val matched: Int,
        val missing: Int,
        val messages: List<String>
    )

    suspend fun resolve(
        context: Context,
        slots: List<AssetSlot>,
        dao: LibraryDao
    ): Result = withContext(Dispatchers.IO) {
        val libraryAssets = dao.getAllAssets().first()
        val resolved = mutableListOf<AssetSlot>()
        val messages = mutableListOf<String>()
        var matchedCount = 0
        var missingCount = 0

        for (slot in slots) {
            val expectedType = if (slot.type.contains("music", ignoreCase = true)) {
                AudioType.MUSIC.name
            } else {
                AudioType.SFX.name
            }

            val filenameKey = normalize(slot.filename)
            val searchKey = normalize(slot.searchPhrase)

            val candidates = libraryAssets.filter { libraryAsset ->
                libraryAsset.assetType.equals(expectedType, ignoreCase = true) &&
                    !libraryAsset.missingOnDisk
            }

            val match = candidates.firstOrNull { candidate ->
                normalize(candidate.fileName) == filenameKey ||
                    (searchKey.isNotBlank() && normalize(candidate.fileName) == searchKey)
            }

            if (match == null) {
                missingCount++
                messages += "${slot.filename}: not found in offline library"
                resolved += slot.copy(
                    uri = null,
                    libraryAssetId = null,
                    libraryCategoryId = null
                )
                continue
            }

            val audioFile = File(context.filesDir, match.relativePath).canonicalFile
            val audioRoot = File(context.filesDir, "audio").canonicalFile

            if (!audioFile.path.startsWith(audioRoot.path + File.separator) || !audioFile.isFile) {
                missingCount++
                messages += "${slot.filename}: library file is missing or inaccessible"
                resolved += slot.copy(
                    uri = null,
                    libraryAssetId = null,
                    libraryCategoryId = null
                )
                continue
            }

            matchedCount++
            resolved += slot.copy(
                filename = match.fileName,
                uri = Uri.fromFile(audioFile),
                libraryAssetId = match.id,
                libraryCategoryId = match.categoryId
            )
            messages += "${slot.filename}: matched with ${match.fileName}"
        }

        Result(
            assets = resolved,
            matched = matchedCount,
            missing = missingCount,
            messages = messages
        )
    }

    private fun normalize(value: String): String {
        val withoutExtension = value.trim().substringBeforeLast('.', value.trim())
        return withoutExtension
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "")
    }
}
