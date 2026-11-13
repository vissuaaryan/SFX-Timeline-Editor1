
package com.vishal.sfxeditor.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AudioLibraryRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val database = remember(context) {
        LibraryDatabase.getDatabase(context)
    }

    val dao = remember(database) {
        database.libraryDao()
    }

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
        .collectAsState(initial = emptyList())

    var playingAssetId by remember {
        mutableStateOf<String?>(null)
    }

    var isPlaying by remember {
        mutableStateOf(false)
    }

    var importType by remember {
        mutableStateOf(AudioType.SFX)
    }

    var importProgress by remember {
        mutableStateOf<AudioBatchProgress?>(null)
    }

    var importMessage by remember {
        mutableStateOf("")
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                importMessage = "Importing audio..."
                importProgress = null

                try {
                    val result = batchImporter.importFiles(
                        uris = uris,
                        type = importType,
                        onProgress = { progress ->
                            importProgress = progress
                        }
                    )

                    importMessage =
                        "Imported: ${result.successful.size} · " +
                        "Duplicates: ${result.duplicates.size} · " +
                        "Failed: ${result.failures.size}"

                } catch (error: Exception) {
                    importMessage = error.message
                        ?: "Audio import failed"
                } finally {
                    importProgress = null
                }
            }
        }
    }

    DisposableEffect(previewController) {
        previewController.onPlaybackStateChanged = { playing ->
            isPlaying = playing
        }

        onDispose {
            previewController.onPlaybackStateChanged = null
            previewController.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        AudioLibraryScreen(
            assets = assets,
            playingAssetId = playingAssetId,
            isPlaying = isPlaying,

            onImport = { type ->
                importType = type
                audioPicker.launch(arrayOf("audio/*"))
            },

            onPlay = { asset ->
                val success = previewController.play(asset)

                if (success) {
                    playingAssetId = asset.id
                } else {
                    importMessage = "Audio file not found"
                }
            },

            onPause = {
                previewController.pause()
            },

            onFavorite = { asset ->
                scope.launch {
                    dao.updateAsset(
                        asset.copy(
                            isFavorite = !asset.isFavorite
                        )
                    )
                }
            }
        )

        importProgress?.let { progress ->
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Importing ${progress.completed}/${progress.total}",
                    color = MaterialTheme.colorScheme.onSurface
                )

                LinearProgressIndicator(
                    progress = {
                        progress.percentage / 100f
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                )
            }
        }

        if (importMessage.isNotBlank()) {
            Text(
                text = importMessage,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
