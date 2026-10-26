package cn.jieti.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import cn.jieti.JietiApp
import cn.jieti.ai.QuestionSearch
import kotlinx.coroutines.*

@Composable fun SearchSettings(app:JietiApp,run:(suspend()->Unit)->Unit,notice:(String)->Unit){
 var selected by remember{mutableStateOf("yuanbao")};val endpoint=QuestionSearch.endpoints.first{it.id==selected};var key by remember(selected){mutableStateOf(app.settings.get("search.$selected.key"))};var url by remember(selected){mutableStateOf(app.settings.get("search.$selected.url",endpoint.url))};var enabled by remember(selected){mutableStateOf(app.settings.get("search.$selected.enabled","true")=="true")};var automatic by remember{mutableStateOf(true)}
 LaunchedEffect(Unit){automatic=app.store.setting("coach.auto","true")=="true"}
 Text("学习规划与题源",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=24.dp,bottom=8.dp))
 Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("自动跟进学习情况",Modifier.weight(1f));Switch(automatic,{value->automatic=value;run{app.store.setSetting("coach.auto",value.toString())}})}
 Text("联网仅检索学科与知识点关键词；用户上传的资料库也可独立提供题源。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
 Row(Modifier.horizontalScroll(rememberScrollState())){for(p in QuestionSearch.endpoints)TextButton(onClick={selected=p.id}){Text(if(selected==p.id)"✓ ${p.name}"else p.name)}}
 Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("启用 ${endpoint.name}",Modifier.weight(1f));Switch(enabled,{enabled=it})}
 OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("搜索 API 地址")},singleLine=true)
 OutlinedTextField(key,{key=it},Modifier.fillMaxWidth().padding(top=8.dp),label={Text("${endpoint.name} API Key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
 Row{TextButton(onClick={app.settings.save("search.$selected.url",url.trim());app.settings.save("search.$selected.key",key.trim());app.settings.save("search.$selected.enabled",enabled.toString());notice("搜索配置已保存在此设备")}){Text("保存搜索配置")};TextButton(onClick={val id=selected;run{val rows=app.questionSearch.testProvider(id);withContext(Dispatchers.Main){notice("${QuestionSearch.endpoints.first{it.id==id}.name} 返回 ${rows.size} 条结果")}}}){Text("测试已保存配置")}}
}
