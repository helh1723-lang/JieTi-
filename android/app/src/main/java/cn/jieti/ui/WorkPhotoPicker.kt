package cn.jieti.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cn.jieti.JietiApp
import kotlinx.coroutines.*

@Composable fun WorkPhotoPicker(app:JietiApp,id:String,photos:List<String>,enabled:Boolean,run:(suspend()->Unit)->Unit,onPhotos:(List<String>)->Unit,onImporting:(Boolean)->Unit){
 val drafts=remember{WorkPhotos(app)};var error by remember{mutableStateOf("")};var importing by remember{mutableStateOf(false)}
 fun add(uris:List<android.net.Uri>){if(uris.isEmpty())return;val previous=photos;importing=true;onImporting(true);run{try{val hashes=uris.take((6-previous.size).coerceAtLeast(0)).map{uri->app.contentResolver.openInputStream(uri)?.use{drafts.importImage(it)}?:error("照片无法读取")};val next=(previous+hashes).distinct().take(6);drafts.save(id,next);withContext(Dispatchers.Main){onPhotos(next);error=""}}catch(e:CancellationException){throw e}catch(e:Exception){withContext(Dispatchers.Main){error=e.message?:"照片导入失败"}}finally{withContext(Dispatchers.Main){importing=false;onImporting(false)}}}}
 val gallery=rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()){add(it)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->val file=drafts.pending(id);if(ok&&file?.exists()==true){importing=true;onImporting(true);val previous=photos;run{try{val hash=file.inputStream().use{drafts.importImage(it)};val next=(previous+hash).distinct().take(6);drafts.save(id,next);withContext(Dispatchers.Main){onPhotos(next);error=""}}catch(e:CancellationException){throw e}catch(e:Exception){withContext(Dispatchers.Main){error=e.message?:"照片导入失败"}}finally{drafts.finishCamera(id);withContext(Dispatchers.Main){importing=false;onImporting(false)}}}}else drafts.finishCamera(id)}
 val takePhoto={try{val file=drafts.cameraFile(id);camera.launch(FileProvider.getUriForFile(app,app.packageName+".files",file))}catch(_:Exception){drafts.finishCamera(id);error="未找到可用相机，可从相册上传"}}
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)takePhoto()else{error="未获得相机权限，可从相册上传"}}
 Column{
  Row{TextButton(enabled=enabled&&!importing&&photos.size<6,onClick={if(ContextCompat.checkSelfPermission(app,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)takePhoto()else permission.launch(Manifest.permission.CAMERA)}){Text("拍照作答")};TextButton(enabled=enabled&&!importing&&photos.size<6,onClick={gallery.launch("image/*")}){Text("相册上传")};if(importing)Text("正在读取照片…",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=14.dp));if(photos.isNotEmpty())Text("${photos.size} / 6 张",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=14.dp))}
  if(photos.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)){for((index,hash)in photos.withIndex())Row{var bitmap by remember(hash){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(hash){bitmap=withContext(Dispatchers.IO){BitmapFactory.decodeFile(app.store.asset(hash).path,BitmapFactory.Options().apply{inSampleSize=8})}};val image=bitmap;if(image!=null)Image(image.asImageBitmap(),"手写作答第 ${index+1} 张",Modifier.size(56.dp).clickable{app.startActivity(Intent(app,FocusActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("assetId",hash))},contentScale=ContentScale.Fit);TextButton(enabled=enabled&&!importing,onClick={val next=photos.filter{it!=hash};drafts.save(id,next);onPhotos(next)}){Text("移除 ${index+1}")}}}
  if(error.isNotBlank())Text(error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
 }
}
