package cn.jieti

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Density
import cn.jieti.capture.CapturePermissionActivity
import cn.jieti.core.*
import cn.jieti.data.*
import cn.jieti.ui.RichAnswer
import cn.jieti.ui.liquidSurface
import androidx.compose.ui.graphics.luminance
import cn.jieti.ui.SoftPage
import cn.jieti.ui.SoftDisclosure
import cn.jieti.ui.SoftNavigation
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

class MainActivity:ComponentActivity(){
 private val app get()=application as JietiApp
 private var sharedImage by mutableStateOf("")
 private var awaitingOverlay=false
 private val notify=registerForActivityResult(ActivityResultContracts.RequestPermission()){launchCapture()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);shared(intent);setContent{val theme by app.uiTheme.collectAsState();val font by app.uiFontScale.collectAsState();JietiTheme(theme,font){Workspace(app,sharedImage,{sharedImage=""},{startLearning()})}}}
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);shared(intent)}
 private fun shared(intent:Intent){if(intent.action==Intent.ACTION_SEND&&intent.type?.startsWith("image/")==true){val uri=intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?:return;app.scope.launch{val bytes=contentResolver.openInputStream(uri)?.use{it.readBytes()}?:return@launch;val hash=app.store.addAsset(bytes);withContext(Dispatchers.Main){sharedImage=hash}}}}
 override fun onResume(){super.onResume();if(awaitingOverlay&&Settings.canDrawOverlays(this)){awaitingOverlay=false;startLearning()}}
 private fun launchCapture(){startActivity(Intent(this,CapturePermissionActivity::class.java))}
 private fun startLearning(){if(!Settings.canDrawOverlays(this)){awaitingOverlay=true;android.widget.Toast.makeText(this,"请允许悬浮显示，返回后继续屏幕授权",android.widget.Toast.LENGTH_LONG).show();startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")));return};if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){notify.launch(Manifest.permission.POST_NOTIFICATIONS);return};launchCapture()}
}
@Composable fun JietiTheme(theme:String="system",fontScale:Float=1f,content:@Composable ()->Unit){
 val dark=theme=="dark"||(theme=="system"&&isSystemInDarkTheme());val density=LocalDensity.current
 val colors=if(dark)darkColorScheme(primary=Color(0xFFAFC49C),primaryContainer=Color(0xFF3C4A36),secondaryContainer=Color(0xFF343B31),background=Color(0xFF20241F),surface=Color(0xFF292E27),onSurface=Color(0xFFE3E7DC),onSurfaceVariant=Color(0xFFB1B9A9))else lightColorScheme(primary=Color(0xFF4F6953),primaryContainer=Color(0xFFE4E9DD),secondaryContainer=Color(0xFFEEEEE5),background=Color(0xFFF8F7F2),surface=Color(0xFFFFFEFA),onSurface=Color(0xFF30392F),onSurfaceVariant=Color(0xFF73786E))
 val warmColors=colors.copy(secondary=colors.primary,tertiary=colors.primary,onPrimary=if(dark)Color(0xFF20301E)else Color.White,onPrimaryContainer=colors.onSurface,onSecondaryContainer=colors.onSurface,surfaceVariant=colors.secondaryContainer,outline=colors.onSurfaceVariant,surfaceTint=colors.primary)
 CompositionLocalProvider(LocalDensity provides Density(density.density,density.fontScale*fontScale)){MaterialTheme(colorScheme=warmColors,typography=Typography(),content=content)}
}
@Composable private fun Workspace(app:JietiApp,shared:String,onShared:()->Unit,start:()->Unit){
 var page by rememberSaveableCompat("题目");var selected by rememberSaveableCompat("");var scopeName by rememberSaveableCompat("math")
 val rows by app.store.dao.observeScreen(selected).collectAsState(initial=emptyList())
 val records=remember(rows){rows.map{wire.decodeFromString<SyncRecord>(it.json)}.filter{!it.deleted}.sortedByDescending{it.updatedAt}}
 var preview by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var editMemory by remember{mutableStateOf<SyncRecord?>(null)};var editRecord by remember{mutableStateOf<SyncRecord?>(null)};var deleting by remember{mutableStateOf<SyncRecord?>(null)}
 LaunchedEffect(rows,selected){if(selected.isNotBlank()&&rows.firstOrNull{it.id==selected}?.let{wire.decodeFromString<SyncRecord>(it.json).deleted}==true)selected=""}
 val coroutine=rememberCoroutineScope()
 fun run(block:suspend()->Unit){coroutine.launch{busy=true;try{withContext(Dispatchers.IO){block()}}catch(e:Exception){error=e.message?:"操作未完成"}finally{busy=false}}}
 val imagePicker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->if(uri!=null)run{val bytes=app.contentResolver.openInputStream(uri)!!.use{it.readBytes()};val hash=app.store.addAsset(bytes);withContext(Dispatchers.Main){preview=hash}}}
 val docPicker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->run{for(uri in uris){val mime=app.contentResolver.getType(uri)?:"application/octet-stream";var name="课程资料";app.contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0)};val hash=app.store.addAsset(app.contentResolver.openInputStream(uri)!!);val doc=app.store.put("document",payload(Document(name,scopeName,listOf(hash),if(name.contains('.'))name.substringAfterLast('.')else mime,"queued",0,0,"",false)));app.agents.queue("document",doc.id)}}}
 LaunchedEffect(shared){if(shared.isNotBlank()){preview=shared;page="题目";onShared()}}
 LaunchedEffect(Unit){scopeName=app.store.setting("currentScope","math")}
 Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={SoftDisclosure(selected.isBlank()){Column(Modifier.padding(horizontal=24.dp,vertical=8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("⌖",fontSize=28.sp,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(10.dp));Text("截题",fontSize=22.sp);Spacer(Modifier.weight(1f));FilledTonalButton(onClick={start()}){Text("开始悬浮学习")}};Row(Modifier.padding(top=8.dp,bottom=6.dp).liquidSurface(30f,MaterialTheme.colorScheme.background.luminance()<.3f).horizontalScroll(rememberScrollState()).padding(horizontal=5.dp,vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){for(name in listOf("题目","记忆","资料库","导师进化","设备同步","设置"))SoftNavigation(name,page==name){page=name;selected=""}}}}}){padding->
  Column(Modifier.padding(padding).fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally){Column(Modifier.widthIn(max=1020.dp).fillMaxSize().padding(horizontal=24.dp)){
   SoftDisclosure(busy){LinearProgressIndicator(Modifier.fillMaxWidth())}
   if(error.isNotBlank()){Surface(color=Color(0xFFFFF0EC),shape=RoundedCornerShape(10.dp)){Row(Modifier.padding(12.dp)){Text(error,Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={error=""}){Text("关闭")}}}}
   SoftPage(page to selected,Modifier.fillMaxSize()){route->
   val shownPage=route.first;val shownQuestion=route.second
   when(shownPage){
    "题目"->if(shownQuestion.isNotBlank()){val q=records.firstOrNull{it.id==shownQuestion};if(q!=null)QuestionScreen(app,q,records,{selected=""},{block->run(block)},{editRecord=q},{deleting=q})}else{
     Row(verticalAlignment=Alignment.CenterVertically){Text("学习记录",fontSize=22.sp,modifier=Modifier.weight(1f));TextButton(onClick={imagePicker.launch("image/*")}){Text("导入截图")}}
     if(preview.isNotBlank()){Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(16.dp)){val bitmap=remember(preview){BitmapFactory.decodeFile(app.store.asset(preview).path)};if(bitmap!=null)Image(bitmap.asImageBitmap(),"预览截图",Modifier.fillMaxWidth().heightIn(max=240.dp));var question by remember{mutableStateOf("")};OutlinedTextField(question,{question=it},Modifier.fillMaxWidth(),label={Text("我卡在哪里（可不填）")});Row{TextButton(onClick={preview=""}){Text("取消")};Button(onClick={run{val q=app.store.put("question",payload(Question("新题目",scopeName,emptyList(),"","",listOf(preview),"help",emptyList(),"","","open")));val hash=preview;withContext(Dispatchers.Main){selected=q.id;preview=""};app.agents.send(q.id,question,"轻提示",listOf(hash))}}){Text("发送并获取轻提示")}}}}}
     if(records.none{it.kind=="question"})EmptyView("从一道不会的题开始","打开电子教材后，点悬浮球框选；也可导入已有截图。")else LazyColumn(verticalArrangement=Arrangement.spacedBy(22.dp)){items(records.filter{it.kind=="question"},key={it.id}){q->Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().clickable{selected=q.id}){Column(Modifier.padding(horizontal=22.dp,vertical=25.dp)){Text(if(q.payload.s("scope")=="signals")"858 · 信号与系统"else "数学一",color=MaterialTheme.colorScheme.primary,fontSize=11.sp);Spacer(Modifier.height(8.dp));Row(verticalAlignment=Alignment.CenterVertically){Text(q.payload.s("title"),fontSize=17.sp,modifier=Modifier.weight(1f));TextButton(onClick={editRecord=q}){Text("编辑")};TextButton(onClick={deleting=q}){Text("删除")}};Text(q.payload.s("diagnosis").ifBlank{"原题已保存，后台整理中"},maxLines=2,fontSize=12.sp,color=Color(0xFF737E69))}}}}
    }
    "记忆"->{Text("它目前了解我什么",fontSize=23.sp);Text("明确偏好优先，推断保留依据；你可以随时纠正。",fontSize=12.sp,color=Color(0xFF737E69));LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(top=18.dp)){items(records.filter{it.kind=="memory"},key={it.id}){r->Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(17.dp)){Text("${uiLabel(r.payload.s("scope"))} · ${uiLabel(r.payload.s("origin"))} · ${uiLabel(r.payload.s("status"))}",fontSize=10.sp,color=Color(0xFF737E69));Text(r.payload.s("value"),Modifier.padding(vertical=10.dp),fontSize=15.sp);Text("依据：${r.payload.strings("sourceIds").joinToString()}\n观察时间：${r.payload.s("observedAt")}",fontSize=10.sp,color=Color(0xFF737E69));Row{TextButton(onClick={editMemory=r}){Text("纠正")};TextButton(onClick={run{app.store.edit(r.id,buildJsonObject{put("locked",!r.payload.b("locked"))})}}){Text(if(r.payload.b("locked"))"解锁"else "锁定")};TextButton(onClick={run{app.store.edit(r.id,buildJsonObject{put("status",if(r.payload.s("status")=="paused")"active"else "paused")})}}){Text(if(r.payload.s("status")=="paused")"启用"else "暂停")};TextButton(onClick={run{app.store.forget(r.id)}}){Text("遗忘")}}}}};items(records.filter{it.kind=="mastery"},key={it.id}){r->Text("${r.payload.s("concept")}：${r.payload.s("state")}",Modifier.padding(12.dp))}}}
    "资料库"->{Row{Text("我的课程资料",fontSize=23.sp,modifier=Modifier.weight(1f));Button(onClick={docPicker.launch(arrayOf("application/pdf","image/*","application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/*"))}){Text("导入资料")}};Text("文字本地检索即用 · 扫描件后台识别 · 可追溯原页",fontSize=12.sp,color=Color(0xFF737E69));LazyColumn{items(records.filter{it.kind=="document"},key={it.id}){r->Surface(Modifier.padding(vertical=8.dp).fillMaxWidth(),shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(17.dp)){Row{Text(r.payload.s("title"),Modifier.weight(1f));TextButton(onClick={editRecord=r}){Text("编辑")};TextButton(onClick={deleting=r}){Text("删除")}};Text("${if(r.payload.b("paused"))"已暂停"else uiLabel(r.payload.s("status"))} · ${r.payload.i("processed")} / ${r.payload.i("pages")} 页",fontSize=12.sp,color=Color(0xFF737E69));if(r.payload.s("error").isNotBlank())Text(r.payload.s("error"),fontSize=12.sp,color=Color(0xFFB97676));Row{TextButton(onClick={run{val paused=!r.payload.b("paused");app.store.edit(r.id,buildJsonObject{put("paused",paused)});if(!paused)app.agents.queue("document",r.id)}}){Text(if(r.payload.b("paused"))"继续建库"else "暂停")};TextButton(onClick={run{app.store.edit(r.id,buildJsonObject{put("paused",false);put("status","queued");put("error","")});app.agents.restartDocument(r.id)}}){Text("重试")}}}}}}}
    "导师进化"->{Row(verticalAlignment=Alignment.CenterVertically){Text("教学策略与成长",fontSize=23.sp,modifier=Modifier.weight(1f));Button(onClick={app.agents.queue("policy","");error="已安排后台评测；证据不足时保持原策略"}){Text("优化我的导师")}};var auto by remember{mutableStateOf(true)};LaunchedEffect(records){auto=app.store.setting("autoEvolution","true")=="true"};Row(verticalAlignment=Alignment.CenterVertically){Text("自动评估教学策略",Modifier.weight(1f));Switch(auto,{value->auto=value;run{app.store.setSetting("autoEvolution",value.toString())}})};var learning by remember{mutableStateOf(true)};LaunchedEffect(records){learning=app.store.setting("learnPreferences","true")=="true"};Row(verticalAlignment=Alignment.CenterVertically){Text("从日常对话学习习惯",Modifier.weight(1f));Switch(learning,{value->learning=value;run{app.store.setSetting("learnPreferences",value.toString())}})};Text("不必说记住：追问与理解反馈会形成观察，多次证据后影响教学；可在记忆页纠正、暂停、锁定或遗忘。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);LazyColumn{items(records.filter{it.kind=="policy"},key={it.id}){r->Surface(Modifier.padding(vertical=8.dp).fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(17.dp)){Text(uiLabel(r.payload.s("status")),color=MaterialTheme.colorScheme.primary);Text(r.payload.s("reason"),Modifier.padding(vertical=10.dp));Text(r.payload.s("instructions"),fontSize=12.sp);Text("观察 ${r.payload.i("observations")} 次 · 负反馈 ${r.payload.i("negativeFeedback")} 次",fontSize=11.sp);if(r.payload.s("status")=="retired")TextButton(onClick={run{for(p in app.store.all("policy"))if(p.payload.s("status")in listOf("active","monitoring"))app.store.edit(p.id,buildJsonObject{put("status","retired")});app.store.edit(r.id,buildJsonObject{put("status","active")})}}){Text("恢复这一版")}}}}}}
    "设备同步"->SyncScreen(app,{block->run(block)},{error=it})
    "设置"->SettingsScreen(app,scopeName,{value->scopeName=value;run{app.store.setSetting("currentScope",value)}},{block->run(block)},{error=it})
   }}
  }}
 }
 if(editRecord!=null){val record=editRecord!!;RecordEditor(record,{editRecord=null}){changes->run{app.store.editUser(record.id,changes,record.rev);if(record.kind=="document")app.agents.restartDocument(record.id);withContext(Dispatchers.Main){editRecord=null}}}}
 if(deleting!=null){val record=deleting!!;AlertDialog(onDismissRequest={if(!busy)deleting=null},title={Text("删除${if(record.kind=="document")"资料"else "题目"}？")},text={Text(record.payload.s("title")+"\n将移出列表及关联内容，并在下次同步时通知另一端。原文件保留在本机。")},confirmButton={TextButton(enabled=!busy,onClick={run{app.agents.delete(record.id);withContext(Dispatchers.Main){if(selected==record.id)selected="";deleting=null}}}){Text("确认删除")}},dismissButton={TextButton(enabled=!busy,onClick={deleting=null}){Text("取消")}})}
 if(editMemory!=null){val r=editMemory!!;var value by remember(r.id){mutableStateOf(r.payload.s("value"))};AlertDialog(onDismissRequest={editMemory=null},title={Text("纠正记忆")},text={OutlinedTextField(value,{value=it},minLines=3)},confirmButton={TextButton(onClick={run{app.store.edit(r.id,buildJsonObject{put("value",value);put("origin","explicit");put("confidence",1.0);put("status","active")})};editMemory=null}){Text("保存")}},dismissButton={TextButton(onClick={editMemory=null}){Text("取消")}})}
}
private fun uiLabel(value:String)=mapOf("math" to "数学一","signals" to "858 信号与系统","global" to "全局","imported" to "历史档案","explicit" to "明确要求","inferred" to "交互观察","active" to "使用中","candidate" to "待验证","superseded" to "已被新观察替代","paused" to "已暂停","queued" to "排队中","processing" to "建库中","ready" to "可检索","failed" to "处理失败","monitoring" to "观察中","retired" to "历史版本","rejected" to "未通过评测","rolled_back" to "已回退")[value]?:value
@Composable private fun rememberSaveableCompat(initial:String)=androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf(initial)}
@Composable private fun EmptyView(title:String,subtitle:String){Column(Modifier.fillMaxSize().padding(40.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text("⌖",fontSize=60.sp,color=Color(0xFF737E69));Text(title,fontSize=21.sp,modifier=Modifier.padding(20.dp));Text(subtitle,fontSize=13.sp,color=Color(0xFF737E69))}}
@Composable private fun QuestionScreen(app:JietiApp,q:SyncRecord,records:List<SyncRecord>,back:()->Unit,run:(suspend()->Unit)->Unit,edit:()->Unit,delete:()->Unit){
 val live by app.agents.state.collectAsState();var input by androidx.compose.runtime.saveable.rememberSaveable(q.id){mutableStateOf("")};var mode by remember{mutableStateOf("轻提示")};var tab by remember{mutableStateOf("答疑")};var showMemory by remember{mutableStateOf(false)};var toolsExpanded by remember{mutableStateOf(false)};var modeMenu by remember{mutableStateOf(false)};var inputFocused by remember{mutableStateOf(false)}
 Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick=back){Text("‹ 返回")};Text(q.payload.s("title"),Modifier.weight(1f),fontSize=19.sp,maxLines=1);TextButton(onClick=edit){Text("编辑")};TextButton(onClick=delete){Text("删除")};TextButton(onClick={run{app.store.edit(q.id,buildJsonObject{put("status","done")});app.agents.queue("archive",q.id)}}){Text("完成本题")}}
 Row(Modifier.horizontalScroll(rememberScrollState())){for(name in listOf("答疑","原题","后台复盘","交互图解","我的笔记"))SoftNavigation(name,tab==name){tab=name}}
 val messages=records.filter{it.kind=="message"&&it.payload.s("questionId")==q.id}.sortedBy{it.updatedAt}
 var text=when(tab){"后台复盘"->"## 诊断\n${q.payload.s("diagnosis")}\n\n## 完整解析\n${q.payload.s("solution")}";"原题"->"## 原题转写\n${q.payload.s("transcription")}\n## 学生步骤\n${q.payload.s("studentWork")}";else->messages.joinToString("\n\n---\n\n"){"**${if(it.payload.s("role")=="user")"我"else "导师"}**\n\n${it.payload.s("content")}"}}
 if(live.questionId==q.id&&live.busy)text+="\n\n**导师**\n\n${live.text}"
 val artifact=records.firstOrNull{it.kind=="artifact"&&it.payload.s("questionId")==q.id}
 val note=records.firstOrNull{it.kind=="note"&&it.payload.s("questionId")==q.id}
 var noteText by androidx.compose.runtime.saveable.rememberSaveable(q.id){mutableStateOf(note?.payload?.s("content")?:"")};var noteBase by androidx.compose.runtime.saveable.rememberSaveable(q.id){mutableStateOf(note?.rev?:"")};var noteDirty by androidx.compose.runtime.saveable.rememberSaveable(q.id){mutableStateOf(false)}
 LaunchedEffect(note?.rev){if(!noteDirty){noteText=note?.payload?.s("content")?:"";noteBase=note?.rev?:""}}
 Column(Modifier.fillMaxSize()){
  SoftDisclosure(tab=="原题"){Column{for(hash in q.payload.strings("assetIds")){val bitmap=remember(hash){BitmapFactory.decodeFile(app.store.asset(hash).path)};if(bitmap!=null)Image(bitmap.asImageBitmap(),"原题截图",Modifier.fillMaxWidth().heightIn(max=200.dp))}}}
  if(tab=="交互图解"&&artifact==null)Text("需要图解时导师会自动生成，你也可以主动请求。",fontSize=13.sp)
  var artifactError by remember(artifact?.id){mutableStateOf("")}
  val context=LocalContext.current
  SoftPage(tab=="我的笔记",Modifier.weight(1f)){showNote->
  if(showNote)Column(Modifier.weight(1f)){OutlinedTextField(noteText,{noteText=it;noteDirty=true},Modifier.fillMaxWidth().weight(1f),placeholder={Text("下次看到什么条件，我应该想到什么？")});TextButton(onClick={val content=noteText;val base=noteBase;run{val latest=app.store.all("note").firstOrNull{it.payload.s("questionId")==q.id};check((latest?.rev?:"")==base){"笔记已在另一端更新，当前草稿仍保留，请先复制后重新打开"};val saved=app.store.put("note",payload(Note(q.id,content)),latest?.id?:uid());withContext(Dispatchers.Main){noteBase=saved.rev;noteDirty=false}}}){Text("保存笔记")}}
  else if(artifactError.isNotBlank())Text(artifactError+"\n"+(if(tab=="交互图解")artifact?.payload?.s("summary")?:""else text),Modifier.weight(1f).verticalScroll(rememberScrollState()))
  else AndroidView(factory={RichAnswer(it)},modifier=Modifier.fillMaxWidth().weight(1f),onRelease={it.destroy()},update={view->view.prepareTransition(tab);view.onArtifactFailure={artifactError=it};view.onArtifactRendered={bitmap->if(artifact!=null&&artifact.payload.strings("assetIds").isEmpty())run{val bytes=java.io.ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);bitmap.recycle();it.toByteArray()};val hash=app.store.addAsset(bytes);app.store.edit(artifact.id,buildJsonObject{put("assetIds",JsonArray(listOf(JsonPrimitive(hash))))})}};if(tab=="交互图解"&&artifact!=null)view.showArtifact(artifact.payload.s("html"))else if(tab=="答疑")view.showConversation(messages.map{it.payload.s("role") to it.payload.s("content")}+if(live.questionId==q.id&&live.text.isNotBlank()&&messages.none{it.id==live.responseId})listOf("assistant" to live.text)else emptyList())else view.showMarkdown(text,false)})
  }
  val citations=records.filter{it.kind=="citation"&&it.payload.s("questionId")==q.id}
  if(citations.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState())){for(c in citations)TextButton(onClick={context.startActivity(Intent(context,cn.jieti.ui.SourceActivity::class.java).putExtra("documentId",c.payload.s("documentId")).putExtra("page",c.payload.i("page")))}){Text("${records.firstOrNull{it.id==c.payload.s("documentId")}?.payload?.s("title")?:"资料"} · "+if(c.payload.i("page")==0)"用户补充"else "第${c.payload.i("page")}页",fontSize=11.sp)}}
  if(live.questionId==q.id&&live.error.isNotBlank())Row(verticalAlignment=Alignment.CenterVertically){Text(live.error,Modifier.weight(1f),color=Color(0xFFBD7777),fontSize=12.sp);TextButton(onClick={app.agents.retry()}){Text("重试本次提问")}}
  SoftDisclosure(toolsExpanded){Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(onClick={showMemory=!showMemory}){Text("本次使用的记忆")};TextButton(onClick={run{app.agents.createArtifact(q.id,mode,"为当前题目制作符合$mode 的图形解释")}}){Text("生成交互图解")};TextButton(onClick={run{val last=messages.lastOrNull{it.payload.s("role")=="assistant"};val p=last?.payload?.s("policyId")?.let{app.store.get(it)};if(p!=null)app.agents.observePolicy(p,false,true);app.store.put("note",payload(Note(q.id,"用户明确反馈：本次讲法不合适")))}}){Text("讲法不合适")}}}
  SoftDisclosure(showMemory){val ids=messages.lastOrNull{it.payload.s("role")=="assistant"}?.payload?.strings("memoryIds").orEmpty();Text(ids.mapNotNull{id->records.firstOrNull{it.id==id}?.payload?.s("value")}.joinToString("\n").ifBlank{"本次未使用长期记忆"},fontSize=11.sp,color=Color(0xFF737E69),modifier=Modifier.heightIn(max=100.dp).verticalScroll(rememberScrollState()))}
  Row(verticalAlignment=Alignment.CenterVertically){Box{TextButton(onClick={modeMenu=true}){Text("$mode ⌄",fontSize=12.sp)};DropdownMenu(expanded=modeMenu,onDismissRequest={modeMenu=false}){for(name in listOf("轻提示","再提示一点","检查步骤","完整讲解","总结同类题"))DropdownMenuItem(text={Text(name)},onClick={mode=name;modeMenu=false})}};Spacer(Modifier.weight(1f));TextButton(onClick={toolsExpanded=!toolsExpanded}){Text(if(toolsExpanded)"收起工具 ⌃"else "更多 ···",fontSize=12.sp)}}
  val composerTint by animateColorAsState(MaterialTheme.colorScheme.surface.copy(alpha=if(inputFocused).92f else .65f),tween(220),label="composer-light")
  val sendInteraction=remember{MutableInteractionSource()};val sendPressed by sendInteraction.collectIsPressedAsState()
  val sendScale by animateFloatAsState(if(sendPressed).92f else 1f,spring(dampingRatio=.8f,stiffness=650f),label="send-press")
  Surface(shape=RoundedCornerShape(28.dp),color=composerTint,shadowElevation=0.dp,modifier=Modifier.fillMaxWidth().padding(bottom=14.dp).liquidSurface(dark=MaterialTheme.colorScheme.background.luminance()<.3f).animateContentSize(spring(dampingRatio=.9f,stiffness=400f))){
   Row(verticalAlignment=Alignment.Bottom,modifier=Modifier.padding(start=20.dp,end=8.dp,top=8.dp,bottom=8.dp)){
    BasicTextField(input,{input=it},Modifier.weight(1f).heightIn(min=40.dp,max=160.dp).padding(vertical=10.dp).onFocusChanged{inputFocused=it.isFocused},maxLines=5,textStyle=LocalTextStyle.current.copy(color=MaterialTheme.colorScheme.onSurface,fontSize=15.sp),cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),decorationBox={inner->Box{if(input.isBlank())Text("说说你的思路，或卡住的地方…",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=15.sp);inner()}})
    Spacer(Modifier.width(10.dp));FilledIconButton(onClick={if(live.busy)app.agents.cancel()else if(input.isNotBlank()){if(app.agents.send(q.id,input,mode))input=""}},enabled=live.busy||input.isNotBlank(),interactionSource=sendInteraction,modifier=Modifier.size(44.dp).graphicsLayer{scaleX=sendScale;scaleY=sendScale}){AnimatedContent(targetState=live.busy,transitionSpec={fadeIn(tween(160)) togetherWith fadeOut(tween(90))},label="send-state"){thinking->Text(if(thinking)"■"else "↑",fontSize=23.sp)}}
   }
  }
 }
}
@Composable private fun SyncScreen(app:JietiApp,run:(suspend()->Unit)->Unit,notice:(String)->Unit){
 var pairing by remember{mutableStateOf("")};var progress by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
 val scanner=androidx.activity.compose.rememberLauncherForActivityResult(ScanContract()){result->if(result.contents!=null)run{app.sync.pair(result.contents);withContext(Dispatchers.Main){notice("配对成功，可以同步了")}}}
 Text("把积累同步到电脑",fontSize=23.sp);Text("两台设备连接同一网络，在电脑端开启同步窗口。",fontSize=12.sp,color=Color(0xFF737E69));Spacer(Modifier.height(25.dp))
 Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={scanner.launch(ScanOptions().setPrompt("扫描截题电脑端的配对二维码").setBeepEnabled(false))}){Text("扫描电脑二维码")};FilledTonalButton(onClick={run{val result=app.sync.synchronize{p->scope.launch{progress=p}};withContext(Dispatchers.Main){progress=result}}}){Text("开始同步")}}
 Text(progress,Modifier.padding(vertical=18.dp),fontSize=13.sp);OutlinedTextField(pairing,{pairing=it},Modifier.fillMaxWidth(),label={Text("或粘贴电脑显示的完整配对信息")},minLines=2);TextButton(onClick={run{app.sync.pair(pairing);withContext(Dispatchers.Main){notice("配对成功")}}}){Text("使用手动配对信息")}
}
@Composable private fun SettingsScreen(app:JietiApp,scope:String,onScope:(String)->Unit,run:(suspend()->Unit)->Unit,notice:(String)->Unit){
 var key by remember{mutableStateOf(app.settings.get("apiKey"))};var base by remember{mutableStateOf(app.settings.get("baseUrl","https://aiping.cn/api/v1"))};var models by remember{mutableStateOf("")};val fields=remember{mutableStateMapOf<String,String>().apply{for(role in listOf("tutor","archive","verify","artifact","strategy","embedding"))put(role,app.settings.get("model.$role",if(role=="embedding")"Qwen3-Embedding-0.6B"else ""))}}
 Column(Modifier.verticalScroll(rememberScrollState())){Text("模型与学习偏好",fontSize=23.sp);Row{FilterChip(scope=="math",{onScope("math")},label={Text("数学一")});Spacer(Modifier.width(10.dp));FilterChip(scope=="signals",{onScope("signals")},label={Text("858 信号与系统")})};OutlinedTextField(base,{base=it},Modifier.fillMaxWidth(),label={Text("API Base URL")});Spacer(Modifier.height(12.dp));OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("API Key")},visualTransformation=PasswordVisualTransformation());for((role,title)in listOf("tutor" to "导师（需视觉与工具调用）","archive" to "后台整理（需视觉）","verify" to "独立核验","artifact" to "交互页面","strategy" to "策略优化","embedding" to "资料嵌入（1024维）"))OutlinedTextField(fields[role]?:"",{fields[role]=it},Modifier.fillMaxWidth().padding(top=10.dp),label={Text("$title 模型")},supportingText={if(role!="tutor"&&role!="embedding")Text("留空时使用导师模型")})
  Row(Modifier.padding(vertical=16.dp)){Button(onClick={app.settings.save("apiKey",key);app.settings.save("baseUrl",base);for((role,value)in fields)app.settings.save("model.$role",value.ifBlank{if(role=="embedding")"Qwen3-Embedding-0.6B"else fields["tutor"].orEmpty()});notice("配置已保存在此设备")}){Text("保存配置")};TextButton(onClick={run{val list=app.provider.models();withContext(Dispatchers.Main){models=list.joinToString("\n")}}}){Text("获取可用模型")};TextButton(onClick={run{val t=app.provider.chat("tutor",buildJsonArray{add(cn.jieti.ai.Provider.message("user","请用一句简体中文说明你能帮助考研复习。"))},stream=true);withContext(Dispatchers.Main){notice("流式文本调用通过。资料文字检索无需嵌入模型；视觉、工具调用仍需题目验收。\n${t.text}")}}}){Text("测试已保存配置")}}
  Text("外观与阅读",fontSize=19.sp);val theme by app.uiTheme.collectAsState();val font by app.uiFontScale.collectAsState();Row{for((id,label)in listOf("system" to "跟随系统","light" to "浅色","dark" to "深色"))TextButton(onClick={app.settings.save("appearance.theme",id);app.uiTheme.value=id}){Text(if(theme==id)"✓ $label"else label)}};Text("字号比例 ${"%.0f".format(font*100)}%",fontSize=12.sp);Slider(font,{app.uiFontScale.value=it},valueRange=0.85f..1.4f,onValueChangeFinished={app.settings.save("appearance.font",app.uiFontScale.value.toString())})
  if(models.isNotBlank())Text(models,fontSize=12.sp);Text("应用会从真实学习证据更新档案；自动教学进化可在导师进化页关闭。",fontSize=12.sp,color=Color(0xFF737E69));Spacer(Modifier.height(30.dp))
 }
}

@Composable private fun RecordEditor(record:SyncRecord,dismiss:()->Unit,save:(JsonObject)->Unit){
 val fields=if(record.kind=="document")listOf("title" to "资料名称","supplementalText" to "补充/纠正内容（优先参考）")else listOf("title" to "题目标题","transcription" to "题干转写","studentWork" to "我的步骤","diagnosis" to "卡点与诊断","solution" to "解析","concepts" to "知识点（逗号分隔）")
 val values=remember(record.id){mutableStateMapOf<String,String>().apply{for((key,_)in fields)put(key,if(key=="concepts")record.payload.strings(key).joinToString("，")else record.payload.s(key))}}
 var subject by remember(record.id){mutableStateOf(record.payload.s("scope"))};var classification by remember(record.id){mutableStateOf(record.payload.s("classification"))}
 AlertDialog(onDismissRequest=dismiss,title={Text(if(record.kind=="document")"编辑资料"else "编辑题目")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState())){for((key,label)in fields)OutlinedTextField(values[key].orEmpty(),{values[key]=it},Modifier.fillMaxWidth().padding(bottom=10.dp),label={Text(label)},minLines=if(key=="title"||key=="concepts")1 else 3);Row{for((key,label)in listOf("math" to "数学一","signals" to "858"))FilterChip(subject==key,{subject=key},label={Text(label)})};if(record.kind=="question")Row(Modifier.horizontalScroll(rememberScrollState())){for((key,label)in listOf("help" to "求助","wrong" to "错题","uncertain" to "待确认"))FilterChip(classification==key,{classification=key},label={Text(label)})}}},confirmButton={TextButton(enabled=values["title"].orEmpty().isNotBlank(),onClick={save(buildJsonObject{for((key,_)in fields){val value=if(key=="concepts")JsonArray(values[key].orEmpty().split(Regex("[,，、\\n]")).map{it.trim()}.filter{it.isNotBlank()}.map(::JsonPrimitive))else JsonPrimitive(values[key].orEmpty());if(value!=record.payload[key]&&!(key=="supplementalText"&&record.payload[key]==null&&values[key].isNullOrBlank()))put(key,value)};if(subject!=record.payload.s("scope"))put("scope",subject);if(record.kind=="question"&&classification!=record.payload.s("classification"))put("classification",classification)})}){Text("保存修改")}},dismissButton={TextButton(onClick=dismiss){Text("取消")}})
}
