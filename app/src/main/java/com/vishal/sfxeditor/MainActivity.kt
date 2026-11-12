      items(savedProjects,key={it.id}){item->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF182A32))){Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text(item.name,color=Color.White,fontWeight=FontWeight.Bold,fontSize=17.sp)
        Text("${item.assets.size} assets · ${item.events.size} timeline events",color=Color.LightGray,fontSize=12.sp)
        Text("Video: ${item.videoName}",color=Color.Gray,fontSize=11.sp)
        Text("Saved: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.savedAt))}",color=Color.Gray,fontSize=10.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
          Button(onClick={openSavedProject(item)},modifier=Modifier.weight(1f)){Icon(Icons.Default.Edit,null);Text("Open / Edit")}
          OutlinedButton(onClick={ProjectLibrary.delete(ctx,item.id);savedProjects=ProjectLibrary.load(ctx);if(activeSavedId==item.id)activeSavedId=null;status="Project deleted"},modifier=Modifier.weight(1f)){Icon(Icons.Default.Delete,null);Text("Delete")}
        }
      }}}
    }
    "get"->LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("GET CODE",fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("Paste the complete LogiCut AI output: [ASSETS] and [RECIPE]. Then copy the generated prompt to your video-capable AI and attach the exported motion-graphics video.",color=Color.LightGray,fontSize=12.sp);OutlinedTextField(logicutCode,{logicutCode=it},modifier=Modifier.fillMaxWidth(),minLines=10,label={Text("Paste LogiCut [ASSETS] + [RECIPE] code")})};item{Text("SOUND STYLE",color=Color.White,fontWeight=FontWeight.Bold);listOf("premium_cinematic","dark_intense","modern_tech","minimal").forEach{Row(Modifier.fillMaxWidth().clickable{style=it},verticalAlignment=Alignment.CenterVertically){RadioButton(style==it,{style=it});Text(it.replace("_"," "),color=Color.White)}}};item{Text("OUTPUT LANGUAGE",color=Color.White);Row{listOf("Hindi","Hinglish","English").forEach{Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){RadioButton(lang==it,{lang=it});Text(it,color=Color.White,fontSize=12.sp)}}};Button(onClick={prompt=MasterPrompt.build(logicutCode,style,lang);status="Prompt generated"},enabled=logicutCode.contains("[RECIPE]") && logicutCode.contains("[ASSETS]"),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AutoAwesome,null);Text("Generate SFX Analysis Prompt")}};if(prompt.isNotBlank())item{OutlinedTextField(prompt,{},modifier=Modifier.fillMaxWidth(),minLines=9,readOnly=true);Button(onClick={copyText(ctx,prompt);status="Prompt copied"},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.ContentCopy,null);Text("Copy Prompt")}}}
    "import"->LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("I HAVE CODE",fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold);Button(onClick={videoPick.launch(arrayOf("video/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.VideoFile,null);Text("Select Original Video")};Text(videoName,color=Color(0xFF69E7B9),fontSize=12.sp);OutlinedButton(onClick={jsonPick.launch(arrayOf("application/json","text/*","*/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FileOpen,null);Text("Import JSON File")};OutlinedTextField(raw,{raw=it},modifier=Modifier.fillMaxWidth(),minLines=7,label={Text("Or paste JSON")});Button(onClick={try{val p=ProjectParser.parse(raw);project=p;assets=p.assets;events=p.events;activeSavedId=null;status="Valid JSON: ${p.assets.size} assets, ${p.events.size} events"}catch(e:Exception){status="Validation error: ${e.message}"}},enabled=raw.isNotBlank(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Verified,null);Text("Validate JSON & Create Slots")}};if(assets.isNotEmpty()){item{Text("ASSET MANAGER · ${assets.count{it.uri!=null}}/${assets.size}",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold);OutlinedButton(onClick={bulkPick.launch(arrayOf("audio/*"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.LibraryMusic,null);Text("Bulk Import Audio Files")}};items(assets){AssetCard(it){pending=it.id;audioPick.launch(arrayOf("audio/*"))}}};item{Text(status,color=Color.LightGray,fontSize=12.sp)}}
    else->TimelinePage(
        Modifier.padding(pad),
        ctx,
        project,
        video,
        videoName,
        assets,
        events,
        { events = it },
        { videoPick.launch(arrayOf("video/*")) },
        { bulkPick.launch(arrayOf("audio/*")) },
        { id -> pending = id; audioPick.launch(arrayOf("audio/*")) },
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
}

@Composable fun HomeTile(title:String,desc:String,action:()->Unit){Card(onClick=action,colors=CardDefaults.cardColors(containerColor=Color(0xFF182A32))){Column(Modifier.fillMaxWidth().padding(16.dp)){Text(title,color=Color.White,fontWeight=FontWeight.Bold);Text(desc,color=Color.LightGray,fontSize=12.sp);Text("OPEN →",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}}
fun displayName(ctx:android.content.Context,u:Uri):String=ctx.contentResolver.query(u,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0)else u.lastPathSegment?:"file"}?:u.lastPathSegment?:"file"
fun copyText(ctx:android.content.Context,s:String){val c=ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;c.setPrimaryClip(android.content.ClipData.newPlainText("SFX Master Prompt",s))}
@Composable fun AssetCard(a:AssetSlot,onPick:()->Unit){val ctx=androidx.compose.ui.platform.LocalContext.current;Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF18232C))){Column(Modifier.fillMaxWidth().padding(10.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.AudioFile,null,tint=Color(0xFF69E7B9));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(a.filename,color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold);Text("${a.id} · ${a.type}",color=Color.LightGray,fontSize=10.sp)};Text(if(a.uri!=null)"ADDED" else "MISSING",color=if(a.uri!=null)Color(0xFF69E7B9)else Color(0xFFFFC46B),fontSize=10.sp)};Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={copyText(ctx,a.filename)}){Icon(Icons.Default.ContentCopy,null);Text("Copy Name")};TextButton(onClick={copyText(ctx,a.id)}){Text("Copy ID")};Spacer(Modifier.weight(1f));TextButton(onClick=onPick){Text(if(a.uri!=null)"Replace" else "Upload")}};if(a.searchPhrase.isNotBlank())Text("Search: ${a.searchPhrase}",color=Color.LightGray,fontSize=10.sp);if(a.sourceName.isNotBlank())Text("Source: ${a.sourceName}",color=Color(0xFF80CFFF),fontSize=10.sp);if(a.sourceUrl.startsWith("https://")||a.sourceUrl.startsWith("http://")){TextButton(onClick={try{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(a.sourceUrl)))}catch(_:Exception){copyText(ctx,a.sourceUrl)}}){Text("Open Source Link")}}}}}

@OptIn(UnstableApi::class)
@Composable fun TimelinePage(
    mod: Modifier,
    ctx: android.content.Context,
    project: SfxProject?,
    video: Uri?,
    videoName: String,
    assets: List<AssetSlot>,
    events: List<AudioEvent>,
    onEvents: (List<AudioEvent>) -> Unit,
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
 val player=remember(video){if(video==null)null else ExoPlayer.Builder(ctx).build().apply{setMediaItem(MediaItem.fromUri(video));prepare()}};DisposableEffect(player){onDispose{player?.release()}}
 LazyColumn(mod.fillMaxSize().padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  item{Text("TIMELINE & EXPORT",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold);if(player!=null)AndroidView(factory={PlayerView(it).apply{this.player=player;useController=true}},update={it.player=player},modifier=Modifier.fillMaxWidth().height(210.dp))else OutlinedButton(onClick=onVideo,modifier=Modifier.fillMaxWidth()){Text("Select Video")};Text(videoName,color=Color.LightGray,fontSize=11.sp)}
  if(project==null)item{Text("Import and validate JSON first.",color=Color(0xFFFFC46B))}else{
   item{Text("${project.name} · ${project.durationMs} ms · ${project.fps} FPS",color=Color.LightGray,fontSize=12.sp);OutlinedButton(onClick=onBulk,modifier=Modifier.fillMaxWidth()){Text("Bulk Match Audio Files")};Text("ASSETS",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}
   items(assets){AssetCard(it){onPick(it.id)}}
   if(project!=null){item{Text("TIMELINE EVENTS",color=Color(0xFF69E7B9),fontWeight=FontWeight.Bold)}}
   items(events){e->Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF18232C))){Column(Modifier.padding(12.dp)){Text("${e.id} · ${assets.firstOrNull{it.id==e.assetId}?.filename?:e.assetId}",color=Color.White,fontWeight=FontWeight.Bold,fontSize=12.sp);Text("${e.startMs}ms → ${e.endMs}ms · Layer ${e.layer}",color=Color.LightGray,fontSize=11.sp);Text(e.visualAction.ifBlank{e.reason},color=Color.Gray,fontSize=10.sp);Row(verticalAlignment=Alignment.CenterVertically){Text("Volume",color=Color.LightGray,fontSize=11.sp);Slider(e.volume,{v->onEvents(events.map{if(it.id==e.id)it.copy(volume=v)else it})},modifier=Modifier.weight(1f));Text("${(e.volume*100).toInt()}%",color=Color.White,fontSize=10.sp)}}}}
   if(project!=null)item{Button(onClick = onSaveProject, modifier = Modifier.fillMaxWidth()){Icon(Icons.Default.Save,null);Text(if(activeSavedId==null)"Save Project" else "Update Saved Project")};val missing=assets.count{it.required&&it.uri==null};Text("Required assets missing: $missing",color=if(missing==0)Color(0xFF69E7B9)else Color(0xFFFFC46B));Button(onClick=onExport,enabled=video!=null&&missing==0&&!exporting,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Movie,null);Text("Render Final MP4")};if(exporting)LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth())}
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
