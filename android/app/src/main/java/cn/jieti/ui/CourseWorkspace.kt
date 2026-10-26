package cn.jieti.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import cn.jieti.JietiApp
import cn.jieti.ai.Curriculum
import cn.jieti.core.*
import cn.jieti.data.*

@Composable fun CourseOverview(progress:Curriculum.Progress){
 val chapters=progress.chapters
 if(LocalConfiguration.current.screenHeightDp<740){Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)){
  Text("${chapters.size} 章节模块",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))
  Text("${progress.mastered} / ${progress.total} 已掌握",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
  Text("${progress.refresh} 待复习",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))
 };return}
 Row(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)){
  CourseMetric(chapters.size.toString(),"章节模块")
  CourseMetric("${progress.mastered} / ${progress.total}","知识点已掌握")
  CourseMetric(progress.refresh.toString(),"待复习")
 }
}
@Composable private fun CourseMetric(value:String,label:String){Column{Text(value,style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary);Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=5.dp))}}

@Composable fun CourseWorkspace(app:JietiApp,sources:CourseSources,subject:String,plan:LearningPlan?,progress:Curriculum.Progress){
 val chapters=progress.chapters;var selected by rememberSaveable(subject){mutableStateOf(if(subject=="signals")"signals-1"else "math-h1")};var directoryOpen by remember(subject){mutableStateOf(false)};var filter by remember(subject,selected){mutableStateOf("all")}
 var expandedGroup by rememberSaveable(subject){mutableStateOf(chapters.first().group)}
 val chapter=remember(chapters,selected){chapters.firstOrNull{it.id==selected}?:chapters.first()}
 val areas=plan?.weaknesses.orEmpty()
 val focusByTopic=remember(areas,subject){areas.mapNotNull{area->Curriculum.topicFor(subject,area.s("concept"))?.let{it to area}}.groupBy({it.first},{it.second}).mapValues{it.value.first()}}
 val stepsByChapter=remember(plan?.steps,subject){val owner=chapters.flatMap{c->c.topics.map{it.id to c.id}}.toMap();plan?.steps.orEmpty().groupBy{owner[Curriculum.topicFor(subject,it.s("focus"))]}}
 val steps=stepsByChapter[chapter.id].orEmpty()
 val unknown=remember(areas,progress.unassigned,subject){(areas.map{it.s("concept")}+progress.unassigned).distinct().filter{it.isNotBlank()&&Curriculum.topicFor(subject,it)==null}}
 BoxWithConstraints(Modifier.fillMaxWidth()){
  val wide=maxWidth>=760.dp
  val directory:@Composable ()->Unit={Column{
   Text("课程目录",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(start=12.dp,bottom=12.dp))
   for(group in chapters.map{it.group}.distinct().filter{it.isNotBlank()})TextButton(onClick={expandedGroup=group;selected=chapters.first{it.group==group}.id},modifier=Modifier.fillMaxWidth().semantics{this.selected=expandedGroup==group}){Text(group,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=if(expandedGroup==group)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.78f));Text("${chapters.count{it.group==group}} 章",style=MaterialTheme.typography.labelSmall)}
   for(c in chapters.filter{it.group.isBlank()||it.group==expandedGroup}){val stats=progress.stats.getValue(c.id);val mastered=stats.mastered;val refresh=stats.refresh
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(c.id==selected)MaterialTheme.colorScheme.primary.copy(alpha=.07f)else androidx.compose.ui.graphics.Color.Transparent).clickable{selected=c.id;directoryOpen=false}.semantics{this.selected=c.id==selected}.padding(horizontal=12.dp,vertical=15.dp),verticalAlignment=Alignment.Top){
     Text(c.number,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=3.dp,end=12.dp))
     Column(Modifier.weight(1f)){Text(c.title,style=MaterialTheme.typography.bodyMedium);Text("$mastered / ${c.topics.size} 已掌握"+(if(c.availability=="outline")" · 待补讲义"else if(refresh>0)" · $refresh 待复习"else ""),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=7.dp));LinearProgressIndicator(progress={mastered.toFloat()/c.topics.size},modifier=Modifier.fillMaxWidth().padding(top=10.dp).height(2.dp),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.outline.copy(alpha=.12f))}
     Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null,Modifier.size(18.dp).padding(start=4.dp),tint=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))
    }
   }
  }}
  val detail:@Composable ()->Unit={Column(Modifier.fillMaxWidth()){
   Text(if(chapter.number=="00")"预备知识"else "${chapter.group.takeIf{it.isNotBlank()}?.plus(" · ").orEmpty()}第 ${chapter.number.toInt()} 章",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
   Text(chapter.title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=10.dp,bottom=10.dp))
   Text(chapter.goal,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))
   Text(if(chapter.availability=="outline")"总讲义目录 · 正文待补充"else chapter.source.ifBlank{"内置知识模块"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=12.dp))
   if(chapter.group.isNotBlank()){
    val source=sources.resolve(chapter.id,chapter.startPage)
    if(source!=null)TextButton(onClick={app.startActivity(Intent(app,SourceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("documentId",source.first).putExtra("page",source.second))},contentPadding=PaddingValues(0.dp)){Text("查看相关资料 · 第 ${chapter.startPage} 页",style=MaterialTheme.typography.labelSmall)}
    else Text("相关资料第 ${chapter.startPage} 页 · 导入同名资料后可打开",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=4.dp))
   }
   Text("知识点与熟练度",style=MaterialTheme.typography.titleSmall,modifier=Modifier.padding(top=28.dp,bottom=8.dp))
   Row{for((id,title)in listOf("all" to "全部","review" to "巩固与复习","diagnostic" to "待诊断"))TextButton(onClick={filter=id},modifier=Modifier.semantics{this.selected=filter==id}){Text(title,color=if(filter==id)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))}}
   val visible=remember(chapter,progress,filter){chapter.topics.filter{val state=progress.state(it.id);filter=="all"||filter=="review"&&state in listOf("needs_refresh","developing")||filter=="diagnostic"&&state in listOf("diagnostic","needs_evidence")}}
   for(topic in visible)key(topic.id){val state=progress.state(topic.id);val focus=focusByTopic[topic.id]
    HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.14f))
    Row(Modifier.fillMaxWidth().padding(vertical=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
     Text(topic.id.replace(Regex("^[a-z]+"),""),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.widthIn(min=28.dp).padding(top=4.dp))
     Column(Modifier.weight(1f)){Text(topic.title,style=MaterialTheme.typography.bodyMedium);if(focus!=null)Text(focus.s("reason"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=6.dp))
      val source=sources.resolve(chapter.id,topic.page)
      if(source!=null)TextButton(onClick={app.startActivity(Intent(app,SourceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("documentId",source.first).putExtra("page",source.second))},contentPadding=PaddingValues(0.dp)){Text("讲义第 ${topic.page} 页 · 查看原页",style=MaterialTheme.typography.labelSmall)}
      else Text((if(topic.page>0)"讲义第 ${topic.page} 页"else if(chapter.availability=="outline")"待补资料"else "知识点")+(if(focus!=null)" · 本周安排"else ""),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=6.dp))
     }
     Text(Curriculum.label(state),style=MaterialTheme.typography.labelSmall,color=if(state in listOf("mastered","developing"))MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(top=4.dp))
    }
   }
   if(visible.isEmpty())Text("本章暂时没有符合筛选条件的知识点。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=20.dp))
   Text("熟练度依据独立作答证据更新；尚未练习的知识点保留为待诊断。",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f),modifier=Modifier.padding(vertical=16.dp))
   if(steps.isNotEmpty()){var expanded by remember(subject,selected){mutableStateOf(false)};TextButton(onClick={expanded=!expanded}){Text("本章复习安排 · ${steps.size} 项")};if(expanded)for(step in steps)Column(Modifier.padding(vertical=10.dp)){Text("第 ${step.i("day")} 天 · ${step.s("focus")} · ${step.i("minutes")} 分钟",style=MaterialTheme.typography.bodyMedium);Text(step.s("task"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f));Text("验收："+step.s("checkpoint"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.78f))}}
  }}
  if(wide)Row(horizontalArrangement=Arrangement.spacedBy(32.dp)){Box(Modifier.width(236.dp)){directory()};Box(Modifier.weight(1f)){detail()}}
  else Column{TextButton(onClick={directoryOpen=!directoryOpen},modifier=Modifier.fillMaxWidth()){Text("课程目录 · ${chapter.number} ${chapter.title}",Modifier.weight(1f));Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight,null)};if(directoryOpen)directory();HorizontalDivider(Modifier.padding(top=4.dp,bottom=24.dp),color=MaterialTheme.colorScheme.outline.copy(alpha=.15f));detail()}
 }
 if(unknown.isNotEmpty()){var expanded by remember(subject){mutableStateOf(false)};TextButton(onClick={expanded=!expanded}){Text("待归类知识点 · ${unknown.size}")};if(expanded){Text("名称暂未匹配讲义目录，原有记录与安排仍保留。",style=MaterialTheme.typography.bodySmall);for(concept in unknown)Text(concept,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=8.dp))}}
}

internal fun courseSource(records:List<SyncRecord>,chapter:CourseChapter,topic:CourseTopic):Pair<String,Int>?{
 return CourseSources(courseDocuments(records)).resolve(chapter.id,topic.page)
}
