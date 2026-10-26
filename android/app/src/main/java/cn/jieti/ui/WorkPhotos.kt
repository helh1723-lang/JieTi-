package cn.jieti.ui
import android.graphics.ImageDecoder
import cn.jieti.JietiApp
import kotlinx.serialization.json.*
import cn.jieti.data.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Local drafts reference content-addressed images; submitted messages own synced copies. */
class WorkPhotos(private val app:JietiApp){
 private val prefs=app.getSharedPreferences("daily-work-photos",0)
 fun hasDraft(id:String)=prefs.contains(id)
 fun read(id:String):List<String> = runCatching{wire.parseToJsonElement(prefs.getString(id,"[]")!!).jsonArray.map{it.jsonPrimitive.content}.filter{Regex("[a-f0-9]{64}").matches(it)}}.getOrDefault(emptyList())
 fun save(id:String,hashes:List<String>){prefs.edit().putString(id,JsonArray(hashes.take(6).map(::JsonPrimitive)).toString()).apply()}
 fun cameraFile(id:String):File{val dir=File(app.cacheDir,"work-photos").apply{mkdirs()};return File.createTempFile("answer-",".jpg",dir).also{prefs.edit().putString("camera:$id",it.name).apply()}}
 fun pending(id:String):File?{val name=prefs.getString("camera:$id",null)?:return null;if(!Regex("answer-[a-zA-Z0-9]+\\.jpg").matches(name))return null;return File(File(app.cacheDir,"work-photos"),name)}
 fun finishCamera(id:String){pending(id)?.delete();prefs.edit().remove("camera:$id").apply()}
 fun importImage(input:java.io.InputStream):String{
  val bytes=input.use{stream->val out=ByteArrayOutputStream();val buffer=ByteArray(16384);while(true){val n=stream.read(buffer);if(n<0)break;out.write(buffer,0,n);require(out.size()<=25*1024*1024){"单张照片请小于25MB"}};out.toByteArray()}
  val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))){decoder,info,_->require(info.size.width>0&&info.size.height>0){"图片无效"};decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE;decoder.setTargetSampleSize(kotlin.math.ceil(maxOf(info.size.width,info.size.height)/3200.0).toInt().coerceAtLeast(1))}
  try{return app.store.addAsset(ByteArrayOutputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,92,it);it.toByteArray()})}finally{bitmap.recycle()}
 }
}
