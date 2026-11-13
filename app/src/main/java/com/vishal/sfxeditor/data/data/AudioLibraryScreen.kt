
package com.vishal.sfxeditor.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryScreen(
    assets: List<AudioAssetEntity>,
    categories: List<AudioCategoryEntity>,
    playingAssetId: String?,
    isPlaying: Boolean,
    onImport: (AudioType, String?) -> Unit,
    onPlay: (AudioAssetEntity) -> Unit,
    onPause: () -> Unit,
    onFavorite: (AudioAssetEntity) -> Unit,
    onRenameAsset: (AudioAssetEntity, String) -> Unit,
    onDeleteAsset: (AudioAssetEntity) -> Unit,
    onMoveAsset: (AudioAssetEntity, String?) -> Unit,
    onAddCategory: (String, String, String?) -> Unit,
    onRenameCategory: (AudioCategoryEntity, String) -> Unit,
    onDeleteCategory: (AudioCategoryEntity) -> Unit,
    selectionMode: Boolean = false,
    initialCategoryId: String? = null,
    onSelectAsset: (AudioAssetEntity) -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(AudioType.SFX) }
    var search by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedCategoryId by remember(initialCategoryId) {
        mutableStateOf(initialCategoryId)
    }

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var actionMenuExpanded by remember { mutableStateOf(false) }
    var parentMenuExpanded by remember { mutableStateOf(false) }

    var dialogMode by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf("") }
    var parentCategoryId by remember { mutableStateOf<String?>(null) }

    var activeAsset by remember { mutableStateOf<AudioAssetEntity?>(null) }
    var assetMenuExpanded by remember { mutableStateOf(false) }
    var assetName by remember { mutableStateOf("") }
    var moveCategoryId by remember { mutableStateOf<String?>(null) }

    val typeCategories = categories.filter {
        it.assetType == selectedType.name
    }

    val selectedCategory = typeCategories.firstOrNull {
        it.id == selectedCategoryId
    }

    // Include the selected folder and every nested subfolder.
    val visibleCategoryIds = remember(selectedCategoryId, typeCategories) {
        if (selectedCategoryId == null) {
            emptySet()
        } else {
            val found = linkedSetOf(selectedCategoryId!!)
            var changed = true
            while (changed) {
                changed = false
                typeCategories.forEach { category ->
                    if (category.parentId in found && found.add(category.id)) {
                        changed = true
                    }
                }
            }
            found
        }
    }

    val filteredAssets = assets.filter { asset ->
        asset.assetType == selectedType.name &&
            (selectedCategoryId == null ||
                asset.categoryId in visibleCategoryIds) &&
            asset.fileName.contains(search, ignoreCase = true) &&
            (!favoritesOnly || asset.isFavorite)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            if (selectionMode) "REPLACE AUDIO FROM LIBRARY" else "AUDIO LIBRARY",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (selectionMode) {
            Text(
                "Choose an audio file. Nested subfolders are included in the selected folder.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedType == AudioType.SFX,
                onClick = {
                    selectedType = AudioType.SFX
                    selectedCategoryId = null
                    favoritesOnly = false
                },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text("SFX")
            }

            SegmentedButton(
                selected = selectedType == AudioType.MUSIC,
                onClick = {
                    selectedType = AudioType.MUSIC
                    selectedCategoryId = null
                    favoritesOnly = false
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text("Music")
            }
        }

        Spacer(Modifier.height(10.dp))

        ExposedDropdownMenuBox(
            expanded = categoryMenuExpanded,
            onExpandedChange = {
                categoryMenuExpanded = !categoryMenuExpanded
            }
        ) {
            OutlinedTextField(
                value = selectedCategory?.let {
                    categoryLabel(it, typeCategories)
                } ?: "All categories",
                onValueChange = {},
                readOnly = true,
                label = { Text("Browse categories") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = categoryMenuExpanded
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = categoryMenuExpanded,
                onDismissRequest = {
                    categoryMenuExpanded = false
                }
            ) {
                DropdownMenuItem(
                    text = { Text("All categories") },
                    onClick = {
                        selectedCategoryId = null
                        categoryMenuExpanded = false
                    }
                )

                typeCategories
                    .sortedWith(
                        compareBy<AudioCategoryEntity> {
                            it.parentId != null
                        }.thenBy {
                            it.sortOrder
                        }.thenBy {
                            it.name.lowercase()
                        }
                    )
                    .forEach { category ->
                        DropdownMenuItem(
                            text = {
                                Text(categoryLabel(category, typeCategories))
                            },
                            onClick = {
                                selectedCategoryId = category.id
                                categoryMenuExpanded = false
                            }
                        )
                    }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                OutlinedButton(
                    onClick = { actionMenuExpanded = true }
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Text("Manage")
                }

                DropdownMenu(
                    expanded = actionMenuExpanded,
                    onDismissRequest = {
                        actionMenuExpanded = false
                    }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add category") },
                        onClick = {
                            categoryName = ""
                            parentCategoryId = null
                            dialogMode = "add"
                            actionMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Add subcategory") },
                        enabled = selectedCategoryId != null,
                        onClick = {
                            categoryName = ""
                            parentCategoryId = selectedCategoryId
                            dialogMode = "add"
                            actionMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Rename selected") },
                        enabled = selectedCategory != null,
                        onClick = {
                            categoryName = selectedCategory?.name.orEmpty()
                            dialogMode = "rename"
                            actionMenuExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Delete selected") },
                        enabled = selectedCategory != null,
                        onClick = {
                            dialogMode = "delete"
                            actionMenuExpanded = false
                        }
                    )
                }
            }

            FilterChip(
                selected = favoritesOnly,
                onClick = {
                    favoritesOnly = !favoritesOnly
                },
                label = { Text("Favorites") },
                leadingIcon = {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                }
            )

            Spacer(Modifier.weight(1f))

            Text(
                "${filteredAssets.size} files",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search audio files") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { search = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }
            }
        )

        Button(
            onClick = {
                onImport(selectedType, selectedCategoryId)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))

            Text(
                if (selectedType == AudioType.SFX) {
                    "Import SFX Audio"
                } else {
                    "Import Music"
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        if (filteredAssets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (search.isNotBlank()) {
                        "No matching audio files"
                    } else if (selectedCategoryId != null) {
                        "This category is empty"
                    } else {
                        "No audio files imported yet"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredAssets, key = { it.id }) { asset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF182A32)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AudioFile,
                                contentDescription = null,
                                tint = Color(0xFF69E7B9)
                            )

                            Spacer(Modifier.width(10.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    asset.fileName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "${formatDuration(asset.durationMs)} · " +
                                        formatSize(asset.sizeBytes),
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                val categoryNameForAsset =
                                    typeCategories.firstOrNull {
                                        it.id == asset.categoryId
                                    }?.name

                                if (categoryNameForAsset != null) {
                                    Text(
                                        categoryNameForAsset,
                                        color = Color(0xFF80CFFF),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Text(
                                        "Uncategorized",
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }

                                if (asset.missingOnDisk) {
                                    Text(
                                        "File missing",
                                        color = Color(0xFFFFC46B)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onFavorite(asset) }
                            ) {
                                Icon(
                                    if (asset.isFavorite) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    },
                                    contentDescription = "Favorite",
                                    tint = Color(0xFF69E7B9)
                                )
                            }

                            if (selectionMode) {
                                TextButton(
                                    onClick = { onSelectAsset(asset) },
                                    enabled = !asset.missingOnDisk
                                ) {
                                    Text("Select")
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (
                                        playingAssetId == asset.id &&
                                        isPlaying
                                    ) {
                                        onPause()
                                    } else {
                                        onPlay(asset)
                                    }
                                },
                                enabled = !asset.missingOnDisk
                            ) {
                                Icon(
                                    if (
                                        playingAssetId == asset.id &&
                                        isPlaying
                                    ) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = "Play audio",
                                    tint = Color.White
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = {
                                        activeAsset = asset
                                        assetMenuExpanded = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Audio options",
                                        tint = Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = assetMenuExpanded &&
                                        activeAsset?.id == asset.id,
                                    onDismissRequest = {
                                        assetMenuExpanded = false
                                    }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            assetName = asset.fileName
                                            assetMenuExpanded = false
                                            dialogMode = "renameAsset"
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Move to category") },
                                        onClick = {
                                            moveCategoryId = asset.categoryId
                                            assetMenuExpanded = false
                                            dialogMode = "moveAsset"
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Delete audio") },
                                        onClick = {
                                            assetMenuExpanded = false
                                            dialogMode = "deleteAsset"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialogMode == "renameAsset" && activeAsset != null) {
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = { Text("Rename audio file") },
            text = {
                OutlinedTextField(
                    value = assetName,
                    onValueChange = { assetName = it },
                    singleLine = true,
                    label = { Text("File name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = assetName.isNotBlank(),
                    onClick = {
                        activeAsset?.let {
                            onRenameAsset(it, assetName.trim())
                        }
                        dialogMode = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (dialogMode == "moveAsset" && activeAsset != null) {
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = { Text("Move audio to category") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = { moveCategoryId = null }
                    ) {
                        Text(
                            if (moveCategoryId == null) {
                                "✓ Uncategorized"
                            } else {
                                "Uncategorized"
                            }
                        )
                    }

                    typeCategories.forEach { category ->
                        TextButton(
                            onClick = {
                                moveCategoryId = category.id
                            }
                        ) {
                            Text(
                                (if (moveCategoryId == category.id) "✓ " else "") +
                                    categoryLabel(category, typeCategories)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeAsset?.let {
                            onMoveAsset(it, moveCategoryId)
                        }
                        dialogMode = null
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (dialogMode == "deleteAsset" && activeAsset != null) {
        AlertDialog(
            onDismissRequest = { dialogMode = null },
            title = { Text("Delete audio file?") },
            text = {
                Text(
                    "This permanently deletes the imported copy of " +
                        activeAsset!!.fileName +
                        " from this app. Your original source file is not affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeAsset?.let { onDeleteAsset(it) }
                        dialogMode = null
                        activeAsset = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (dialogMode == "add" || dialogMode == "rename") {
        AlertDialog(
            onDismissRequest = {
                dialogMode = null
            },
            title = {
                Text(
                    if (dialogMode == "add") {
                        "Create category"
                    } else {
                        "Rename category"
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {
                            categoryName = it
                        },
                        singleLine = true,
                        label = { Text("Category name") }
                    )

                    if (dialogMode == "add") {
                        Box {
                            OutlinedButton(
                                onClick = {
                                    parentMenuExpanded = true
                                }
                            ) {
                                Text(
                                    parentCategoryId?.let { id ->
                                        typeCategories.firstOrNull {
                                            it.id == id
                                        }?.name
                                    } ?: "No parent (top-level)"
                                )
                            }

                            DropdownMenu(
                                expanded = parentMenuExpanded,
                                onDismissRequest = {
                                    parentMenuExpanded = false
                                }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text("No parent (top-level)")
                                    },
                                    onClick = {
                                        parentCategoryId = null
                                        parentMenuExpanded = false
                                    }
                                )

                                typeCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(categoryLabel(category, typeCategories))
                                        },
                                        onClick = {
                                            parentCategoryId = category.id
                                            parentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = categoryName.isNotBlank(),
                    onClick = {
                        if (dialogMode == "add") {
                            onAddCategory(
                                selectedType.name,
                                categoryName.trim(),
                                parentCategoryId
                            )
                        } else {
                            selectedCategory?.let {
                                onRenameCategory(it, categoryName.trim())
                            }
                        }

                        dialogMode = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (dialogMode == "delete") {
        AlertDialog(
            onDismissRequest = {
                dialogMode = null
            },
            title = {
                Text("Delete category?")
            },
            text = {
                Text(
                    "Assets in this category will move to its parent " +
                        "(or All categories). Its subcategories will also " +
                        "be moved up one level."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedCategory?.let {
                            onDeleteCategory(it)
                        }

                        selectedCategoryId = null
                        dialogMode = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun categoryLabel(
    category: AudioCategoryEntity,
    categories: List<AudioCategoryEntity>
): String {
    val parent = categories.firstOrNull {
        it.id == category.parentId
    }

    return if (parent == null) {
        category.name
    } else {
        "  ↳ ${category.name} · ${parent.name}"
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000

    return String.format(
        Locale.getDefault(),
        "%02d:%02d",
        totalSeconds / 60,
        totalSeconds % 60
    )
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f MB",
                bytes / 1048576.0
            )

        bytes >= 1024L ->
            String.format(
                Locale.getDefault(),
                "%.1f KB",
                bytes / 1024.0
            )

        else -> "$bytes B"
    }
}
