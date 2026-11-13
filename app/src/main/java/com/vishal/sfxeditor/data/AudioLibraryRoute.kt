
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AudioLibraryRoute() {
    val context = LocalContext.current

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
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val categories by dao.getAllCategories()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val scope = rememberCoroutineScope()

    var selectedImportType by remember {
        mutableStateOf(AudioType.SFX)
    }

    var selectedImportCategoryId by remember {
        mutableStateOf<String?>(null)
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

    // Create default categories only when a type has no categories.
    LaunchedEffect(dao) {
        seedDefaultCategories(dao, AudioType.SFX)
        seedDefaultCategories(dao, AudioType.MUSIC)
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
                        categoryId = selectedImportCategoryId,
                        onProgress = { progress ->
                            importProgress =
                                progress.percentage / 100f

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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AudioLibraryScreen(
            assets = assets,
            categories = categories,
            playingAssetId = playingAssetId,
            isPlaying = isPlaying,

            onImport = { type, categoryId ->
                selectedImportType = type
                selectedImportCategoryId = categoryId
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
            },

            onAddCategory = { type, name, parentId ->
                scope.launch {
                    try {
                        val allCategories = dao.getAllCategories().first()

                        val validParent = parentId?.let { id ->
                            allCategories.firstOrNull {
                                it.id == id && it.assetType == type
                            }
                        }

                        val nextOrder = allCategories
                            .filter {
                                it.assetType == type &&
                                    it.parentId == validParent?.id
                            }
                            .maxOfOrNull { it.sortOrder }?.plus(1) ?: 0

                        dao.insertCategory(
                            AudioCategoryEntity(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                assetType = type,
                                parentId = validParent?.id,
                                sortOrder = nextOrder,
                                isCustom = true,
                                createdAt = System.currentTimeMillis()
                            )
                        )

                        importStatus = "Category created"
                    } catch (error: Exception) {
                        importStatus =
                            "Could not create category: ${error.message}"
                    }
                }
            },

            onRenameCategory = { category, newName ->
                scope.launch {
                    try {
                        dao.updateCategory(
                            category.copy(name = newName)
                        )
                        importStatus = "Category renamed"
                    } catch (error: Exception) {
                        importStatus =
                            "Rename failed: ${error.message}"
                    }
                }
            },

            onDeleteCategory = { category ->
                scope.launch {
                    try {
                        val allAssets = dao.getAllAssets().first()
                        val allCategories = dao.getAllCategories().first()

                        // Move direct assets to the deleted category's parent.
                        allAssets
                            .filter { it.categoryId == category.id }
                            .forEach { asset ->
                                dao.updateAsset(
                                    asset.copy(
                                        categoryId = category.parentId
                                    )
                                )
                            }

                        // Move child categories up one level.
                        allCategories
                            .filter { it.parentId == category.id }
                            .forEach { child ->
                                dao.updateCategory(
                                    child.copy(
                                        parentId = category.parentId
                                    )
                                )
                            }

                        dao.deleteCategory(category)
                        importStatus = "Category deleted"
                    } catch (error: Exception) {
                        importStatus =
                            "Delete failed: ${error.message}"
                    }
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

private suspend fun seedDefaultCategories(
    dao: LibraryDao,
    type: AudioType
) {
    val existing = dao.getCategoriesByType(type.name).first()

    if (existing.isNotEmpty()) return

    val now = System.currentTimeMillis()

    suspend fun addRoot(
        id: String,
        name: String,
        children: List<Pair<String, String>> = emptyList()
    ) {
        dao.insertCategory(
            AudioCategoryEntity(
                id = id,
                name = name,
                assetType = type.name,
                parentId = null,
                sortOrder = 0,
                isCustom = false,
                createdAt = now
            )
        )

        children.forEachIndexed { index, child ->
            dao.insertCategory(
                AudioCategoryEntity(
                    id = child.first,
                    name = child.second,
                    assetType = type.name,
                    parentId = id,
                    sortOrder = index,
                    isCustom = false,
                    createdAt = now
                )
            )
        }
    }

    if (type == AudioType.SFX) {
        addRoot(
            "default_sfx_cinematic",
            "Cinematic",
            listOf(
                "default_sfx_deep_impact" to "Deep Impact",
                "default_sfx_heavy_impact" to "Heavy Impact",
                "default_sfx_metallic_impact" to "Metallic Impact",
                "default_sfx_sub_impact" to "Sub Impact"
            )
        )

        addRoot(
            "default_sfx_whoosh",
            "Whoosh",
            listOf(
                "default_sfx_fast_whoosh" to "Fast",
                "default_sfx_soft_whoosh" to "Soft",
                "default_sfx_reverse_whoosh" to "Reverse"
            )
        )

        addRoot("default_sfx_transitions", "Transitions")
        addRoot("default_sfx_ui", "UI Sounds")
        addRoot("default_sfx_ambience", "Ambience")
        addRoot("default_sfx_foley", "Foley")
        addRoot("default_sfx_risers", "Risers")

    } else {
        addRoot(
            "default_music_cinematic",
            "Cinematic",
            listOf(
                "default_music_epic" to "Epic",
                "default_music_dark" to "Dark",
                "default_music_emotional" to "Emotional",
                "default_music_suspense" to "Suspense"
            )
        )

        addRoot("default_music_documentary", "Documentary")
        addRoot("default_music_corporate", "Corporate")
        addRoot("default_music_ambient", "Ambient")
        addRoot("default_music_background", "Background")
    }
}
