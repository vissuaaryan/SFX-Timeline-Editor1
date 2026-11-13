
package com.vishal.sfxeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.vishal.sfxeditor.data.AudioLibraryRoute
import com.vishal.sfxeditor.data.LibraryDatabase
import com.vishal.sfxeditor.data.AudioAssetEntity
import com.vishal.sfxeditor.data.AudioCategoryEntity
import com.vishal.sfxeditor.data.AudioPreviewController
import com.vishal.sfxeditor.data.AutoAssetResolver
import com.vishal.sfxeditor.data.LibraryManifestBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

private val PinkPrimary = Color(0xFFFF70B7)
private val PinkSoft = Color(0xFFFFA6D1)
private val PinkLight = Color(0xFFFFD6E9)
private val Lavender = Color(0xFFC9A7FF)
private val Background = Color(0xFF100A13)
private val SurfaceDark = Color(0xFF1B101D)
private val CardDark = Color(0xFF2B192A)
private val CardDeep = Color(0xFF301B2D)
private val TextMain = Color(0xFFFFF1F7)
private val TextMuted = Color(0xFFE7C8D9)
private val WarningPink = Color(0xFFFFC1D9)

fun displayName(
    ctx: android.content.Context,
    u: Uri
): String {
    return ctx.contentResolver.query(
        u,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use {
        if (it.moveToFirst()) it.getString(0)
        else u.lastPathSegment ?: "file"
    } ?: (u.lastPathSegment ?: "file")
}


private fun isAssetUriAvailable(
    context: android.content.Context,
    uri: Uri?
): Boolean {
    if (uri == null) return false
    return try {
        when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).isFile } == true
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?.use { true } ?: false
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SfxApp()
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SfxApp() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val appScope = rememberCoroutineScope()

    var page by remember { mutableStateOf("home") }
    var logicutCode by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    var raw by remember { mutableStateOf("") }
    var savedProjects by remember {
        mutableStateOf(ProjectLibrary.load(ctx))
    }

    var activeSavedId by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<SavedProject?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var video by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("No video selected") }

    var project by remember { mutableStateOf<SfxProject?>(null) }
    var assets by remember { mutableStateOf<List<AssetSlot>>(emptyList()) }
    var events by remember { mutableStateOf<List<AudioEvent>>(emptyList()) }
    val recoveryManager = remember(ctx) { ProjectRecoveryManager(ctx) }
    var recoveryCandidate by remember(ctx) {
        mutableStateOf(recoveryManager.getCheckpoint())
    }

    var pending by remember { mutableStateOf<String?>(null) }
    var replaceTargetAssetId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Ready") }

    var exporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    fun saveCurrentProject() {
        val p = project

        if (p == null) {
            status = "Import and validate a project first"
            return
        }

        val id = activeSavedId ?: java.util.UUID.randomUUID().toString()

        val item = SavedProject(
            id,
            p.name,
            System.currentTimeMillis(),
            video?.toString() ?: "",
            videoName,
            p.copy(assets = assets, events = events),
            assets,
            events
        )

        ProjectLibrary.upsert(ctx, item)
        recoveryManager.saveCheckpoint(item)
        activeSavedId = id
        savedProjects = ProjectLibrary.load(ctx)
        status = "Project saved to Projects"
    }

    fun openSavedProject(item: SavedProject) {
        project = item.project
        assets = item.assets
        events = item.events

        video = item.videoUri
            .takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }

        videoName = item.videoName
        activeSavedId = item.id
        page = "timeline"
        status = "Project loaded. Checking missing audio links..."

        // Restore private-library file URIs after reinstall, restore, or a moved
        // library file. Prefer the stable library ID; use exact filename + type
        // only for older projects that do not have a stored library ID.
        appScope.launch {
            val dao = LibraryDatabase.getDatabase(ctx).libraryDao()
            val allLibraryAssets = runCatching { dao.getAllAssets().first() }
                .getOrDefault(emptyList())
            val audioRoot = runCatching {
                File(ctx.filesDir, "audio").canonicalFile
            }.getOrNull()

            val repaired = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                item.assets.map { slot ->
                    if (isAssetUriAvailable(ctx, slot.uri)) {
                        slot
                    } else {
                        val expectedType = if (slot.type.contains("music", true)) "MUSIC" else "SFX"
                        val libraryAsset = slot.libraryAssetId
                            ?.let { id -> runCatching { dao.getAssetById(id) }.getOrNull() }
                            ?: allLibraryAssets.firstOrNull {
                                it.assetType.equals(expectedType, true) &&
                                    it.fileName.equals(slot.filename, true)
                            }

                        val candidate = libraryAsset?.let {
                            runCatching {
                                File(ctx.filesDir, it.relativePath).canonicalFile
                            }.getOrNull()
                        }

                        if (
                            libraryAsset != null &&
                            candidate != null &&
                            audioRoot != null &&
                            candidate.path.startsWith(audioRoot.path + File.separator) &&
                            candidate.isFile
                        ) {
                            slot.copy(
                                uri = Uri.fromFile(candidate),
                                libraryAssetId = libraryAsset.id,
                                libraryCategoryId = libraryAsset.categoryId
                            )
                        } else {
                            slot.copy(uri = null)
                        }
                    }
                }
            }

            assets = repaired
            project = project?.copy(assets = repaired)
            val restoredCount = repaired.count { isAssetUriAvailable(ctx, it.uri) }
            val missingCount = repaired.size - restoredCount
            status = if (missingCount == 0) {
                "Project loaded. All audio links are available."
            } else {
                "$restoredCount audio links restored · $missingCount still missing. Use Relink from Library."
            }
        }
    }

    fun persist(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    // Debounced autosave + recovery checkpoint. Any project/timeline/media edit
    // schedules a fresh snapshot; rapid edits are coalesced into one write.
    LaunchedEffect(project, assets, events, video, videoName, activeSavedId) {
        val currentProject = project
        if (currentProject != null) {
            kotlinx.coroutines.delay(1_500L)
            val id = activeSavedId ?: java.util.UUID.randomUUID().toString()
            val snapshot = SavedProject(
                id = id,
                name = currentProject.name,
                savedAt = System.currentTimeMillis(),
                videoUri = video?.toString() ?: "",
                videoName = videoName,
                project = currentProject.copy(assets = assets, events = events),
                assets = assets,
                events = events
            )
            ProjectLibrary.upsert(ctx, snapshot)
            recoveryManager.saveCheckpoint(snapshot)
            if (activeSavedId == null) activeSavedId = id
            savedProjects = ProjectLibrary.load(ctx)
            status = "Autosaved"
        }
    }

    val videoPick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { u ->
        if (u != null) {
            video = u
            persist(u)
            videoName = displayName(ctx, u)
            status = "Video imported"
        }
    }

    val jsonPick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { u ->
        if (u != null) {
            try {
                ctx.contentResolver.openInputStream(u)
                    ?.bufferedReader()
                    ?.use { raw = it.readText() }

                persist(u)
                status = "JSON loaded"
            } catch (e: Exception) {
                status = "JSON read failed: ${e.message}"
            }
        }
    }

    val audioPick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { u ->
        if (u != null && pending != null) {
            persist(u)

            assets = assets.map {
                if (it.id == pending) it.copy(uri = u) else it
            }

            pending = null
            status = "Audio asset assigned"
        }
    }

    val bulkPick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { list ->
        if (list.isNotEmpty()) {
            list.forEach { persist(it) }

            val pool = list.toMutableList()

            assets = assets.map { a ->
                if (a.uri != null) {
                    a
                } else {
                    val exact = pool.indexOfFirst {
                        displayName(ctx, it).equals(a.filename, true)
                    }

                    val byId = if (exact < 0) {
                        pool.indexOfFirst {
                            displayName(ctx, it).startsWith(a.id, true)
                        }
                    } else {
                        exact
                    }

                    if (byId >= 0) {
                        a.copy(uri = pool.removeAt(byId))
                    } else {
                        a
                    }
                }
            }

            status = "Bulk matching finished. Review unmatched assets."
        }
    }

    val exportPick = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/mp4")
    ) { dest ->
        val p = project
        val v = video

        if (dest != null && p != null && v != null) {
            exporting = true
            progress = 0f

            TimelineExporter(ctx).export(
                p.copy(assets = assets, events = events),
                v,
                object : TimelineExporter.Callback {

                    override fun onProgress(percent: Int) {
                        (ctx as? ComponentActivity)?.runOnUiThread {
                            progress = percent / 100f
                        }
                    }

                    override fun onComplete(file: File) {
                        Thread {
                            try {
                                ctx.contentResolver.openOutputStream(dest)?.use { output ->
                                    FileInputStream(file).use { input ->
                                        input.copyTo(output)
                                    }
                                }

                                (ctx as? ComponentActivity)?.runOnUiThread {
                                    exporting = false
                                    status = "MP4 export complete"
                                }
                            } catch (e: Exception) {
                                (ctx as? ComponentActivity)?.runOnUiThread {
                                    exporting = false
                                    status = "Save failed: ${e.message}"
                                }
                            }
                        }.start()
                    }

                    override fun onError(message: String) {
                        (ctx as? ComponentActivity)?.runOnUiThread {
                            exporting = false
                            status = "Export failed: $message"
                        }
                    }
                }
            )
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PinkPrimary,
            onPrimary = Color(0xFF260D1B),
            secondary = PinkSoft,
            onSecondary = Color(0xFF321323),
            tertiary = Lavender,
            background = Background,
            onBackground = TextMain,
            surface = SurfaceDark,
            onSurface = TextMain,
            surfaceVariant = CardDeep,
            onSurfaceVariant = TextMuted,
            outline = Color(0xFF80536F)
        )
    ) {
        Scaffold(
            containerColor = Background,

            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "SFX TIMELINE EDITOR",
                                fontWeight = FontWeight.Bold,
                                color = TextMain
                            )

                            Text(
                                "AI-assisted cinematic sound design",
                                fontSize = 11.sp,
                                color = PinkSoft
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceDark,
                        titleContentColor = TextMain
                    )
                )
            },

            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = PinkSoft
                ) {
                    listOf(
                        "home" to "Home",
                        "get" to "Get Code",
                        "import" to "I Have Code",
                        "timeline" to "Timeline",
                        "library" to "Library",
                        "projects" to "Projects"
                    ).forEach { (key, label) ->

                        NavigationBarItem(
                            selected = page == key,
                            onClick = { page = key },

                            icon = {
                                val navIcon = when (key) {
                                    "home" -> Icons.Default.Home
                                    "get" -> Icons.Default.AutoAwesome
                                    "import" -> Icons.Default.Code
                                    "library" -> Icons.Default.LibraryMusic
                                    "projects" -> Icons.Default.Folder
                                    else -> Icons.Default.GraphicEq
                                }

                                Icon(
                                    imageVector = navIcon,
                                    contentDescription = label
                                )
                            },

                            label = {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (page == key) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            },

                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF260D1B),
                                selectedTextColor = PinkSoft,
                                indicatorColor = PinkPrimary,
                                unselectedIconColor = Color(0xFFB99AAA),
                                unselectedTextColor = Color(0xFFB99AAA)
                            )
                        )
                    }
                }
            },

            content = { paddingValues ->

                when (page) {

                    "home" -> {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            item {
                                Text(
                                    "Your sound design workspace",
                                    fontSize = 24.sp,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Script → AI JSON → Assets → Timeline → MP4",
                                    color = TextMuted
                                )
                            }

                            item {
                                HomeTile(
                                    "GET CODE",
                                    "Create a strict AI video-analysis prompt"
                                ) {
                                    page = "get"
                                }
                            }

                            item {
                                HomeTile(
                                    "I HAVE CODE",
                                    "Import JSON and your original video"
                                ) {
                                    page = "import"
                                }
                            }

                            item {
                                HomeTile(
                                    "TIMELINE & EXPORT",
                                    "Review audio events and render final MP4"
                                ) {
                                    page = "timeline"
                                }
                            }

                            item {
                                HomeTile(
                                    "AUDIO LIBRARY",
                                    "Manage your offline SFX and music collection"
                                ) {
                                    page = "library"
                                }
                            }

                            item {
                                HomeTile(
                                    "MY PROJECTS",
                                    "Open, edit, update or delete saved projects"
                                ) {
                                    savedProjects = ProjectLibrary.load(ctx)
                                    page = "projects"
                                }
                            }

                            if (project != null) {
                                item {
                                    Button(
                                        onClick = { saveCurrentProject() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = null
                                        )

                                        Text(
                                            if (activeSavedId == null) {
                                                "Save Project"
                                            } else {
                                                "Update Saved Project"
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    "${project?.name ?: "No project loaded"}\n" +
                                        "$videoName\n" +
                                        "${assets.count { it.uri != null }} / ${assets.size} assets assigned\n" +
                                        status,
                                    color = TextMuted
                                )
                            }
                        }
                    }

                    "projects" -> {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            item {
                                Text(
                                    "MY PROJECTS",
                                    fontSize = 24.sp,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Saved projects stay on this device. Open one to continue editing.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }

                            item {
                                Button(
                                    onClick = {
                                        savedProjects = ProjectLibrary.load(ctx)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null
                                    )
                                    Text("Refresh")
                                }
                            }

                            if (savedProjects.isEmpty()) {
                                item {
                                    Text(
                                        "No saved projects yet. Open a project and tap Save Project.",
                                        color = TextMuted
                                    )
                                }
                            }

                            items(
                                savedProjects,
                                key = { saved -> saved.id }
                            ) { saved ->

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = CardDark
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {

                                        Text(
                                            saved.name,
                                            color = TextMain,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp
                                        )

                                        Text(
                                            "${saved.assets.size} assets · ${saved.events.size} timeline events",
                                            color = TextMuted,
                                            fontSize = 12.sp
                                        )

                                        Text(
                                            "Video: ${saved.videoName}",
                                            color = PinkSoft,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            "Saved: ${
                                                java.text.DateFormat
                                                    .getDateTimeInstance()
                                                    .format(java.util.Date(saved.savedAt))
                                            }",
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { openSavedProject(saved) },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null)
                                                Text("Open / Edit")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    renameTarget = saved
                                                    renameValue = saved.name
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                                                Text("Rename")
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    val duplicate = ProjectLibrary.duplicate(ctx, saved.id)
                                                    savedProjects = ProjectLibrary.load(ctx)
                                                    status = if (duplicate != null) "Project duplicated" else "Could not duplicate project"
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                                Text("Duplicate")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    ProjectLibrary.delete(ctx, saved.id)
                                                    savedProjects = ProjectLibrary.load(ctx)
                                                    if (activeSavedId == saved.id) activeSavedId = null
                                                    status = "Project deleted"
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null)
                                                Text("Delete")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "get" -> {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            item {
                                Text(
                                    "GET CODE",
                                    fontSize = 24.sp,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Paste the complete LogiCut AI output: [ASSETS] and [RECIPE]. Then copy the generated prompt to your video-capable AI and attach the exported motion-graphics video.",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = logicutCode,
                                    onValueChange = {
                                        logicutCode = it
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 10,
                                    label = {
                                        Text("Paste LogiCut [ASSETS] + [RECIPE] code")
                                    }
                                )
                            }

                            item {
                                Text(
                                    "FIXED SOUND DESIGN PROFILE",
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Premium Cinematic · Hinglish",
                                    color = PinkPrimary,
                                    fontSize = 13.sp
                                )

                                Button(
                                    onClick = {
                                        appScope.launch {
                                            try {
                                                status = "Reading offline SFX & Music Library..."
                                                val dao = LibraryDatabase.getDatabase(ctx).libraryDao()
                                                val libraryAssets = dao.getAllAssets().first()
                                                val libraryCategories = dao.getAllCategories().first()
                                                val manifest = LibraryManifestBuilder.build(
                                                    assets = libraryAssets,
                                                    categories = libraryCategories
                                                ).toString(2)
                                                prompt = MasterPrompt.build(
                                                    logicutCode = logicutCode,
                                                    libraryManifest = manifest
                                                )
                                                status = "Prompt generated with ${libraryAssets.size} Library assets and ${libraryCategories.size} folders"
                                            } catch (error: Exception) {
                                                status = "Could not read Library: ${error.message}"
                                            }
                                        }
                                    },
                                    enabled = logicutCode.contains("[RECIPE]") &&
                                        logicutCode.contains("[ASSETS]"),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null
                                    )
                                    Text("Generate SFX Analysis Prompt")
                                }
                            }

                            if (prompt.isNotBlank()) {
                                item {
                                    OutlinedTextField(
                                        value = prompt,
                                        onValueChange = {},
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 9,
                                        readOnly = true
                                    )

                                    Button(
                                        onClick = {
                                            copyText(ctx, prompt)
                                            status = "Prompt copied"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                        Text("Copy Prompt")
                                    }
                                }
                            }
                        }
                    }

                    "import" -> {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            item {
                                Text(
                                    "I HAVE CODE",
                                    fontSize = 24.sp,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        videoPick.launch(arrayOf("video/*"))
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.VideoFile,
                                        contentDescription = null
                                    )
                                    Text("Select Original Video")
                                }

                                Text(
                                    videoName,
                                    color = PinkPrimary,
                                    fontSize = 12.sp
                                )

                                OutlinedButton(
                                    onClick = {
                                        jsonPick.launch(
                                            arrayOf(
                                                "application/json",
                                                "text/*",
                                                "*/*"
                                            )
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.FileOpen,
                                        contentDescription = null
                                    )
                                    Text("Import JSON File")
                                }

                                OutlinedTextField(
                                    value = raw,
                                    onValueChange = { raw = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 7,
                                    label = { Text("Or paste JSON") }
                                )

                                Button(
                                    onClick = {
                                        appScope.launch {
                                            try {
                                                status = "Checking offline SFX & Music Library..."
                                                val parsed = ProjectParser.parse(raw)
                                                val dao = LibraryDatabase.getDatabase(ctx).libraryDao()
                                                val resolution = AutoAssetResolver.resolve(
                                                    context = ctx,
                                                    slots = parsed.assets,
                                                    dao = dao
                                                )
                                                val resolvedProject = parsed.copy(assets = resolution.assets)

                                                project = resolvedProject
                                                assets = resolution.assets
                                                events = parsed.events
                                                activeSavedId = null
                                                page = "timeline"

                                                status = "Ready: ${resolution.matched} matched from Library, ${resolution.missing} missing. ${parsed.events.size} timeline events loaded."
                                            } catch (error: Exception) {
                                                status = "Validation/matching error: ${error.message}"
                                            }
                                        }
                                    },
                                    enabled = raw.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null
                                    )
                                    Text("Validate JSON & Create Slots")
                                }
                            }

                            if (assets.isNotEmpty()) {
                                item {
                                    Text(
                                        "ASSET MANAGER · ${assets.count { it.uri != null }}/${assets.size}",
                                        color = PinkPrimary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            bulkPick.launch(arrayOf("audio/*"))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.LibraryMusic,
                                            contentDescription = null
                                        )
                                        Text("Bulk Import Audio Files")
                                    }
                                }

                                items(assets) { asset ->
                                    AssetCard(asset) {
                                        pending = asset.id
                                        audioPick.launch(arrayOf("audio/*"))
                                    }
                                }
                            }

                            item {
                                Text(
                                    status,
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    "library" -> {
                        val replaceTarget = assets.firstOrNull {
                            it.id == replaceTargetAssetId
                        }
                        if (replaceTarget == null) {
                            AudioLibraryRoute()
                        } else {
                            AudioLibraryRoute(
                                selectionMode = true,
                                initialCategoryId = replaceTarget.libraryCategoryId,
                                onSelectAsset = { chosen ->
                                    val updatedAssets = assets.map { slot ->
                                        if (slot.id == replaceTarget.id) {
                                            slot.copy(
                                                filename = chosen.fileName,
                                                uri = Uri.fromFile(
                                                    File(ctx.filesDir, chosen.relativePath)
                                                ),
                                                libraryAssetId = chosen.id,
                                                libraryCategoryId = chosen.categoryId
                                            )
                                        } else {
                                            slot
                                        }
                                    }
                                    assets = updatedAssets
                                    project = project?.copy(assets = updatedAssets)
                                    replaceTargetAssetId = null
                                    page = "timeline"
                                    status = "Replaced ${replaceTarget.filename} with ${chosen.fileName}"
                                }
                            )
                        }
                    }

                    else -> {
                        TimelinePage(
                            Modifier.padding(paddingValues),
                            ctx,
                            project,
                            video,
                            videoName,
                            assets,
                            events,
                            { updatedEvents ->
                                events = updatedEvents
                            },
                            { updatedAssets ->
                                assets = updatedAssets
                                project = project?.copy(assets = updatedAssets)
                            },
                            { assetId ->
                                replaceTargetAssetId = assetId
                                page = "library"
                            },
                            {
                                videoPick.launch(arrayOf("video/*"))
                            },
                            {
                                bulkPick.launch(arrayOf("audio/*"))
                            },
                            { assetId ->
                                pending = assetId
                                audioPick.launch(arrayOf("audio/*"))
                            },
                            exporting,
                            progress,
                            {
                                exportPick.launch("SFX_Final_Mix.mp4")
                            },
                            activeSavedId,
                            {
                                saveCurrentProject()
                            },
                            status
                        )
                    }
                }
            }
        )
    }

    val recovery = recoveryCandidate
    if (recovery != null) {
        AlertDialog(
            onDismissRequest = { recoveryCandidate = null },
            title = { Text("Recover previous session?") },
            text = {
                Text(
                    "An autosaved recovery copy was found: ${recovery.name}. " +
                        "It contains ${recovery.assets.size} assets and ${recovery.events.size} timeline events. " +
                        "Would you like to restore it as a project?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val restored = recoveryManager.restoreAsProject("${recovery.project.name} (Recovered)")
                    if (restored != null) {
                        openSavedProject(restored)
                        recoveryManager.clearCheckpoint()
                        savedProjects = ProjectLibrary.load(ctx)
                        status = "Previous session recovered"
                    } else {
                        status = "Could not recover previous session"
                    }
                    recoveryCandidate = null
                }) { Text("Recover") }
            },
            dismissButton = {
                TextButton(onClick = { recoveryCandidate = null }) { Text("Later") }
            }
        )
    }

    val target = renameTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Project name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val success = ProjectLibrary.rename(ctx, target.id, renameValue)
                    savedProjects = ProjectLibrary.load(ctx)
                    status = if (success) "Project renamed" else "Enter a valid project name"
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HomeTile(
    title: String,
    desc: String,
    action: () -> Unit
) {
    Card(
        onClick = action,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = CardDark
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                title,
                color = PinkSoft,
                fontWeight = FontWeight.Bold
            )

            Text(
                desc,
                color = TextMuted,
                fontSize = 12.sp
            )

            Text(
                "OPEN →",
                color = PinkPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun copyText(
    ctx: android.content.Context,
    s: String
) {
    val clipboard = ctx.getSystemService(
        android.content.Context.CLIPBOARD_SERVICE
    ) as android.content.ClipboardManager

    clipboard.setPrimaryClip(
        android.content.ClipData.newPlainText(
            "SFX Master Prompt",
            s
        )
    )
}

@Composable
fun AssetCard(
    a: AssetSlot,
    onPick: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = CardDeep
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AudioFile,
                    contentDescription = null,
                    tint = PinkPrimary
                )

                Spacer(Modifier.width(8.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        a.filename,
                        color = TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "${a.id} · ${a.type}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Text(
                    if (isAssetUriAvailable(ctx, a.uri)) "ADDED" else "MISSING",
                    color = if (isAssetUriAvailable(ctx, a.uri)) {
                        PinkPrimary
                    } else {
                        WarningPink
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        copyText(ctx, a.filename)
                    }
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null
                    )
                    Text("Copy Name")
                }

                TextButton(
                    onClick = {
                        copyText(ctx, a.id)
                    }
                ) {
                    Text("Copy ID")
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onPick) {
                    Text(if (isAssetUriAvailable(ctx, a.uri)) "Replace" else "Relink")
                }
            }

            if (a.searchPhrase.isNotBlank()) {
                Text(
                    "Search: ${a.searchPhrase}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            if (a.sourceName.isNotBlank()) {
                Text(
                    "Source: ${a.sourceName}",
                    color = PinkSoft,
                    fontSize = 10.sp
                )
            }

            if (
                a.sourceUrl.startsWith("https://") ||
                a.sourceUrl.startsWith("http://")
            ) {
                TextButton(
                    onClick = {
                        try {
                            ctx.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(a.sourceUrl)
                                )
                            )
                        } catch (_: Exception) {
                            copyText(ctx, a.sourceUrl)
                        }
                    }
                ) {
                    Text("Open Source Link")
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TimelinePage(
    mod: Modifier,
    ctx: android.content.Context,
    project: SfxProject?,
    video: Uri?,
    videoName: String,
    assets: List<AssetSlot>,
    events: List<AudioEvent>,
    onEvents: (List<AudioEvent>) -> Unit,
    onAssets: (List<AssetSlot>) -> Unit,
    onReplaceAsset: (String) -> Unit,
    onVideo: () -> Unit,
    onBulk: () -> Unit,
    onPick: (String) -> Unit,
    exporting: Boolean,
    progress: Float,
    onExport: () -> Unit,
    activeSavedId: String?,
    onSaveProject: () -> Unit,
    status: String
) {
    val player = remember(video) {
        if (video == null) {
            null
        } else {
            ExoPlayer.Builder(ctx).build().apply {
                setMediaItem(MediaItem.fromUri(video))
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.release()
        }
    }

    LazyColumn(
        mod
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        item {
            Text(
                "TIMELINE & EXPORT",
                color = TextMain,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )

            if (player != null) {
                AndroidView(
                    factory = {
                        PlayerView(it).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = {
                        it.player = player
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                )
            } else {
                OutlinedButton(
                    onClick = onVideo,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Video")
                }
            }

            Text(
                videoName,
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        if (project == null) {
            item {
                Text(
                    "Import and validate JSON first.",
                    color = WarningPink
                )
            }
        } else {
            item {
                Text(
                    "${project.name} · ${project.durationMs} ms · ${project.fps} FPS",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                OutlinedButton(
                    onClick = onBulk,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bulk Match Audio Files")
                }

                Text(
                    "ASSETS",
                    color = PinkPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(assets) { asset ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssetCard(asset) { onPick(asset.id) }
                OutlinedButton(
                    onClick = { onReplaceAsset(asset.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isAssetUriAvailable(ctx, asset.uri)) {
                            "Replace from Library"
                        } else {
                            "Relink from Library"
                        }
                    )
                }
            }
        }

        if (project != null) {
            item {
                Text(
                    "TIMELINE EVENTS",
                    color = PinkPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        items(events) { e ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = CardDeep
                )
            ) {
                Column(Modifier.padding(12.dp)) {

                    Text(
                        "${e.id} · ${
                            assets.firstOrNull { it.id == e.assetId }?.filename
                                ?: e.assetId
                        }",
                        color = TextMain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Text(
                        "${e.startMs}ms → ${e.endMs}ms · Layer ${e.layer}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Text(
                        e.visualAction.ifBlank { e.reason },
                        color = PinkSoft,
                        fontSize = 10.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Volume",
                            color = TextMuted,
                            fontSize = 11.sp
                        )

                        Slider(
                            value = e.volume,
                            onValueChange = { v ->
                                onEvents(
                                    events.map {
                                        if (it.id == e.id) {
                                            it.copy(volume = v)
                                        } else {
                                            it
                                        }
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            "${(e.volume * 100).toInt()}%",
                            color = TextMain,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        if (project != null) {
            item {
                Button(
                    onClick = onSaveProject,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null
                    )

                    Text(
                        if (activeSavedId == null) {
                            "Save Project"
                        } else {
                            "Update Saved Project"
                        }
                    )
                }

                val missing = assets.count {
                    it.required && it.uri == null
                }

                Text(
                    "Required assets missing: $missing",
                    color = if (missing == 0) {
                        PinkPrimary
                    } else {
                        WarningPink
                    }
                )

                Button(
                    onClick = onExport,
                    enabled = video != null && missing == 0 && !exporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null
                    )

                    Text("Render Final MP4")
                }

                if (exporting) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text(
                status,
                color = TextMuted,
                fontSize = 11.sp
            )

            Text(
                "Renderer uses AndroidX Media3 Composition to mix the source video and synchronized audio sequences. Crossfades between tracks are not supported by Media3 Composition; fade envelopes on individual clips are applied.",
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

object MasterPrompt {

    fun build(
        logicutCode: String,
        libraryManifest: String = "{}"
    ): String = """

# ROLE

You are a senior cinematic motion-graphics sound designer, sound editor and audio asset researcher. Create a premium, high-impact sound design plan for the supplied LogiCut motion-graphics project.

# INPUT: LOGICUT CODE

The user is supplying the original LogiCut output below. It contains [ASSETS] and [RECIPE]. Parse both sections carefully. [ASSETS] describes visual source objects; [RECIPE] is the visual animation timeline. This is NOT an audio recipe.

${logicutCode.trim()}

# USER'S OFFLINE AUDIO LIBRARY — EXACT MATCHING RULES

The following manifest is the user's actual offline SFX and Music Library. It is authoritative for assets that already exist on the device:

${libraryManifest.trim()}

When designing the sound plan:
1. Inspect the manifest before inventing any new audio asset.
2. If an existing library asset is suitable, reuse its exact asset_id and exact filename. Do not rename it, change its extension, or create a near-duplicate filename.
3. Use the manifest's exact type (sfx or music), folder_path, and category when referring to an existing asset.
4. If no existing asset is suitable, create a new unique asset ID and a descriptive filename. Mark it as not present in the offline library by adding `"library_match": false` and do not pretend it is available locally.
5. For reused assets, add `"library_match": true` and `"library_asset_id": "<exact asset_id>"` to that asset object.
6. Keep the output's `assets` and `timeline` references internally consistent. Every timeline asset_id must match an asset object.
7. Do not claim that the app can download or fetch source_url. The app only performs offline matching against this manifest.

# USER SETTINGS

Sound direction: premium_cinematic
Output language: Hinglish
Target application: SFX Timeline Editor for Android.

# REQUIRED VIDEO INPUT

The user must also attach the final exported motion-graphics video. Use the LogiCut RECIPE to understand intended events and approximate timing, then inspect the rendered video to verify what actually appears and when. The rendered video is the final visual source of truth. Do not assume every recipe line is visible exactly as authored. If no video is attached or frame inspection is unavailable, state this clearly and lower timing confidence. Never pretend to have inspected frames.

# SOUND DESIGN — EVERY MEANINGFUL MOTION

Design sound for meaningful visual events, not merely scene changes. Inspect every object/image entrance, exit, pop, scale, slide, drop, slam, rotation and transformation; text character reveals, typing, counters, labels and title impacts; cards, panels, grids, icons, shapes, rings, lines and particles; camera push-ins, zooms, shakes, pans and scene transitions; visible real-world actions; reveals, pauses, emphasis beats and ending. Match sound character, direction, weight and duration to the movement. Use tasteful combinations such as tight transient + low impact + short tail when appropriate. Give major hero movements strong cinematic accents; give minor movements controlled micro-sounds. Do not put a loud impact on every element. Avoid cheap/cartoon sounds, generic repeated whooshes, harsh clicks, excessive bass and clutter. Preserve narration intelligibility and use intentional silence where stronger.

# BACKGROUND MUSIC — REQUIRED

Always include a suitable background music bed unless the user explicitly disables it. Select music based on topic, emotion, pacing and visual style. Recommend a real, currently discoverable track or a specific usable music search result. Include intro, transitions, loop/continuity plan and end fade. Keep music beneath narration and duck it during speech and important SFX. Do not invent track titles or URLs.

# REAL, FINDABLE AUDIO ASSETS — CRITICAL

Recommend assets that actually exist and can be found online. Search the web when available; prioritize established libraries and actual YouTube videos/pages. For each asset provide exact filename for the app slot, unique ID, type (sfx or music), category and concise description; a precise search_phrase; source_name and direct source_url to the actual asset page/video or search results page; suggested duration and a reason linking it to the visual event. Never fabricate a track, asset, page, URL, availability or download. If you cannot verify an exact asset, provide a clearly labelled search URL/query rather than pretending it is a direct download. Prefer downloadable results. License details are not needed in the output. Do not instruct the user to bypass access controls or download restrictions.

# TIMING AND JSON

Use milliseconds. Derive timestamps from actual video frames where possible, cross-check against LogiCut recipe. Include source_frame and timing_confidence (high/medium/low). Use actual FPS. Every event must fit video duration; duration_ms = end_ms - start_ms. Create separate events for each distinct sound layer. Use enough events to cover meaningful motion throughout, but avoid overdesign. Include fade_in_ms, fade_out_ms, volume 0..1, pan -1..1, layer, priority, ducking_enabled, visual_action and reason. Music should be a long-duration asset/event spanning the appropriate video section; if looping is needed, use clear repeated sections without audible gaps.

# MIX

Keep original voiceover/audio unless the user says otherwise. Background music must sit below voiceover. Set music ducking true. Prevent clipping; avoid claiming loudness was measured unless it actually was. Use smooth fades. A practical target may be around -14 LUFS integrated and -1 dBTP true peak, but mark these as targets, not measured facts.

# STRICT OUTPUT

Return ONLY one valid JSON object. No Markdown fences or explanation. Use this exact structure and include the extra asset fields shown:

{
  "schema_version": "1.1",
  "project": {
    "project_name": "Motion Graphics Sound Design",
    "video_filename": "",
    "video_duration_ms": 0,
    "fps": 0,
    "resolution": "",
    "language": "Hinglish",
    "sound_style": "premium_cinematic"
  },
  "analysis": {
    "video_analyzed": true,
    "script_analyzed": true,
    "logicut_recipe_analyzed": true,
    "frame_inspection_available": true,
    "timing_method": "frame_based",
    "limitations": []
  },
  "audio_settings": {
    "preserve_original_audio": true,
    "sample_rate": 48000,
    "master_volume": 1.0,
    "target_lufs": -14,
    "true_peak_db": -1
  },
  "assets": [
    {
      "id": "SFX_001",
      "filename": "SFX_001_DEEP_IMPACT.wav",
      "type": "sfx",
      "category": "impact",
      "description": "...",
      "required": true,
      "suggested_duration_ms": 900,
      "search_phrase": "...",
      "source_name": "...",
      "source_url": "https://...",
      "visual_reason": "...",
      "library_match": false,
      "library_asset_id": ""
    }
  ],
  "timeline": [
    {
      "event_id": "EVENT_001",
      "asset_id": "SFX_001",
      "start_ms": 500,
      "end_ms": 1400,
      "duration_ms": 900,
      "source_frame": 30,
      "timing_confidence": "high",
      "layer": 1,
      "volume": 0.8,
      "fade_in_ms": 20,
      "fade_out_ms": 250,
      "pan": 0.0,
      "priority": "high",
      "ducking_enabled": true,
      "visual_action": "Object scales in and lands",
      "reason": "..."
    }
  ],
  "mixing": {
    "music_ducking": true,
    "prevent_clipping": true,
    "normalization": "target_only_not_measured",
    "master_fade_out_ms": 1200
  },
  "validation": {
    "all_asset_references_valid": true,
    "timestamps_within_video": true,
    "duplicate_ids": false,
    "json_valid": true,
    "warnings": []
  }
}

# VALIDATION

Every timeline asset_id must exist. IDs unique. All assets must have unique, copyable filenames. Include at least one background music asset/event. Check event timing, durations, fades, volume and pan. No event beyond video duration. Do not claim frame accuracy if not inspected. Ensure source_url is a real URL; if only a search URL is available, label source_name/search_phrase accordingly. Analyse the attached video and supplied LogiCut code now.

    """.trimIndent()
}
