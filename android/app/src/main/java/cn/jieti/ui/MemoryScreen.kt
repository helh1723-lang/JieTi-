package cn.jieti.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.jieti.core.SyncRecord
import cn.jieti.data.*

/** A continuous reading surface; only contextual actions use a popup. */
@Composable fun MemoryScreen(records:List<SyncRecord>,label:(String)->String,onEdit:(SyncRecord)->Unit,onAction:(SyncRecord,String)->Unit){
 val memories=records.filter{it.kind=="memory"};val mastery=records.filter{it.kind=="mastery"}
 Column(Modifier.fillMaxSize()){
  Text("我的学习记忆",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(top=8.dp).semantics{heading()})
  Text("${memories.count{it.payload.s("status")=="active"}} 条使用中 · ${mastery.size} 个知识点",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=16.dp))
  LazyColumn(Modifier.fillMaxWidth().weight(1f),contentPadding=PaddingValues(bottom=28.dp)){
   if(memories.isEmpty())item{Text("日常对话中的学习习惯会慢慢积累在这里。",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=24.dp))}
   itemsIndexed(memories,key={_,r->r.id}){index,r->
    var menu by remember(r.id){mutableStateOf(false)};var details by remember(r.id){mutableStateOf(false)}
    if(index>0)HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.12f))
    Column(Modifier.fillMaxWidth().padding(vertical=18.dp)){
     Row(verticalAlignment=Alignment.Top){
      Column(Modifier.weight(1f).padding(top=9.dp,end=16.dp)){
       Text(r.payload.s("value"),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface)
       Text(listOf(label(r.payload.s("scope")),label(r.payload.s("origin")),label(r.payload.s("status"))).joinToString(" · ")+if(r.payload.b("locked"))" · 已锁定"else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp))
      }
      Box{IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreHoriz,contentDescription="记忆操作："+r.payload.s("value").take(18))};DropdownMenu(expanded=menu,onDismissRequest={menu=false}){
       DropdownMenuItem(text={Text("纠正")},onClick={menu=false;onEdit(r)})
       DropdownMenuItem(text={Text(if(r.payload.b("locked"))"解锁"else "锁定")},onClick={menu=false;onAction(r,"lock")})
       DropdownMenuItem(text={Text(if(r.payload.s("status")=="paused")"启用"else "暂停")},onClick={menu=false;onAction(r,"pause")})
       DropdownMenuItem(text={Text(if(details)"收起依据"else "查看依据")},onClick={menu=false;details=!details})
       DropdownMenuItem(text={Text("遗忘")},onClick={menu=false;onAction(r,"forget")})
      }}
     }
     SoftDisclosure(details){Text("依据：${r.payload.strings("sourceIds").joinToString()}\n观察时间：${r.payload.s("observedAt")}\n置信度：${(r.payload["confidence"]?.toString()?.toDoubleOrNull()?.times(100))?.toInt()?:0}%",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=12.dp,end=48.dp))}
    }
   }
   if(mastery.isNotEmpty()){
    item{Text("知识掌握",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=24.dp,bottom=12.dp).semantics{heading()})}
    itemsIndexed(mastery,key={_,r->r.id}){_,r->Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)){Text(r.payload.s("concept"),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text(label(r.payload.s("state")),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   }
  }
 }
}
