
package com.vishal.sfxeditor.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun AudioLibraryRoute() {
    val context = LocalContext.current
    val database = remember(context) {
        LibraryDatabase.getDatabase(context)
    }
    val dao = remember(database) { database.libraryDao() }
    val repository = remember(context, dao) {
        AudioLibraryRepository(context, dao)
    }
    val batchImporter = remember(repository) {
        AudioBatchImporter(repository)
    }
    val previewController = remember(context) {
        AudioPreviewController(context)
    }

    val assets by dao.getAllAssets()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val scope = rememberCoroutineScope()

    var selectedImportType by remember {
        mutableStateOf(AudioType.SFX)
    }
    var playingAssetId by remember {
        mutableStateOf<String?>(null)
    }
    var isPlaying by remember {
        mutableStateOf(false)
    }
    var isImporting by remember {
        mutableStateOf(false)
    }
    var importProgress by remember {
        mutableFloatStateOf(0f)
    }
    var importStatus by remember {
        mutableStateOf("Ready")
    }

    DisposableEffect(previewController) {
        previewController.onPlaybackStateChanged = {
            isPlaying = it
        }

        onDispose {
            previewController.release()
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isImporting = true
                importProgress = 0f

                try {
                    val result = batchImporter.importFiles(
                        uris = uris,
                        type = selectedImportType,
                        onProgress = { progress ->
                            importProgress = progress.percentage / 100f
                            importStatus =
                                "${progress.completed}/${progress.total} processed"
                        }
                    )

                    importStatus =
                        "Imported: ${result.successful.size}, " +
                        "Duplicates: ${result.duplicates.size}, " +
                        "Failed: ${result.failures.size}"
                } catch (error: kotlinx.coroutines.CancellationException) {
                    importStatus = "Import cancelled"
                    throw error
                } catch (error: Exception) {
                    importStatus = "Import failed: ${error.message}"
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AudioLibraryScreen(
            assets = assets,
            playingAssetId = playingAssetId,
            isPlaying = isPlaying,
            onImport = { type ->
                selectedImportType = type
                importPicker.launch(arrayOf("audio/*"))
            },
            onPlay = { asset ->
                playingAssetId = asset.id

                if (!previewController.play(asset)) {
                    importStatus = "Audio file missing: ${asset.fileName}"
                    playingAssetId = null
                }
            },
            onPause = {
                previewController.pause()
            },
            onFavorite = { asset ->
                scope.launch {
                    dao.updateAsset(
                        asset.copy(isFavorite = !asset.isFavorite)
                    )
                }
            }
        )

        if (isImporting) {
            LinearProgressIndicator(
                progress = { importProgress },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Text(
            text = importStatus,
            modifier = Modifier.padding(12.dp)
        )
    }
}
