
package com.vishal.sfxeditor.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

enum class AudioType {
    SFX,
    MUSIC
}

sealed class AudioImportResult {
    data class Success(val asset: AudioAssetEntity) : AudioImportResult()
    data class Duplicate(val existing: AudioAssetEntity) : AudioImportResult()
    data class Failure(val reason: String) : AudioImportResult()
}

class AudioLibraryRepository(
    private val context: Context,
    private val dao: LibraryDao
) {

    suspend fun importAudio(
        uri: Uri,
        type: AudioType,
        categoryId: String? = null
    ): AudioImportResult = withContext(Dispatchers.IO) {

        val resolver = context.contentResolver
        val originalName = getDisplayName(uri) ?: "audio_${System.currentTimeMillis()}"

        val extension = originalName
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            ?: resolver.getType(uri)?.substringAfter("/")?.substringBefore(";")
                ?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }
            ?: "audio"

        val directory = File(
            context.filesDir,
            if (type == AudioType.SFX) "audio/sfx" else "audio/music"
        )

        if (!directory.exists() && !directory.mkdirs()) {
            return@withContext AudioImportResult.Failure(
                "Could not create audio storage directory"
            )
        }

        val id = UUID.randomUUID().toString()
        val temporaryFile = File(directory, "$id.tmp")
        var finalFile: File? = null

        try {
            val digest = MessageDigest.getInstance("SHA-256")

            val input = resolver.openInputStream(uri)
                ?: return@withContext AudioImportResult.Failure(
                    "Cannot open selected audio file"
                )

            var totalBytes = 0L

            input.use { source ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)

                    while (true) {
                        val count = source.read(buffer)
                        if (count == -1) break

                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        totalBytes += count
                    }
                }
            }

            if (totalBytes == 0L) {
                temporaryFile.delete()
                return@withContext AudioImportResult.Failure("Audio file is empty")
            }

            val hash = digest.digest().joinToString("") {
                "%02x".format(it)
            }

            val duplicate = dao.findByHash(hash)

            if (duplicate != null) {
                temporaryFile.delete()
                return@withContext AudioImportResult.Duplicate(duplicate)
            }

            val metadata = MediaMetadataRetriever()
            val durationMs = try {
                metadata.setDataSource(temporaryFile.absolutePath)
                metadata.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION
                )?.toLongOrNull() ?: 0L
            } finally {
                metadata.release()
            }

            if (durationMs <= 0L) {
                temporaryFile.delete()
                return@withContext AudioImportResult.Failure(
                    "Unsupported or invalid audio file"
                )
            }

            val storedName = "$id.$extension"
            finalFile = File(directory, storedName)

            if (!temporaryFile.renameTo(finalFile)) {
                return@withContext AudioImportResult.Failure(
                    "Could not save audio file"
                )
            }

            val relativePath = if (type == AudioType.SFX) {
                "audio/sfx/$storedName"
            } else {
                "audio/music/$storedName"
            }

            val asset = AudioAssetEntity(
                id = id,
                fileName = originalName,
                assetType = type.name,
                relativePath = relativePath,
                categoryId = categoryId,
                durationMs = durationMs,
                sizeBytes = totalBytes,
                sha256 = hash,
                extension = extension,
                importedAt = System.currentTimeMillis()
            )

            try {
                dao.insertAsset(asset)
            } catch (error: Exception) {
                finalFile.delete()
                throw error
            }

            AudioImportResult.Success(asset)

        } catch (error: SecurityException) {
            temporaryFile.delete()
            finalFile?.delete()
            AudioImportResult.Failure("Permission denied while reading audio")

        } catch (error: IOException) {
            temporaryFile.delete()
            finalFile?.delete()
            AudioImportResult.Failure(
                error.message ?: "Audio import failed"
            )

        } catch (error: Exception) {
            temporaryFile.delete()
            finalFile?.delete()
            AudioImportResult.Failure(
                error.message ?: "Unexpected import error"
            )
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }
}
