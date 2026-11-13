


package com.vishal.sfxeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
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
import java.io.File
import java.io.FileInputStream

fun displayName(ctx:android.content.Context,u:Uri):String=ctx.contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else u.lastPathSegment?:"file"}?:u.lastPathSegment?:"file"

class MainActivity:ComponentActivity(){ override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{SfxApp()}} }

@kotlin.OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable fun SfxApp(){
 val ctx=androidx.compose.ui.platform.LocalContext.current
 var page by remember{mutableStateOf("home")};var logicutCode by remember{mutableStateOf("")};var style by remember{mutableStateOf("premium_cinematic")};var lang by remember{mutableStateOf("Hindi")};var prompt by remember{mutableStateOf("")}
 var raw by remember{mutableStateOf("")};var savedProjects by remember{mutableStateOf(ProjectLibrary.load(ctx))};var activeSavedId by remember{mutableStateOf<String?>(null)};var video by remember{mutableStateOf<Uri?>(null)};var videoName by remember{mutableStateOf("No video selected")};var project by remember{mutableStateOf<SfxProject?>(null)};var assets by remember{mutableStateOf(listOf<AssetSlot>())};var events by remember{mutableStateOf(listOf<AudioEvent>())};var pending by remember{mutableStateOf<String?>(null)};var status by remember{mutableStateOf("Ready")};var exporting by remember{mutableStateOf(false)};var progress by remember{mutableFloatStateOf(0f)}
 fun saveCurrentProject(){
  val p=project
  if(p==null){status="Import and validate a project first";return}
  val id=activeSavedId ?: java.util.UUID.randomUUID().toString()
  val item=SavedProject(id,p.name,System.currentTimeMillis(),video?.toString()?:"",videoName,p.copy(assets=assets,events=events),assets,events)
  ProjectLibrary.upsert(ctx,item);activeSavedId=id;savedProjects=ProjectLibrary.load(ctx);status="Project saved to Projects"
 }
 fun openSavedProject(item:SavedProject){
  project=item.project;assets=item.assets;events=item.events;video=item.videoUri.takeIf{it.isNotBlank()}?.let{Uri.parse(it)};videoName=item.videoName;activeSavedId=item.id;page="timeline";status="Project loaded. Edit and tap Save Project to update it."
 }
 fun persist(uri:Uri){try{ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){}}
 val videoPick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null){video=u;persist(u);videoName=displayName(ctx,u);status="Video imported"}}
 val jsonPick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null)try{ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use{raw=it.readText()};persist(u);status="JSON loaded"}catch(e:Exception){status="JSON read failed: ${e.message}"}}
 val audioPick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u->if(u!=null&&pending!=null){persist(u);assets=assets.map{if(it.id==pending)it.copy(uri=u)else it};pending=null;status="Audio asset assigned"}}
 val bulkPick=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){list->if(list.isNotEmpty()){list.forEach{persist(it)};val pool=list.toMutableList();assets=assets.map{a->if(a.uri!=null)a else {val exact=pool.indexOfFirst{displayName(ctx,it).equals(a.filename,true)};val byId=if(exact<0)pool.indexOfFirst{displayName(ctx,it).startsWith(a.id,true)}else exact;if(byId>=0)a.copy(uri=pool.removeAt(byId))else a}};status="Bulk matching finished. Review unmatched assets."}}
 val exportPick=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")){dest->val p=project;val v=video;if(dest!=null&&p!=null&&v!=null){exporting=true;progress=0f;TimelineExporter(ctx).export(p.copy(assets=assets,events=events),v,object:TimelineExporter.Callback{override fun onProgress(percent:Int){(ctx as? ComponentActivity)?.runOnUiThread{progress=percent/100f}};override fun onComplete(file:File){Thread{try{ctx.contentResolver.openOutputStream(dest)?.use{o->FileInputStream(file).use{it.copyTo(o)}};(ctx as? ComponentActivity)?.runOnUiThread{exporting=false;status="MP4 export complete"}}catch(e:Exception){(ctx as? ComponentActivity)?.runOnUiThread{exporting=false;status="Save failed: ${e.message}"}}}.start()};override fun onError(message:String){(ctx as? ComponentActivity)?.runOnUiThread{exporting=false;status="Export failed: $message"}}})}}
 MaterialTheme(
  colorScheme = darkColorScheme(
   primary = Color(0xFF69E7B9),
   secondary = Color(0xFF80CFFF),
   background = Color(0xFF090F14),
   surface = Color(0xFF14202A)
  )
 ) {
  Scaffold(
   containerColor = Color(0xFF090F14),
   topBar = {
    TopAppBar(
     title = {
      Column {
       Text("SFX TIMELINE EDITOR", fontWeight = FontWeight.Bold)
       Text("AI-assisted cinematic sound design", fontSize = 11.sp, color = Color.LightGray)
      }
     },
     colors = TopAppBarDefaults.topAppBarColors(
      containerColor = Color(0xFF101A22),
      titleContentColor = Color.White
     )
    )
   },
   bottomBar = {
    NavigationBar(containerColor = Color(0xFF101A22)) {
     listOf(
      "home" to "Home",
      "get" to "Get Code",
      "import" to "I Have Code",
      "timeline" to "Timeline",
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
         "projects" -> Icons.Default.Folder
         else -> Icons.Default.GraphicEq
        }
        Icon(imageVector = navIcon, contentDescription = label)
       },
       label = { Text(label, fontSize = 10.sp) }
      )
     }
    }
   },
   content = { paddingValues ->
    when (page) {
    "home" -> {
     LazyColumn(
      modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
     ) {
      item {
       Text("Your sound design workspace", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
       Text("Script → AI JSON → Assets → Timeline → MP4", color = Color.LightGray)
      }
      item { HomeTile("GET CODE", "Create a strict AI video-analysis prompt") { page = "get" } }
      item { HomeTile("I HAVE CODE", "Import JSON and your original video") { page = "import" } }
      item { HomeTile("TIMELINE & EXPORT", "Review audio events and render final MP4") { page = "timeline" } }
      item {
       HomeTile("MY PROJECTS", "Open, edit, update or delete saved projects") {
        savedProjects = ProjectLibrary.load(ctx)
        page = "projects"
       }
      }
      if (project != null) {
       item {
        Button(onClick = { saveCurrentProject() }, modifier = Modifier.fillMaxWidth()) {
         Icon(Icons.Default.Save, contentDescription = null)
         Text(if (activeSavedId == null) "Save Project" else "Update Saved Project")
        }
       }
      }
      item {
       Text(
        "${project?.name ?: "No project loaded"}\n$videoName\n${assets.count { it.uri != null }} / ${assets.size} assets assigned\n$status",
        color = Color.LightGray
       )
      }
     }
    }

    "projects" -> {
     LazyColumn(
      modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
     ) {
      item {
       Text("MY PROJECTS", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
       Text("Saved projects stay on this device. Open one to continue editing.", color = Color.LightGray, fontSize = 12.sp)
      }
      item {
       Button(onClick = { savedProjects = ProjectLibrary.load(ctx) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Text("Refresh")
       }
      }
      if (savedProjects.isEmpty()) {
       item { Text("No saved projects yet. Open a project and tap Save Project.", color = Color.LightGray) }
      }
      items(savedProjects, key = { saved -> saved.id }) { saved ->
       Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF182A32))) {
        Column(
         modifier = Modifier.fillMaxWidth().padding(14.dp),
         verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
         Text(saved.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
         Text("${saved.assets.size} assets · ${saved.events.size} timeline events", color = Color.LightGray, fontSize = 12.sp)
         Text("Video: ${saved.videoName}", color = Color.Gray, fontSize = 11.sp)
         Text(
          "Saved: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(saved.savedAt))}",
          color = Color.Gray,
          fontSize = 10.sp
         )
         Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = { openSavedProject(saved) }, modifier = Modifier.weight(1f)) {
           Icon(Icons.Default.Edit, contentDescription = null)
           Text("Open / Edit")
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
      modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
     ) {
      item {
       Text("GET CODE", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
       Text(
        "Paste the complete LogiCut AI output: [ASSETS] and [RECIPE]. Then copy the generated prompt to your video-capable AI and attach the exported motion-graphics video.",
        color = Color.LightGray,
        fontSize = 12.sp
       )
       OutlinedTextField(
        value = logicutCode,
        onValueChange = { logicutCode = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 10,
        label = { Text("Paste LogiCut [ASSETS] + [RECIPE] code") }
       )
      }
      item {
       Text("SOUND STYLE", color = Color.White, fontWeight = FontWeight.Bold)
       listOf("premium_cinematic", "dark_intense", "modern_tech", "minimal").forEach { soundStyle ->
        Row(
         modifier = Modifier.fillMaxWidth().clickable { style = soundStyle },
         verticalAlignment = Alignment.CenterVertically
        ) {
         RadioButton(selected = style == soundStyle, onClick = { style = soundStyle })
         Text(soundStyle.replace("_", " "), color = Color.White)
        }
       }
      }
      item {
       Text("OUTPUT LANGUAGE", color = Color.White)
       Row {
        listOf("Hindi", "Hinglish", "English").forEach { language ->
         Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          RadioButton(selected = lang == language, onClick = { lang = language })
          Text(language, color = Color.White, fontSize = 12.sp)
         }
        }
       }
       Button(
        onClick = {
         prompt = MasterPrompt.build(logicutCode, style, lang)
         status = "Prompt generated"
        },
        enabled = logicutCode.contains("[RECIPE]") && logicutCode.contains("[ASSETS]"),
        modifier = Modifier.fillMaxWidth()
       ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null)
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
         Icon(Icons.Default.ContentCopy, contentDescription = null)
         Text("Copy Prompt")
        }
       }
      }
     }
    }

    "import" -> {
     LazyColumn(
      modifier = Modifier.padding(paddingValues).fillMaxSize().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
     ) {
      item {
       Text("I HAVE CODE", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
       Button(onClick = { videoPick.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.VideoFile, contentDescription = null)
        Text("Select Original Video")
       }
       Text(videoName, color = Color(0xFF69E7B9), fontSize = 12.sp)
       OutlinedButton(onClick = { jsonPick.launch(arrayOf("application/json", "text/*", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.FileOpen, contentDescription = null)
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
         try {
          val parsed = ProjectParser.parse(raw)
          project = parsed
          assets = parsed.assets
          events = parsed.events
          activeSavedId = null
          status = "Valid JSON: ${parsed.assets.size} assets, ${parsed.events.size} events"
         } catch (error: Exception) {
          status = "Validation error: ${error.message}"
         }
        },
        enabled = raw.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
       ) {
        Icon(Icons.Default.Verified, contentDescription = null)
        Text("Validate JSON & Create Slots")
       }
      }
      if (assets.isNotEmpty()) {
       item {
        Text("ASSET MANAGER · ${assets.count { it.uri != null }}/${assets.size}", color = Color(0xFF69E7B9), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { bulkPick.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
         Icon(Icons.Default.LibraryMusic, contentDescription = null)
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
      item { Text(status, color = Color.LightGray, fontSize = 12.sp) }
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
      { updatedEvents -> events = updatedEvents },
      { videoPick.launch(arrayOf("video/*")) },
      { bulkPick.launch(arrayOf("audio/*")) },
      { assetId ->
       pending = assetId
       audioPick.launch(arrayOf("audio/*"))
      },
      exporting,
      progress,
      { exportPick.launch("SFX_Final_Mix.mp4") },
      activeSavedId,
      { saveCurrentProject() },
      status
     )
    }
   }
  }
 )
 }
}

@Composable fun HomeTile(title:String,desc:String,action:()->Unit){Card(onClick=action,colors=CardDefaults.cardColors(containerColor=Color(0xFF182A32))){Column(Modifier.fillMaxWidth().padding(16.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(desc,color=Color.LightGray,fontSize=12.sp);Text("OPEN →",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}}

fun copyText(ctx:android.content.Context,s:String){val c=ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;c.setPrimaryClip(android.content.ClipData.newPlainText("SFX Master Prompt",s))}
@Composable fun AssetCard(a:AssetSlot,onPick:()->Unit){val ctx=androidx.compose.ui.platform.LocalContext.current;Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF18232C))){Column(Modifier.fillMaxWidth().padding(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AudioFile,null,tint=Color(0xFF69E7B9));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(a.filename,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold);Text("${a.id} · ${a.type}",color=Color.LightGray,fontSize=10.sp)};Text(if(a.uri!=null)"ADDED" else "MISSING",color=if(a.uri!=null)Color(0xFF69E7B9)else Color(0xFFFFC46B),fontSize=10.sp)};Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={copyText(ctx,a.filename)}){Icon(Icons.Default.ContentCopy,null);Text("Copy Name")};TextButton(onClick={copyText(ctx,a.id)}){Text("Copy ID")};Spacer(Modifier.weight(1f));TextButton(onClick=onPick){Text(if(a.uri!=null)"Replace" else "Upload")}};if(a.searchPhrase.isNotBlank())Text("Search: ${a.searchPhrase}",color=Color.LightGray,fontSize=10.sp);if(a.sourceName.isNotBlank())Text("Source: ${a.sourceName}",color=Color(0xFF80CFFF),fontSize=10.sp);if(a.sourceUrl.startsWith("https://")||a.sourceUrl.startsWith("http://")){TextButton(onClick={try{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(a.sourceUrl)))}catch(_:Exception){copyText(ctx,a.sourceUrl)}}){Text("Open Source Link")}}}}}

@OptIn(UnstableApi::class)
@Composable fun TimelinePage(mod:Modifier,ctx:android.content.Context,project:SfxProject?,video:Uri?,videoName:String,assets:List<AssetSlot>,events:List<AudioEvent>,onEvents:(List<AudioEvent>)->Unit,onVideo:()->Unit,onBulk:()->Unit,onPick:(String)->Unit,exporting:Boolean,progress:Float,onExport:()->Unit,activeSavedId:String?,onSaveProject:()->Unit,status:String){
 val player=remember(video){if(video==null)null else ExoPlayer.Builder(ctx).build().apply{setMediaItem(MediaItem.fromUri(video));prepare()}};DisposableEffect(player){onDispose{player?.release()}}
 LazyColumn(mod.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("TIMELINE & EXPORT",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold);if(player!=null)AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},update={it.player=player},modifier=Modifier.fillMaxWidth().height(210.dp))else OutlinedButton(onClick=onVideo,modifier=Modifier.fillMaxWidth()){Text("Select Video")};Text(videoName,color=Color.LightGray,fontSize=11.sp)}
  if(project==null)item{Text("Import and validate JSON first.",color=Color(0xFFFFC46B))}else{
   item{Text("${project.name} · ${project.durationMs} ms · ${project.fps} FPS",color=Color.LightGray,fontSize=12.sp);OutlinedButton(onClick=onBulk,modifier=Modifier.fillMaxWidth()){Text("Bulk Match Audio Files")};Text("ASSETS",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}
   items(assets){AssetCard(it){onPick(it.id)}}
   if(project!=null){item{Text("TIMELINE EVENTS",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}
   items(events){e->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF18232C))){Column(Modifier.padding(12.dp)){Text("${e.id} · ${assets.firstOrNull{it.id==e.assetId}?.filename?:e.assetId}",color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp);Text("${e.startMs}ms → ${e.endMs}ms · Layer ${e.layer}",color=Color.LightGray,fontSize=11.sp);Text(e.visualAction.ifBlank{e.reason},color=Color.Gray,fontSize=10.sp);Row(verticalAlignment=Alignment.CenterVertically){Text("Volume",color=Color.LightGray,fontSize=11.sp);Slider(e.volume,{v->onEvents(events.map{if(it.id==e.id)it.copy(volume=v)else it})},modifier=Modifier.weight(1f));Text("${(e.volume*100).toInt()}%",color=Color.White,fontSize=10.sp)}}}}
   if(project!=null){
    item {
     Button(onClick=onSaveProject,modifier=Modifier.fillMaxWidth()){
      Icon(Icons.Default.Save,null)
      Text(if(activeSavedId==null)"Save Project" else "Update Saved Project")
     }
     val missing=assets.count{it.required&&it.uri==null}
     Text("Required assets missing: $missing",color=if(missing==0)Color(0xFF69E7B9)else Color(0xFFFFC46B))
     Button(onClick=onExport,enabled=video!=null&&missing==0&&!exporting,modifier=Modifier.fillMaxWidth()){
      Icon(Icons.Default.Movie,null)
      Text("Render Final MP4")
     }
     if(exporting)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())
    }
   }
   item{Text(status,color=Color.LightGray,fontSize=11.sp);Text("Renderer uses AndroidX Media3 Composition to mix the source video and synchronized audio sequences. Crossfades between tracks are not supported by Media3 Composition; fade envelopes on individual clips are applied.",color=Color.Gray,fontSize=10.sp)}
 }
}

object MasterPrompt {
 fun build(logicutCode:String,style:String,language:String)= """
# ROLE
You are a senior cinematic motion-graphics sound designer, sound editor and audio asset researcher. Create a premium, high-impact sound design plan for the supplied LogiCut motion-graphics project.

# INPUT: LOGICUT CODE
The user is supplying the original LogiCut output below. It contains [ASSETS] and [RECIPE]. Parse both sections carefully. [ASSETS] describes visual source objects; [RECIPE] is the visual animation timeline. This is NOT an audio recipe.

${logicutCode.trim()}

# USER SETTINGS
Sound direction: $style
Output language: $language
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
 "schema_version":"1.1",
 "project":{"project_name":"Motion Graphics Sound Design","video_filename":"","video_duration_ms":0,"fps":0,"resolution":"","language":"$language","sound_style":"$style"},
 "analysis":{"video_analyzed":true,"script_analyzed":true,"logicut_recipe_analyzed":true,"frame_inspection_available":true,"timing_method":"frame_based","limitations":[]},
 "audio_settings":{"preserve_original_audio":true,"sample_rate":48000,"master_volume":1.0,"target_lufs":-14,"true_peak_db":-1},
 "assets":[{"id":"SFX_001","filename":"SFX_001_DEEP_IMPACT.wav","type":"sfx","category":"impact","description":"...","required":true,"suggested_duration_ms":900,"search_phrase":"...","source_name":"...","source_url":"https://...","visual_reason":"..."}],
 "timeline":[{"event_id":"EVENT_001","asset_id":"SFX_001","start_ms":500,"end_ms":1400,"duration_ms":900,"source_frame":30,"timing_confidence":"high","layer":1,"volume":0.8,"fade_in_ms":20,"fade_out_ms":250,"pan":0.0,"priority":"high","ducking_enabled":true,"visual_action":"Object scales in and lands","reason":"..."}],
 "mixing":{"music_ducking":true,"prevent_clipping":true,"normalization":"target_only_not_measured","master_fade_out_ms":1200},
 "validation":{"all_asset_references_valid":true,"timestamps_within_video":true,"duplicate_ids":false,"json_valid":true,"warnings":[]}
}

# VALIDATION
Every timeline asset_id must exist. IDs unique. All assets must have unique, copyable filenames. Include at least one background music asset/event. Check event timing, durations, fades, volume and pan. No event beyond video duration. Do not claim frame accuracy if not inspected. Ensure source_url is a real URL; if only a search URL is available, label source_name/search_phrase accordingly. Analyse the attached video and supplied LogiCut code now.
""".trimIndent()
}
