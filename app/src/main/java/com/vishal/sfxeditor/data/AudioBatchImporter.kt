
package com.vishal.sfxeditor.data

import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class AudioBatchProgress(
    val total: Int,
    val completed: Int,
    val successful: Int,
    val duplicates: Int,
    val failed: Int,
    val currentFile: String? = null
) {
    val percentage: Int
        get() = if (total == 0) 100 else completed * 100 / total
}

data class AudioBatchResult(
    val successful: List<AudioAssetEntity>,
    val duplicates: List<AudioAssetEntity>,
    val failures: List<Pair<Uri, String>>
)

class AudioBatchImporter(
    private val repository: AudioLibraryRepository
) {

    suspend fun importFiles(
        uris: List<Uri>,
        type: AudioType,
        categoryId: String? = null,
        onProgress: (AudioBatchProgress) -> Unit = {}
    ): AudioBatchResult {

        val successful = mutableListOf<AudioAssetEntity>()
        val duplicates = mutableListOf<AudioAssetEntity>()
        val failures = mutableListOf<Pair<Uri, String>>()

        var completed = 0

        onProgress(
            AudioBatchProgress(
                total = uris.size,
                completed = 0,
                successful = 0,
                duplicates = 0,
                failed = 0
            )
        )

        for (uri in uris) {
            currentCoroutineContext().ensureActive()

            when (
                val result = repository.importAudio(
                    uri = uri,
                    type = type,
                    categoryId = categoryId
                )
            ) {
                is AudioImportResult.Success -> {
                    successful.add(result.asset)
                }

                is AudioImportResult.Duplicate -> {
                    duplicates.add(result.existing)
                }

                is AudioImportResult.Failure -> {
                    failures.add(uri to result.reason)
                }
            }

            completed++

            onProgress(
                AudioBatchProgress(
                    total = uris.size,
                    completed = completed,
                    successful = successful.size,
                    duplicates = duplicates.size,
                    failed = failures.size,
                    currentFile = uri.lastPathSegment
                )
            )
        }

        return AudioBatchResult(
            successful = successful,
            duplicates = duplicates,
            failures = failures
        )
    }
}
