package com.vishal.sfxeditor.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full offline library backup.
 *
 * The ZIP contains:
 * - manifest.json: categories and audio metadata
 * - audio/<asset-id>.<extension>: original audio bytes
 *
 * This class deliberately performs no network access.
 */
class LibraryBackupManager(
    private val context: Context,
    private val dao: LibraryDao
) {
    data class BackupResult(
        val categories: Int,
        val assets: Int,
        val missingFiles: Int = 0
    )

    data class RestoreResult(
        val categoriesAdded: Int,
        val assetsAdded: Int,
        val alreadyPresent: Int,
        val missingAudio: Int
    )

    suspend fun exportBackup(destination: Uri): Result<BackupResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val categories = dao.getAllCategories().first()
                val assets = dao.getAllAssets().first()

                val manifest = JSONObject()
                    .put("format", "sfx-library-backup")
                    .put("version", 1)
                    .put("created_at", System.currentTimeMillis())

                val categoryArray = JSONArray()
                categories.forEach { category ->
                    categoryArray.put(
                        JSONObject()
                            .put("id", category.id)
                            .put("name", category.name)
                            .put("assetType", category.assetType)
                            .put("parentId", category.parentId ?: JSONObject.NULL)
                            .put("sortOrder", category.sortOrder)
                            .put("isCustom", category.isCustom)
                            .put("createdAt", category.createdAt)
                    )
                }

                val assetArray = JSONArray()
                var missing = 0
                assets.forEach { asset ->
                    val file = File(context.filesDir, asset.relativePath).canonicalFile
                    val audioRoot = File(context.filesDir, "audio").canonicalFile
                    val isSafe = file.path.startsWith(audioRoot.path + File.separator)
                    val exists = isSafe && file.isFile

                    if (!exists) missing++

                    assetArray.put(
                        JSONObject()
                            .put("id", asset.id)
                            .put("fileName", asset.fileName)
                            .put("assetType", asset.assetType)
                            .put("relativePath", asset.relativePath)
                            .put("categoryId", asset.categoryId ?: JSONObject.NULL)
                            .put("durationMs", asset.durationMs)
                            .put("sizeBytes", asset.sizeBytes)
                            .put("sha256", asset.sha256)
                            .put("extension", asset.extension)
                            .put("tagsJson", asset.tagsJson)
                            .put("isFavorite", asset.isFavorite)
                            .put("importedAt", asset.importedAt)
                            .put("missingOnDisk", !exists)
                            .put("hasBackupFile", exists)
                    )
                }

                manifest.put("categories", categoryArray)
                manifest.put("assets", assetArray)

                val output = context.contentResolver.openOutputStream(destination)
                    ?: error("Cannot open selected backup destination")

                var exported = 0
                ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    assets.forEach { asset ->
                        val source = File(context.filesDir, asset.relativePath).canonicalFile
                        val audioRoot = File(context.filesDir, "audio").canonicalFile
                        if (source.isFile &&
                            source.path.startsWith(audioRoot.path + File.separator)
                        ) {
                            val extension = asset.extension
                                .lowercase()
                                .filter { it.isLetterOrDigit() }
                                .take(10)
                                .ifBlank { "audio" }
                            zip.putNextEntry(
                                ZipEntry("audio/${asset.id}.$extension")
                            )
                            source.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                            exported++
                        }
                    }
                }

                BackupResult(categories.size, exported, missing)
            }
        }

    suspend fun restoreBackup(source: Uri): Result<RestoreResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tempRoot = File(context.cacheDir, "library_restore_${System.currentTimeMillis()}")
                check(tempRoot.mkdirs()) { "Cannot create temporary restore folder" }

                try {
                    val manifestFile = File(tempRoot, "manifest.json")
                    val audioFolder = File(tempRoot, "audio")
                    check(audioFolder.mkdirs()) { "Cannot create temporary audio folder" }

                    val input = context.contentResolver.openInputStream(source)
                        ?: error("Cannot open selected backup file")

                    ZipInputStream(BufferedInputStream(input)).use { zip ->
                        var entry: ZipEntry?
                        var totalBytes = 0L
                        while (zip.nextEntry.also { entry = it } != null) {
                            val current = entry ?: continue
                            val name = current.name.replace('\\', '/')
                            check(!name.startsWith("/") && !name.split('/').contains("..")) {
                                "Invalid path inside backup"
                            }

                            when {
                                name == "manifest.json" -> {
                                    copyEntry(zip, manifestFile, MAX_MANIFEST_BYTES)
                                }
                                name.startsWith("audio/") && !current.isDirectory -> {
                                    val leaf = name.removePrefix("audio/")
                                    check('/' !in leaf && leaf.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,10}"))) {
                                        "Invalid audio filename in backup"
                                    }
                                    val target = File(audioFolder, leaf)
                                    totalBytes += copyEntry(zip, target, MAX_TOTAL_AUDIO_BYTES - totalBytes)
                                    check(totalBytes <= MAX_TOTAL_AUDIO_BYTES) {
                                        "Backup audio exceeds the 2 GB restore safety limit"
                                    }
                                }
                            }
                            zip.closeEntry()
                        }
                    }

                    check(manifestFile.isFile) { "Backup manifest is missing" }
                    val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
                    check(manifest.optString("format") == "sfx-library-backup") {
                        "This is not an SFX Library backup"
                    }
                    check(manifest.optInt("version") == 1) {
                        "Unsupported backup version"
                    }

                    val categoriesJson = manifest.optJSONArray("categories") ?: JSONArray()
                    val assetsJson = manifest.optJSONArray("assets") ?: JSONArray()

                    var categoriesAdded = 0
                    for (i in 0 until categoriesJson.length()) {
                        val item = categoriesJson.getJSONObject(i)
                        val id = item.getString("id")
                        if (dao.getCategoryById(id) == null) {
                            dao.insertCategory(
                                AudioCategoryEntity(
                                    id = id,
                                    name = item.getString("name"),
                                    assetType = item.getString("assetType"),
                                    parentId = item.optNullableString("parentId"),
                                    sortOrder = item.optInt("sortOrder", 0),
                                    isCustom = item.optBoolean("isCustom", false),
                                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                                )
                            )
                            categoriesAdded++
                        }
                    }

                    var assetsAdded = 0
                    var alreadyPresent = 0
                    var missingAudio = 0
                    val audioRoot = File(context.filesDir, "audio").canonicalFile

                    for (i in 0 until assetsJson.length()) {
                        val item = assetsJson.getJSONObject(i)
                        val id = item.getString("id")

                        if (dao.getAssetById(id) != null) {
                            alreadyPresent++
                            continue
                        }

                        val extension = item.optString("extension", "audio")
                            .lowercase()
                            .filter { it.isLetterOrDigit() }
                            .take(10)
                            .ifBlank { "audio" }
                        val backupAudio = File(audioFolder, "$id.$extension")
                        if (!backupAudio.isFile) {
                            missingAudio++
                            continue
                        }

                        val type = item.optString("assetType", "SFX")
                        val subdirectory = if (type.equals("MUSIC", true)) "music" else "sfx"
                        val destinationDir = File(audioRoot, subdirectory)
                        check(destinationDir.mkdirs() || destinationDir.isDirectory) {
                            "Cannot create audio storage directory"
                        }
                        val destination = File(destinationDir, "$id.$extension").canonicalFile
                        check(destination.path.startsWith(audioRoot.path + File.separator)) {
                            "Unsafe restore destination"
                        }

                        backupAudio.copyTo(destination, overwrite = false)
                        val actualHash = sha256(destination)
                        val relativePath = "audio/$subdirectory/${destination.name}"

                        try {
                            dao.insertAsset(
                                AudioAssetEntity(
                                    id = id,
                                    fileName = item.getString("fileName"),
                                    assetType = type,
                                    relativePath = relativePath,
                                    categoryId = item.optNullableString("categoryId"),
                                    durationMs = item.optLong("durationMs", 0L),
                                    sizeBytes = destination.length(),
                                    sha256 = actualHash,
                                    extension = extension,
                                    tagsJson = item.optString("tagsJson", "[]"),
                                    isFavorite = item.optBoolean("isFavorite", false),
                                    importedAt = item.optLong("importedAt", System.currentTimeMillis()),
                                    missingOnDisk = false
                                )
                            )
                            assetsAdded++
                        } catch (error: Exception) {
                            destination.delete()
                            throw error
                        }
                    }

                    RestoreResult(
                        categoriesAdded = categoriesAdded,
                        assetsAdded = assetsAdded,
                        alreadyPresent = alreadyPresent,
                        missingAudio = missingAudio
                    )
                } finally {
                    tempRoot.deleteRecursively()
                }
            }
        }

    private fun copyEntry(
        input: ZipInputStream,
        target: File,
        maxBytes: Long
    ): Long {
        require(maxBytes >= 0L) { "Backup size limit exceeded" }
        target.parentFile?.mkdirs()
        var copied = 0L
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                require(copied <= maxBytes) { "Backup entry exceeds size limit" }
                output.write(buffer, 0, count)
            }
        }
        return copied
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 10L * 1024L * 1024L
        const val MAX_TOTAL_AUDIO_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
