package cn.jieti.ui
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import cn.jieti.*
import cn.jieti.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SourceActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val app=application as JietiApp;val id=intent.getStringExtra("documentId")?:return;val initial=intent.getIntExtra("page",1)
  setContent{JietiTheme{var page by remember{mutableIntStateOf(initial)};var count by remember{mutableIntStateOf(1)};var title by remember{mutableStateOf("资料原页")};var text by remember{mutableStateOf("")};var bitmap by remember{mutableStateOf<Bitmap?>(null)}
   LaunchedEffect(id,page){withContext(Dispatchers.IO){runCatching{val doc=app.store.get(id)?.takeUnless{it.deleted}?:error("资料不存在或已删除");title=doc.payload.s("title");val file=app.store.asset(doc.payload.strings("assetIds").first());val mime=doc.payload.s("mime").lowercase();if(page==0){text=doc.payload.s("supplementalText");count=0}else if(mime.contains("pdf")){ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use{fd->PdfRenderer(fd).use{pdf->count=pdf.pageCount;pdf.openPage((page-1).coerceIn(0,count-1)).use{p->val b=Bitmap.createBitmap(1400,(p.height*1400f/p.width).toInt(),Bitmap.Config.ARGB_8888);b.eraseColor(android.graphics.Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);bitmap=b}}}}else if(mime.contains("image")||mime in listOf("png","jpg","jpeg"))bitmap=BitmapFactory.decodeFile(file.path)else text=if(mime.contains("docx"))cn.jieti.library.DocumentText.docx(file)else file.readText()}.onFailure{text=it.message?:"资料无法打开"}}}
   Column(Modifier.fillMaxSize().padding(20.dp)){Row{TextButton(onClick={finish()}){Text("‹ 返回")};Text(title,Modifier.weight(1f));Text("第 $page / $count 页")};Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){bitmap?.let{Image(it.asImageBitmap(),"资料原页",Modifier.fillMaxWidth())};if(text.isNotBlank())Text(text)};if(count>1)Row{TextButton(enabled=page>1,onClick={page--}){Text("上一页")};TextButton(enabled=page<count,onClick={page++}){Text("下一页")}}}
  }}
 }
}
