package cn.jieti.ai

import cn.jieti.data.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

data class Turn(val text:String,val toolCalls:JsonArray=JsonArray(emptyList()),val finishReason:String="stop")
open class Provider(private val settings:SecureSettings,private val foreground:AtomicInteger=AtomicInteger()){
 private val http=OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(180,TimeUnit.SECONDS).callTimeout(240,TimeUnit.SECONDS).build()
 fun model(role:String)=settings.get("model.$role",settings.get("model.tutor"))
 private fun request(endpoint:String,body:JsonObject?=null):Request{
  val base=settings.get("baseUrl","https://aiping.cn/api/v1").trim().trimEnd('/')
  require(base.startsWith("https://")){"API 地址必须使用 HTTPS"}
  val key=settings.get("apiKey");check(key.isNotBlank()){ "请先在设置中填写 API Key" }
  val builder=Request.Builder().url("$base/$endpoint").header("Authorization","Bearer $key")
  return if(body==null)builder.get().build()else builder.post(body.toString().toRequestBody("application/json".toMediaType())).build()
 }
 private fun checked(response:Response):Response {if(!response.isSuccessful){val code=response.code;response.close();throw ProviderFailure(code,when(code){401,403->"API 凭据无效或模型不可访问（$code）";429->"请求受限，请稍后重试";else->"模型服务返回 HTTP $code"})};return response}
 suspend fun models():List<String> = withContext(Dispatchers.IO){checked(http.newCall(request("models")).execute()).use{wire.parseToJsonElement(it.body!!.string()).jsonObject["data"]!!.jsonArray.mapNotNull{m->m.jsonObject["id"]?.jsonPrimitive?.content}}}
 suspend fun embeddings(texts:List<String>):List<FloatArray> = withContext(Dispatchers.IO){
  val model=settings.get("model.embedding","Qwen3-Embedding-0.6B")
  val body=buildJsonObject{put("model",model);put("input",JsonArray(texts.map(::JsonPrimitive)));put("dimensions",1024);put("encoding_format","float")}
  checked(http.newCall(request("embeddings",body)).execute()).use{response->
   val rows=wire.parseToJsonElement(response.body!!.string()).jsonObject["data"]!!.jsonArray.sortedBy{it.jsonObject["index"]!!.jsonPrimitive.int}
   require(rows.size==texts.size){"向量返回数量不正确"}
   rows.map{r->r.jsonObject["embedding"]!!.jsonArray.map{it.jsonPrimitive.float}.toFloatArray().also{require(it.size==1024&&it.all(Float::isFinite)){"请选择支持1024维的嵌入模型"}}}
  }
 }
 open suspend fun chat(role:String,messages:JsonArray,tools:JsonArray?=null,stream:Boolean=true,priority:Boolean=true,jsonMode:Boolean=false,onDelta:(String)->Unit={}):Turn=withContext(Dispatchers.IO){
  if(!priority)while(foreground.get()>0)delay(250)
  val model=model(role);check(model.isNotBlank()){ "请先选择 $role 模型" }
  val body=buildJsonObject{put("model",model);put("messages",messages);put("stream",stream);if(jsonMode)putJsonObject("response_format"){put("type","json_object")};if(tools!=null)put("tools",tools);if(role=="tutor")when(settings.get("tutor.thinking","auto")){"on"->put("enable_thinking",true);"off"->put("enable_thinking",false)}}
  val call=http.newCall(request("chat/completions",body));val cancellation=launch(start=CoroutineStart.UNDISPATCHED){try{awaitCancellation()}finally{call.cancel()}}
  try{checked(call.execute()).use{response->
   if(!stream){val choice=wire.parseToJsonElement(response.body!!.string()).jsonObject["choices"]!!.jsonArray[0].jsonObject;val msg=choice["message"]!!.jsonObject;val content=msg["content"]?.jsonPrimitive?.contentOrNull?:"";val calls=msg["tool_calls"]as? JsonArray?:JsonArray(emptyList());val reason=choice["finish_reason"]?.jsonPrimitive?.contentOrNull;onDelta(content);Completion.validate(reason,calls.isNotEmpty());return@withContext Turn(content,calls,reason!!)}
   var reason:String?=null;val text=StringBuilder();val calls=mutableMapOf<Int,MutableMap<String,String>>()
   response.body!!.source().use{source->while(!source.exhausted()){
    currentCoroutineContext().ensureActive();val line=source.readUtf8Line()?:break
    if(!line.startsWith("data:"))continue;val data=line.removePrefix("data:").trim();if(data=="[DONE]")break
    val root=wire.parseToJsonElement(data).jsonObject;if(root["error"]!=null)error("模型返回流式错误")
    for(choice in root["choices"]as? JsonArray?:JsonArray(emptyList())){choice.jsonObject["finish_reason"]?.jsonPrimitive?.contentOrNull?.let{reason=it};val d=choice.jsonObject["delta"]as? JsonObject?:continue;val part=d["content"]?.jsonPrimitive?.contentOrNull;if(part!=null){text.append(part);check(text.length<=1000000){"回答过长，请缩小本次问题范围"};onDelta(part)}
     for(t in d["tool_calls"]as? JsonArray?:JsonArray(emptyList())){val o=t.jsonObject;val index=o["index"]!!.jsonPrimitive.int;val c=calls.getOrPut(index){mutableMapOf("id" to "","name" to "","arguments" to "")};o["id"]?.jsonPrimitive?.content?.let{c["id"]=it};(o["function"]as? JsonObject)?.let{f->for(k in listOf("name","arguments"))f[k]?.jsonPrimitive?.content?.let{c[k]=c[k]+it}}}
    }
   }}
   Completion.validate(reason,calls.isNotEmpty())
   Turn(text.toString(),JsonArray(calls.toSortedMap().values.map{c->buildJsonObject{put("id",c["id"]!!);put("type","function");putJsonObject("function"){put("name",c["name"]!!);put("arguments",c["arguments"]!!)}}}))
  }}catch(e:Exception){currentCoroutineContext().ensureActive();throw e}finally{cancellation.cancel()}
 }
 suspend fun json(role:String,system:String,input:String,priority:Boolean=true):JsonObject {val t=chat(role,JsonArray(listOf(message("system",system+"\n只返回合法 JSON 对象，不使用代码围栏。"),message("user",input))),stream=false,priority=priority,jsonMode=true);return parseObject(t.text)}
 companion object {
  fun message(role:String,text:String)=buildJsonObject{put("role",role);put("content",text)}
  fun parseObject(text:String):JsonObject {val trimmed=text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim();return wire.parseToJsonElement(trimmed).jsonObject}
 }
}
