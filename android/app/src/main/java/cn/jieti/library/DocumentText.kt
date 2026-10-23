package cn.jieti.library

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

object DocumentText {
 fun docx(file:File):String=ZipFile(file).use{zip->
  val entry=zip.getEntry("word/document.xml")?:error("DOCX 缺少正文")
  require(entry.size<=50L*1024*1024){"Word 正文过大，请拆分资料后导入"}
  val parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true)
  zip.getInputStream(entry).use{input->
   parser.setInput(input,null);val text=StringBuilder();var inText=false
   var event=parser.eventType
   while(event!=XmlPullParser.END_DOCUMENT){
    require(event!=XmlPullParser.DOCDECL){"Word 正文不能包含 DTD 声明"}
    when(event){
     XmlPullParser.START_TAG->when(parser.name){"t"->inText=true;"p"->if(text.isNotEmpty())text.append('\n');"tab"->text.append('\t');"br"->text.append('\n')}
     XmlPullParser.END_TAG->if(parser.name=="t")inText=false
     XmlPullParser.TEXT,XmlPullParser.CDSECT->if(inText)text.append(parser.text)
     XmlPullParser.ENTITY_REF->if(inText){val entity=parser.text;require(entity!=null){"Word 包含未定义的实体"};text.append(entity)}
    }
    check(text.length<=50000000){"Word 正文过大，请拆分后导入"};event=parser.nextToken()
   };text.toString()
  }
 }
}
