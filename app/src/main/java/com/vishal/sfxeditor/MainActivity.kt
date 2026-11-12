


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
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF69E7B9),secondary=Color(0xFF80CFFF),background=Color(0xFF090F14),surface=Color(0xFF14202A))){
  Scaffold(containerColor=Color(0xFF090F14),topBar={TopAppBar(title={Column{Text("SFX TIMELINE EDITOR",fontWeight=FontWeight.Bold);Text("AI-assisted cinematic sound design",fontSize=11.sp,color=Color.LightGray)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF101A22),titleContentColor=Color.White))},bottomBar={NavigationBar(containerColor=Color(0xFF101A22)){listOf("home" to "Home","get" to "Get Code","import" to "I Have Code","timeline" to "Timeline","projects" to "Projects").forEach{(k,n)->NavigationBarItem(selected=page==k,onClick={page=k},icon={Icon(when(k){"home"->Icons.Default.Home;"get"->Icons.Default.AutoAwesome;"import"->Icons.Default.Code;"projects"->Icons.Default.Folder;else->Icons.Default.GraphicEq},null)},label={Text(n,fontSize=10.sp)})}}}){pad->
   when(page){
    "home"->LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text("Your sound design workspace",fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("Script → AI JSON → Assets → Timeline → MP4",color=Color.LightGray)};item{HomeTile("GET CODE","Create a strict AI video-analysis prompt",{page="get"})};item{HomeTile("I HAVE CODE","Import JSON and your original video",{page="import"})};item{HomeTile("TIMELINE & EXPORT","Review audio events and render final MP4",{page="timeline"})};item{HomeTile("MY PROJECTS","Open, edit, update or delete saved projects",{savedProjects=ProjectLibrary.load(ctx);page="projects"})};if(project!=null)item{Button(onClick={saveCurrentProject()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,null);Text(if(activeSavedId==null)"Save Project" else "Update Saved Project")}};item{Text("${project?.name?:"No project loaded"}\n$videoName\n${assets.count{it.uri!=null}} / ${assets.size} assets assigned\n$status",color=Color.LightGray)}}
    "projects"->LazyColumn(Modifier.padding(pad).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      item{Text("MY PROJECTS",fontSize=24.sp,color=Color.White,fontWeight=FontWeight.Bold);Text("Saved projects stay on this device. Open one to continue editing.",color=Color.LightGray,fontSize=12.sp)}
      item{Button(onClick={savedProjects=ProjectLibrary.load(ctx)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Refresh,null);Text("Refresh")}}
      if(savedProjects.isEmpty())item{Text("No saved projects yet. Open a project and tap Save Project.",color=Color.LightGray)}
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
        Modifier.padding(pad), ctx, project, video, videoName, assets, events,
        { events = it },
        { videoPick.launch(arrayOf("video/*")) },
        { bulkPick.launch(arrayOf("audio/*")) },
        { id -> pending = id; audioPick.launch(arrayOf("audio/*")) },
        exporting, progress,
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




