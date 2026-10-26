package cn.jieti.ui

import android.graphics.*
import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.io.File

object SourceHighlight {
 fun draw(context:Context,file:File,page:Int,bitmap:Bitmap,excerpt:String):Boolean{
  if(excerpt.isBlank())return false
  com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
  return PDDocument.load(file,MemoryUsageSetting.setupTempFileOnly()).use{pdf->
   val chars=StringBuilder();val boxes=mutableListOf<RectF>()
   val stripper=object:PDFTextStripper(){override fun writeString(text:String,positions:MutableList<TextPosition>){for(p in positions)for(c in p.unicode.orEmpty())if(!c.isWhitespace()){chars.append(c);boxes.add(RectF(p.xDirAdj,p.yDirAdj-p.heightDir,p.xDirAdj+p.widthDirAdj,p.yDirAdj+2))}}}
   stripper.startPage=page;stripper.endPage=page;stripper.getText(pdf)
   val needle=excerpt.filterNot{it.isWhitespace()};var start=-1;var length=0
   // Use only an exact contiguous phrase actually present in this page.
   for(size in listOf(60,30,16,8)){if(start>=0)break;for(offset in 0..minOf(120,(needle.length-size).coerceAtLeast(0))){if(offset+size>needle.length)continue;val at=chars.indexOf(needle.substring(offset,offset+size));if(at>=0){start=at;length=size;break}}}
   if(start<0)return@use false
   val media=pdf.getPage(page-1).cropBox;val sx=bitmap.width/media.width;val sy=bitmap.height/media.height
   val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=0x568AA78E}
   for(box in boxes.subList(start,start+length))canvas.drawRect(box.left*sx,box.top*sy,box.right*sx,box.bottom*sy,paint)
   true
  }
 }
}
