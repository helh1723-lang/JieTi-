package cn.jieti.sync

import android.content.Context
import android.net.Uri
import android.util.Base64
import cn.jieti.JietiApp
import cn.jieti.core.*
import cn.jieti.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class SyncClient(private val context:Context,private val store:LearningStore,private val settings:SecureSettings){
 private fun http(fingerprint:String):OkHttpClient{
  require(Regex("[a-f0-9]{64}").matches(fingerprint)){"证书指纹格式错误"}
  val trust=object:X509TrustManager{override fun getAcceptedIssuers()=emptyArray<X509Certificate>();override fun checkClientTrusted(chain:Array<out X509Certificate>?,authType:String?)=Unit;override fun checkServerTrusted(chain:Array<out X509Certificate>?,authType:String?){val cert=chain?.firstOrNull()?:throw java.security.cert.CertificateException("证书不存在");cert.checkValidity();if(sha(cert.encoded)!=fingerprint)throw java.security.cert.CertificateException("电脑证书发生变化，请重新配对")}}
  val ssl=SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),SecureRandom())}
  return OkHttpClient.Builder().sslSocketFactory(ssl.socketFactory,trust).hostnameVerifier{_,session->runCatching{sha(session.peerCertificates.first().encoded)==fingerprint}.getOrDefault(false)}.connectTimeout(15,TimeUnit.SECONDS).readTimeout(120,TimeUnit.SECONDS).build()
 }
 private fun request(http:OkHttpClient,url:String,path:String,token:String,body:JsonObject?=null):JsonObject{
  val b=Request.Builder().url(url+path);if(token.isNotBlank())b.header("Authorization","Bearer $token");if(body!=null)b.post(body.toString().toRequestBody("application/json".toMediaType()))
  return http.newCall(b.build()).execute().use{r->check(r.isSuccessful){"同步请求失败（${r.code}），请检查连接或重新配对"};wire.parseToJsonElement(r.body!!.string()).jsonObject}
 }
 suspend fun pair(pairing:String)=withContext(Dispatchers.IO){
  val raw=if(pairing.startsWith("jieti://")){val uri=Uri.parse(pairing);val encoded=uri.getQueryParameter("data")?:error("无效配对信息");String(Base64.decode(encoded,Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))}else pairing
  val info=wire.parseToJsonElement(raw).jsonObject;val url=info.s("url").trimEnd('/');require(url.startsWith("https://")){"同步必须使用HTTPS"}
  val fp=info.s("fingerprint");val result=request(http(fp),url,"/pair","",buildJsonObject{put("secret",info.s("secret"));put("deviceId",store.deviceId)})
  settings.save("sync.url",url);settings.save("sync.fp",fp);settings.save("sync.token",result.s("token"))
 }
 private fun upload(http:OkHttpClient,url:String,token:String,hash:String){
  val file=store.asset(hash);check(file.exists()){ "待同步附件尚未到达" }
  val head=http.newCall(Request.Builder().url("$url/assets/$hash").header("Authorization","Bearer $token").head().build()).execute()
  val exists=head.code==200;var offset=head.header("X-Offset")?.toLongOrNull()?:0;head.close();if(exists)return
  file.inputStream().use{input->check(input.skip(offset)==offset);val buffer=ByteArray(1048576);while(offset<file.length()){val n=input.read(buffer);check(n>0);http.newCall(Request.Builder().url("$url/assets/$hash?offset=$offset&total=${file.length()}").header("Authorization","Bearer $token").put(buffer.copyOf(n).toRequestBody("application/octet-stream".toMediaType())).build()).execute().use{check(it.isSuccessful){"附件传输失败（${it.code}）"}};offset+=n}}
 }
 private fun download(http:OkHttpClient,url:String,token:String,hash:String){
  val target=store.asset(hash);if(target.exists())return;val partial=File(target.parentFile,"$hash.download");var offset=if(partial.exists())partial.length()else 0L;var total=Long.MAX_VALUE
  while(offset<total){http.newCall(Request.Builder().url("$url/assets/$hash?offset=$offset").header("Authorization","Bearer $token").build()).execute().use{r->check(r.isSuccessful){"电脑上的附件尚未完整保存"};total=r.header("X-Total-Size")!!.toLong();val bytes=r.body!!.bytes();check(bytes.isNotEmpty()||total==0L);partial.appendBytes(bytes);offset+=bytes.size}}
  val digest=java.security.MessageDigest.getInstance("SHA-256");partial.inputStream().use{input->val b=ByteArray(1048576);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}};val hashActual=digest.digest().joinToString(""){"%02x".format(it)}
  if(hashActual!=hash){partial.delete();error("附件校验失败，请重试")};check(partial.renameTo(target))
 }
 suspend fun synchronize(onProgress:(String)->Unit):String=withContext(Dispatchers.IO){
  val url=settings.get("sync.url");check(url.isNotBlank()){ "请先与电脑配对" };val client=http(settings.get("sync.fp"));val token=settings.get("sync.token")
  onProgress("比对两端版本…")
  val manifest=request(client,url,"/manifest",token)["records"]!!.jsonArray.associate{it.jsonObject.s("id") to it.jsonObject.s("rev")}
  val local=store.dao.heads();val toPush=local.filter{manifest[it.id]!=it.rev}
  val changedDocuments=mutableSetOf<String>()
  var pushed=0
  for(heads in toPush.sortedBy{if(it.kind=="forget")0 else 1}.chunked(10)){
   val batch=heads.mapNotNull{store.get(it.id)}
   for(r in batch.filter{!it.deleted})for(hash in r.payload.strings("assetIds")){onProgress("上传附件 ${++pushed}…");upload(client,url,token,hash)}
   val result=request(client,url,"/records/push",token,buildJsonObject{put("records",JsonArray(batch.map{wire.encodeToJsonElement(it)}))})
   for(item in result["accepted"]!!.jsonArray){val r=wire.decodeFromJsonElement<SyncRecord>(item);if(r.kind=="document"&&r.rev!=store.get(r.id)?.rev)changedDocuments.add(r.id);store.acceptServer(r,batch.firstOrNull{it.id==r.id}?.rev?:"")}
   for(item in result["conflicts"]as? JsonArray?:JsonArray(emptyList())){
    val conflict=item.jsonObject;val remote=wire.decodeFromJsonElement<SyncRecord>(conflict["local"]!!);val current=store.get(remote.id)?:continue
    store.dao.conflict(ConflictRow(uid(),remote.id,wire.encodeToString(SyncRecord.serializer(),current),wire.encodeToString(SyncRecord.serializer(),remote),"电脑保留双方版本，等待在电脑解决"))
    store.acceptServer(remote,batch.firstOrNull{it.id==remote.id}?.rev?:"")
   }
  }
  val updated=request(client,url,"/manifest",token)["records"]!!.jsonArray
  val currentHeads=store.dao.heads().associate{it.id to it.rev};val needed=mutableListOf<String>();for(m in updated){val o=m.jsonObject;if(currentHeads[o.s("id")]!=o.s("rev"))needed.add(o.s("id"))}
  for(batch in needed.chunked(10)){
   onProgress("接收学习记录与资料…")
   val pulled=request(client,url,"/records/pull",token,buildJsonObject{put("ids",JsonArray(batch.map(::JsonPrimitive)))})["records"]!!.jsonArray.map{wire.decodeFromJsonElement<SyncRecord>(it)}
   for(r in pulled.sortedBy{if(it.kind=="forget")0 else 1}){if(!r.deleted)for(hash in r.payload.strings("assetIds"))download(client,url,token,hash);store.ingest(r);if(r.kind=="document"&&!r.deleted)changedDocuments.add(r.id)}
  }
  onProgress("电脑正在生成复盘文档…");request(client,url,"/complete",token,JsonObject(emptyMap()))
  for(doc in store.all("document"))if(!doc.payload.b("paused")&&(doc.payload.s("status")!="ready"||doc.id in changedDocuments)) (context.applicationContext as JietiApp).agents.queue("document",doc.id)
  val memory=(context.applicationContext as JietiApp).memory;memory.retireDeletedSources();for(m in store.all("mastery"))memory.refreshMastery(m.payload.s("scope"),m.payload.s("concept"))
  store.setSetting("lastSync",now());"同步完成，复盘文档已在电脑保存。冲突可在电脑设备页查看。"
 }
}
