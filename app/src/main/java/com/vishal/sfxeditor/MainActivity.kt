


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
