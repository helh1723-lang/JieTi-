package cn.jieti.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import cn.jieti.ai.Provider
import cn.jieti.core.*
import cn.jieti.data.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import io.objectbox.annotation.*
import kotlinx.coroutines.*
import androidx.room.withTransaction
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

// Retain the entity for existing installations and vector-index compatibility.
@Entity data class VectorChunk(@Id var id:Long=0,@Unique var chunkId:String="",var scope:String="",var model:String="",@HnswIndex(dimensions=1024,distanceType=VectorDistanceType.COSINE) var vector:FloatArray?=null)
class Library(private val context:Context,private val store:LearningStore,private val provider:Provider,private val settings:SecureSettings,private val foreground:AtomicInteger){
 private suspend fun idle(){while(foreground.get()>0){currentCoroutineContext().ensureActive();delay(150)}}
 private suspend fun current(id:String):Document?=store.get(id)?.takeUnless{it.deleted}?.value<Document>()?.takeUnless{it.paused}
 suspend fun process(id:String):Boolean=withContext(Dispatchers.IO){
  var doc=current(id)?:return@withContext true
  val started=System.currentTimeMillis()
  try{
   val file=store.asset(doc.assetIds.first());check(file.exists()){ "资料原文件尚未到达，请先同步" }
   store.edit(id,buildJsonObject{put("status","processing");put("error","")})
   index(id,0,doc.supplementalText)
   if(doc.mime.lowercase().contains("pdf")){
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
    PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly()).use{pdf->
     store.edit(id,buildJsonObject{put("pages",pdf.numberOfPages)})
     val stripper=PDFTextStripper()
     for(page in doc.processed until pdf.numberOfPages){
      currentCoroutineContext().ensureActive();idle();doc=current(id)?:return@withContext true
      if(System.currentTimeMillis()-started>210000){store.edit(id,buildJsonObject{put("status","queued")});return@withContext false}
      stripper.startPage=page+1;stripper.endPage=page+1
      var text=stripper.getText(pdf).trim()
      val pdfPage=pdf.getPage(page);val hasImages=pdfPage.resources?.xObjectNames?.any()==true
      if((text.isBlank()&&pdfPage.hasContents())||(text.length<30&&hasImages)||text.count{it=='\uFFFD'}>text.length/20){
       store.edit(id,buildJsonObject{put("error","第 ${page+1} 页需要识别，已完成的页面可先检索")})
       val bytes=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{render(it,page)}}
       idle();text=recognize(bytes)
      }
      if(!index(id,page+1,text))return@withContext true
      store.edit(id,buildJsonObject{put("processed",page+1);put("error","")})
     }
    }
   }else if(doc.processed==0){
    idle();val mime=doc.mime.lowercase()
    val text=when{mime.contains("docx")->DocumentText.docx(file);mime.contains("image")||mime in listOf("png","jpg","jpeg","webp")->recognize(file.readBytes());else->file.readText().removePrefix("\uFEFF")}
    if(!index(id,1,text))return@withContext true
    store.edit(id,buildJsonObject{put("processed",1);put("pages",1)})
   }
   if(current(id)!=null)store.edit(id,buildJsonObject{put("status","ready");put("error","")});true
  }catch(e:CancellationException){throw e}catch(e:Exception){
   if(current(id)!=null)store.edit(id,buildJsonObject{put("status","failed");put("error",e.message?.take(200)?:"资料处理失败")});throw e
  }
 }
 private fun render(renderer:PdfRenderer,page:Int):ByteArray {val p=renderer.openPage(page);try{val scale=minOf(1600f/p.width,2200f/p.height);val b=Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);return ByteArrayOutputStream().use{b.compress(Bitmap.CompressFormat.JPEG,85,it);b.recycle();it.toByteArray()}}finally{p.close()}}
 private suspend fun recognize(bytes:ByteArray):String{
  val mime=if(bytes.firstOrNull()==0x89.toByte())"png"else "jpeg"
  val content=buildJsonArray{add(buildJsonObject{put("type","text");put("text","逐字转写这页课程资料，公式保留LaTeX，图形用清晰文字描述。无法辨认处标记[无法辨认]，不要补全。")});add(buildJsonObject{put("type","image_url");putJsonObject("image_url"){put("url","data:image/$mime;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP))}})}
  return provider.chat("archive",buildJsonArray{add(buildJsonObject{put("role","user");put("content",content)})},stream=false,priority=false).text
 }

 private suspend fun index(documentId:String,page:Int,text:String):Boolean=store.db.withTransaction{
  val doc=current(documentId)?:return@withTransaction false
  store.dao.deletePage(documentId,page)
  text.windowed(1400,1200,partialWindows=true).forEachIndexed{n,t->store.dao.chunk(ChunkRow("$documentId:$page:$n",documentId,doc.scope,page,t,tokenize(t)))};true
 }
 suspend fun search(scope:String,query:String):List<Citation>{
  val tokens=tokenize(query).split(' ').filter{it.isNotBlank()}.take(48);if(tokens.isEmpty())return emptyList()
  val lexical=store.dao.search(scope,tokens.joinToString(" OR "){"\"$it\""})
  val ranked=lexical.sortedByDescending{c->val text=tokenize(c.text).split(' ').toSet();tokens.count{it in text}+(if(c.page==0)2 else 0)}
  val available=mutableListOf<Citation>();val perDocument=mutableMapOf<String,Int>()
  for(c in ranked){val count=perDocument[c.documentId]?:0;if(count>=3)continue;val doc=store.get(c.documentId);if(doc!=null&&!doc.deleted&&doc.payload.s("scope")==scope){available.add(Citation(c.documentId,c.page,c.text));perDocument[c.documentId]=count+1};if(available.size>=8)break};return available
 }
 companion object {fun tokenize(text:String):String {val chars=text.lowercase().filter{it.isLetterOrDigit()};return (Regex("[a-z0-9]+").findAll(text.lowercase()).map{it.value}.toList()+(if(chars.length<2)listOf(chars)else chars.windowed(2))).filter{it.isNotBlank()}.distinct().joinToString(" ")}}
}
