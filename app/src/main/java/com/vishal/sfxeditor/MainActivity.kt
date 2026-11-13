
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

@Composable
fun AudioLibraryScreen(
    assets: List<AudioAssetEntity>,
    playingAssetId: String?,
    isPlaying: Boolean,
    onImport: (AudioType) -> Unit,
    onPlay: (AudioAssetEntity) -> Unit,
    onPause: () -> Unit,
    onFavorite: (AudioAssetEntity) -> Unit
) {
    var selectedType by remember { mutableStateOf(AudioType.SFX) }
    var search by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }

    val filteredAssets = assets.filter { asset ->
        asset.assetType == selectedType.name &&
        asset.fileName.contains(search, ignoreCase = true) &&
        (!favoritesOnly || asset.isFavorite)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "AUDIO LIBRARY",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SegmentedButton(
                selected = selectedType == AudioType.SFX,
                onClick = { selectedType = AudioType.SFX },
                shape = SegmentedButtonDefaults.itemShape(0, 2)
            ) {
                Text("SFX")
            }

            SegmentedButton(
                selected = selectedType == AudioType.MUSIC,
                onClick = { selectedType = AudioType.MUSIC },
                shape = SegmentedButtonDefaults.itemShape(1, 2)
            ) {
                Text("Music")
            }
        }

        Spacer(Modifier.height(12.dp))

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favorites only") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null
                    )
                }
            )

            Spacer(Modifier.weight(1f))

            Text(
                "${filteredAssets.size} files",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        Button(
            onClick = { onImport(selectedType) },
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

        Spacer(Modifier.height(12.dp))

        if (filteredAssets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (search.isNotBlank()) {
                        "No matching audio files"
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

                            IconButton(
                                onClick = {
                                    if (playingAssetId == asset.id && isPlaying) {
                                        onPause()
                                    } else {
                                        onPlay(asset)
                                    }
                                },
                                enabled = !asset.missingOnDisk
                            ) {
                                Icon(
                                    if (playingAssetId == asset.id && isPlaying) {
                                        Icons.Default.Pause
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = "Play audio",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
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
            String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)

        bytes >= 1024L ->
            String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)

        else -> "$bytes B"
    }
}
