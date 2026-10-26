package cn.jieti.ai

import cn.jieti.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.net.URI

data class QuestionSource(val id:String,val type:String,val title:String,val url:String,val text:String,val provider:String="",val documentId:String="",val page:Int=0,val revision:String="")
data class SearchEndpoint(val id:String,val name:String,val url:String)
open class QuestionSearch(private val settings:SecureSettings){
 companion object {
  val endpoints=listOf(SearchEndpoint("yuanbao","元宝","https://api.wsa.cloud.tencent.com/SearchPro"),SearchEndpoint("tavily","Tavily","https://api.tavily.com/search"),SearchEndpoint("bocha","博查","https://api.bocha.cn/v1/web-search"))
  fun canonicalUrl(value:String):String?=runCatching{val u=URI(value.trim());if(u.scheme !in listOf("http","https")||u.host.isNullOrBlank()||u.userInfo!=null)return null;URI(u.scheme.lowercase(),null,u.host.lowercase(),u.port,u.path.ifBlank{"/"},u.query?.split('&')?.filterNot{it.startsWith("utm_",true)||it.startsWith("from=")||it.startsWith("spm=")}?.joinToString("&")?.ifBlank{null},null).toString()}.getOrNull()
  fun parse(id:String,root:JsonObject):List<QuestionSource>{
   val rows:List<JsonObject> = when(id){
    "yuanbao"->(root["Response"] as? JsonObject)?.get("Pages")?.let{it as? JsonArray}.orEmpty().mapNotNull{row->runCatching{if(row is JsonObject)row else wire.parseToJsonElement(row.jsonPrimitive.content).jsonObject}.getOrNull()}
    "tavily"->(root["results"] as? JsonArray).orEmpty().mapNotNull{it as? JsonObject}
    "bocha"->((root["data"] as? JsonObject)?.get("webPages") as? JsonObject)?.get("value")?.let{it as? JsonArray}.orEmpty().mapNotNull{it as? JsonObject}
    else->emptyList()
   }
   return rows.mapNotNull{row->val url=canonicalUrl(row.s("url"))?:return@mapNotNull null;val title=row.s("title").ifBlank{row.s("name")};val text=row.s("passage").ifBlank{row.s("content")}.ifBlank{row.s("summary")}.ifBlank{row.s("snippet")};if(title.isBlank()||text.length<20)null else QuestionSource("web:"+sha(url.toByteArray()).take(32),"web",title.take(200),url,text.take(10000),id)}.distinctBy{it.url}.take(12)
  }
 }
 private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).callTimeout(45,TimeUnit.SECONDS).followRedirects(false).build()
 private suspend fun execute(request:Request):JsonObject=withContext(Dispatchers.IO){val call=client.newCall(request);val cancel=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}};try{call.execute().use{r->check(r.isSuccessful){"搜索服务 HTTP ${r.code}"};val input=r.body?.byteStream()?:error("搜索返回为空");val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);require(out.size()<=2097152){"搜索结果过大"}};wire.parseToJsonElement(out.toString("UTF-8")).jsonObject}}finally{cancel.cancel()}}
 suspend fun testProvider(id:String):List<QuestionSource>{val endpoint=endpoints.first{it.id==id};val key=settings.get("search.$id.key");check(key.isNotBlank()){"请先保存搜索配置"};val url=settings.get("search.$id.url",endpoint.url);require(url.startsWith("https://")){"搜索地址必须是 HTTPS"};val query="考研数学 历年真题";val body=when(id){"yuanbao"->buildJsonObject{put("Query",query);put("Cnt","10")};"tavily"->buildJsonObject{put("query",query);put("max_results",5)};else->buildJsonObject{put("query",query);put("count",5);put("summary",true)}};return parse(id,execute(Request.Builder().url(url).header("Authorization","Bearer $key").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()))}
 open suspend fun search(query:String):List<QuestionSource>{
  require(query.toByteArray().size<=1400){"搜索关键词过长"};val errors=mutableListOf<String>();var configured=false
  for(endpoint in endpoints){val key=settings.get("search.${endpoint.id}.key");if(key.isBlank()||settings.get("search.${endpoint.id}.enabled","true")!="true")continue;configured=true
   try{
    val url=settings.get("search.${endpoint.id}.url",endpoint.url);require(url.startsWith("https://")){"搜索地址必须是 HTTPS"}
    val body=when(endpoint.id){"yuanbao"->buildJsonObject{put("Query",query);put("Cnt","10")};"tavily"->buildJsonObject{put("query",query);put("max_results",10);put("include_raw_content",false)};else->buildJsonObject{put("query",query);put("count",10);put("summary",true)}}
    val root=execute(Request.Builder().url(url).header("Authorization","Bearer $key").post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
    val serviceError=(root["Response"] as? JsonObject)?.get("Error") as? JsonObject;check(serviceError==null){"搜索服务暂不可用"}
    val rows=parse(endpoint.id,root);if(rows.isNotEmpty())return rows;errors.add(endpoint.name+"未找到题源")
   }catch(e:CancellationException){throw e}catch(_:Exception){errors.add(endpoint.name+"搜索未完成")}
  }
  if(!configured)return emptyList()
  if(errors.isNotEmpty())error(errors.joinToString("；"));return emptyList()
 }
 open suspend fun expand(source:QuestionSource):QuestionSource=withContext(Dispatchers.IO){
  val uri=runCatching{URI(source.url)}.getOrNull()?:return@withContext source
  if(uri.scheme!="https"||uri.host.isNullOrBlank()||Regex("(?i)localhost|.*\\.local|127\\..*|10\\..*|192\\.168\\..*|169\\.254\\..*|172\\.(1[6-9]|2[0-9]|3[01])\\..*|::1").matches(uri.host)||uri.path.endsWith(".pdf",true))return@withContext source
  try{val call=client.newCall(Request.Builder().url(source.url).header("User-Agent","Jieti-Study/0.2").build());val cancel=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}};try{call.execute().use{r->if(!r.isSuccessful||r.header("Content-Type").orEmpty().contains("pdf"))return@use source
    val stream=r.body?.byteStream()?:return@use source;val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(16384);while(out.size()<1048576){val n=stream.read(buffer,0,minOf(buffer.size,1048576-out.size()));if(n<0)break;out.write(buffer,0,n)}
    val html=out.toString("UTF-8").replace(Regex("(?is)<script[^>]*type=[\"']math/tex[^>]*>(.*?)</script>")){" \\("+it.groupValues[1]+"\\) "}.replace(Regex("(?is)<(script|style|nav|footer)[^>]*>.*?</\\1>")," ")
    val text=android.text.Html.fromHtml(html,android.text.Html.FROM_HTML_MODE_LEGACY).toString().replace('\u00a0',' ').trim()
    if(text.length>source.text.length)source.copy(text=(source.text+"\n\n原页面正文：\n"+text).take(18000))else source
   }}finally{cancel.cancel()}}catch(e:CancellationException){throw e}catch(_:Exception){source}
 }
}
