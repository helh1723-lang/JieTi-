package cn.jieti.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import cn.jieti.JietiApp
import cn.jieti.ai.CoachRules
import cn.jieti.ai.Curriculum
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import cn.jieti.core.*
import cn.jieti.data.*
import kotlinx.coroutines.*

@Composable fun CoachScreen(app:JietiApp,subject:String,onSubject:(String)->Unit,run:(suspend()->Unit)->Unit,onDiscuss:(String)->Unit){
 var date by remember{mutableStateOf(CoachRules.day())};LaunchedEffect(Unit){while(true){delay(60000);date=CoachRules.day()}}
 val records by remember(app.store,date){app.store.dao.observeCoach("daily:%:$date:%").decodedRecords()}.collectAsState(initial=emptyList())
 val plan=remember(records,subject){records.firstOrNull{it.kind=="plan"&&it.payload.s("scope")==subject}?.value<LearningPlan>()};val items=remember(records,subject,date){records.filter{it.kind=="practice"&&it.payload.s("scope")==subject&&it.payload.s("day")==date}.sortedBy{it.payload.i("slot")}}
 val status by app.coach.state.collectAsState();var selected by remember(subject,date){mutableStateOf("")};var goal by remember(subject){mutableStateOf("")};var minutes by remember{mutableIntStateOf(60)};var sourceStatus by remember{mutableStateOf("")};var editing by remember(subject){mutableStateOf(false)}
 LaunchedEffect(subject,status.busy,records,editing){
  val settings=withContext(Dispatchers.IO){Triple(app.store.setting("coach.goal.$subject","考研复习，先补齐薄弱知识点，再做迁移与限时练习"),app.store.setting("coach.minutes","60").toIntOrNull()?:60,app.store.setting("coach.dailyStatus.$subject"))}
  if(!editing){goal=settings.first;minutes=settings.second};sourceStatus=settings.third
 }
 // Let the selected course settle first. A quick switch cancels only this pending delay;
 // an already-started refresh belongs to the application, not to this composition.
 LaunchedEffect(subject,date){delay(700);withContext(Dispatchers.IO){if(app.provider.model("coach").isNotBlank()&&app.store.setting("coach.auto","true")=="true"&&app.store.setting("coach.enabled.$subject",if(subject=="math")"true"else "false")=="true")app.coach.requestAutoRefresh(subject,date)}}
 val courseScope=rememberCoroutineScope()
 val courseData=remember(app.store){CourseData(app.store,courseScope)}
 val progressByCourse by courseData.progress.collectAsStateWithLifecycle()
 val sources by courseData.sources.collectAsStateWithLifecycle()
 val progress=progressByCourse[subject]
 val courseNavigation=rememberSaveableStateHolder()
 var section by rememberSaveable(subject){mutableStateOf("chapters")}
 val active=items.firstOrNull{it.id==selected}
 if(active!=null){DailyPractice(app,active,{selected=""},run,onDiscuss);return}
 val wide=LocalConfiguration.current.screenWidthDp>=840
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
  Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("学习规划",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));if(wide){for((id,title)in listOf("math" to "数学","signals" to "信号与系统"))TextButton(onClick={onSubject(id)},modifier=Modifier.semantics{this.selected=subject==id}){Text(title,color=if(subject==id)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)};TextButton(onClick={editing=!editing}){Text("目标与时间")}};TextButton(enabled=!status.busy,onClick={run{app.store.setSetting("coach.enabled.$subject","true");app.coach.refresh(subject)}}){Text(if(status.busy)"更新中…"else "更新安排")}}
  if(!wide)Row{for((id,title)in listOf("math" to "数学","signals" to "信号与系统"))TextButton(onClick={onSubject(id)},modifier=Modifier.semantics{this.selected=subject==id}){Text(if(subject==id)"✓ $title"else title)};Spacer(Modifier.weight(1f));TextButton(onClick={editing=!editing}){Text("目标与时间")}}
  SoftDisclosure(editing){Column{OutlinedTextField(goal,{goal=it},Modifier.fillMaxWidth(),label={Text("学习目标")},minLines=2);Text("每天 $minutes 分钟",style=MaterialTheme.typography.bodySmall);Slider(minutes.toFloat(),{minutes=it.toInt()},valueRange=15f..180f);TextButton(onClick={val value=goal;val time=minutes;run{app.store.setSetting("coach.goal.$subject",value);app.store.setSetting("coach.minutes",time.toString());withContext(Dispatchers.Main){editing=false};app.coach.refresh(subject)}}){Text("保存并更新")}}}
  if(status.busy){LinearProgressIndicator(Modifier.fillMaxWidth());Text(status.phase,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))}
  if(status.error.isNotBlank())Text(status.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))
  progress?.let{CourseOverview(it)}
  Row(Modifier.fillMaxWidth().padding(bottom=12.dp)){for((id,title)in listOf("chapters" to "章节学习","daily" to "每日三题","week" to "周计划"))TextButton(onClick={section=id},modifier=Modifier.semantics{this.selected=section==id}){Text(title,color=if(section==id)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}}
  if(section=="chapters"){if(progress!=null)courseNavigation.SaveableStateProvider(subject){CourseWorkspace(app,sources,subject,plan,progress)}else Text("正在读取学习记录…",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=24.dp))}
  if(section=="week"){
   if(plan==null)Text("在章节中梳理知识点，更新安排后生成周计划。",style=MaterialTheme.typography.bodyMedium)else{
    Text(plan.summary,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=12.dp))
    for(step in plan.steps)Column(Modifier.padding(vertical=14.dp)){val owner=cn.jieti.ai.Curriculum.chapters(subject).firstOrNull{c->c.topics.any{it.id==cn.jieti.ai.Curriculum.topicFor(subject,step.s("focus"))}};Text(owner?.title?:"待归类",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text("第 ${step.i("day")} 天 · ${step.s("focus")} · ${step.i("minutes")} 分钟",style=MaterialTheme.typography.bodyMedium);Text(step.s("task"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("验收："+step.s("checkpoint"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
  }
  if(section=="daily"){
  Row{Text("每日三题",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));Text("${items.count{it.payload.s("status")=="reviewed"}} / 3 · $date",style=MaterialTheme.typography.bodySmall)}
  if(sourceStatus.isNotBlank())Text(sourceStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=10.dp))
  for((index,row)in items.withIndex()){val p=row.value<PracticeItem>();val owner=cn.jieti.ai.Curriculum.chapters(subject).firstOrNull{c->c.topics.any{it.id==cn.jieti.ai.Curriculum.topicFor(subject,p.concept)}};Column(Modifier.padding(vertical=16.dp)){Text(owner?.title?:"待归类",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Row{Text("${p.slot}. ${p.concept}",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));TextButton(onClick={selected=row.id}){Text(if(p.status=="reviewed")"查看讲评"else "开始作答")}};Text((if(p.sourceType=="library")"资料库"else "联网题源")+" · "+p.sourceTitle+(if(p.page>0)" · 第 ${p.page} 页"else "")+(if(p.examLabel.isNotBlank())" · 来源标注 "+p.examLabel else ""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);if(index<items.lastIndex)HorizontalDivider(Modifier.padding(top=18.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))}}
  if(items.size<3)TextButton(enabled=!status.busy,onClick={run{app.coach.refresh(subject)}}){Text("补齐今日题目")}
  }
 }
}
@Composable private fun DailyPractice(app:JietiApp,record:SyncRecord,back:()->Unit,run:(suspend()->Unit)->Unit,onDiscuss:(String)->Unit){
 val item=record.value<PracticeItem>();val photosDraft=remember{WorkPhotos(app)};var photos by remember(record.id){mutableStateOf(photosDraft.read(record.id))};var composerExpanded by remember(record.id){mutableStateOf(item.status!="reviewed")};var photosBusy by remember(record.id){mutableStateOf(false)};var recognized by remember(record.id){mutableStateOf("")};val drafts=remember{LocalDrafts(app)};var work by remember(item.questionId,record.id){mutableStateOf(drafts.read("daily:${record.id}")?.text.orEmpty())};var independent by remember(record.id){mutableStateOf(true)};var reference by remember(record.id){mutableStateOf(false)};val status by app.coach.state.collectAsState()
 LaunchedEffect(item.lastAttemptId){if(item.lastAttemptId.isNotBlank()){composerExpanded=false;val attempt=withContext(Dispatchers.IO){app.store.get(item.lastAttemptId)};recognized=attempt?.payload?.s("transcription").orEmpty();if(work.isBlank())work=attempt?.payload?.s("work").orEmpty();if(!photosDraft.hasDraft(record.id)){photos=attempt?.payload?.strings("assetIds").orEmpty();photosDraft.save(record.id,photos)}}}
 Column(Modifier.fillMaxSize()){
  Row{TextButton(onClick=back){Text("‹ 返回")};Text("每日三题 · 第 ${item.slot} 题",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick={run{app.coach.markHelp(record.id);withContext(Dispatchers.Main){if(item.documentId.isNotBlank())app.startActivity(Intent(app,SourceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("documentId",item.documentId).putExtra("page",item.page).putExtra("questionId",item.questionId).putExtra("citationText",item.sourceQuote))else QuestionSearchLink.open(app,item.sourceUrl)}}}){Text("原文")}}
  Text(item.sourceTitle+(if(item.examLabel.isBlank())""else " · 来源标注 ${item.examLabel}"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  AndroidView(factory={RichAnswer(it)},modifier=Modifier.fillMaxWidth().weight(1f),onRelease={it.destroy()},update={view->view.focusQuestionId=item.questionId;view.showStudyText(item.stem+(if(recognized.isNotBlank())"\n\n## 手写步骤识别\n"+recognized else "")+(if(item.lastFeedback.isNotBlank())"\n\n## 本次讲评\n"+item.lastFeedback else "")+(if(reference)"\n\n## 原文参考答案\n"+item.referenceAnswer else ""))})
  if(status.error.isNotBlank())Text(status.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
  if(item.status=="reviewed")TextButton(onClick={composerExpanded=!composerExpanded}){Text(if(composerExpanded)"收起作答"else "查看或修改我的作答")}
  SoftDisclosure(composerExpanded){Column{
  WorkPhotoPicker(app,record.id,photos,status.gradingId.isBlank(),run,{photos=it},{photosBusy=it})
  OutlinedTextField(work,{work=it;drafts.save("daily:${record.id}",it)},Modifier.fillMaxWidth().heightIn(max=160.dp),label={Text("补充文字或解题步骤（可选）")},minLines=2)
  Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Checkbox(independent,{independent=it},enabled=!item.helpUsed);Text(if(item.helpUsed)"已参考提示/原文，按辅助完成记录"else "独立作答，未参考提示或答案",style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f));TextButton(enabled=status.gradingId.isBlank()&&!photosBusy&&(work.isNotBlank()||photos.isNotEmpty()),onClick={val answer=work;val images=photos;run{app.coach.grade(record.id,answer,independent,images)}}){Text(if(status.gradingId==record.id)"核验中…"else "提交讲评")}}
  }}
  Row{TextButton(onClick={run{app.coach.markHelp(record.id);withContext(Dispatchers.Main){app.agents.send(item.questionId,"先给我一个启动方向，不要透露完整答案。","轻提示");onDiscuss(item.questionId)}}}){Text("先给一点提示")};if(item.referenceAnswer.isNotBlank()&&item.status=="reviewed")TextButton(onClick={reference=!reference}){Text(if(reference)"收起原文答案"else "对照原文答案")}}
 }
}
object QuestionSearchLink {fun open(app:JietiApp,url:String){val safe=cn.jieti.ai.QuestionSearch.canonicalUrl(url)?:return;app.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(safe)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}}
