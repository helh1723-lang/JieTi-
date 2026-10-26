package cn.jieti.library

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/** Preserve Word math structure, tables and chart data; never silently flatten OMML. */
object DocumentText {
 data class Part(val name:String)
 data class Extracted(val text:String,val images:List<Part>,val issues:List<String>)
 private data class Node(val name:String,val ns:String,val attrs:Map<String,String>,val children:MutableList<Node> = mutableListOf(),var text:String=""){
  fun child(n:String)=children.firstOrNull{it.name==n}
  fun attr(n:String)=attrs[n].orEmpty()
 }
 private fun parse(xml:java.io.InputStream):Node{
  val parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(xml,null)
  val root=Node("root","",emptyMap());val stack=mutableListOf(root);var event=parser.eventType;var size=0
  while(event!=XmlPullParser.END_DOCUMENT){require(event!=XmlPullParser.DOCDECL){"Word 正文不能包含 DTD"}
   when(event){XmlPullParser.START_TAG->{require(stack.size<200){"Word 结构过深"};val n=Node(parser.name,parser.namespace.orEmpty(),(0 until parser.attributeCount).associate{parser.getAttributeName(it) to parser.getAttributeValue(it)});stack.last().children.add(n);stack.add(n)};XmlPullParser.END_TAG->stack.removeAt(stack.lastIndex);XmlPullParser.TEXT,XmlPullParser.CDSECT->{size+=parser.text.length;require(size<50000000){"Word 正文过大"};stack.last().text+=parser.text};XmlPullParser.ENTITY_REF->{val t=parser.text?:error("Word 包含未定义实体");stack.last().text+=t}}
   event=parser.nextToken()
  };return root
 }
 private fun math(n:Node,issues:MutableList<String>):String{
  fun c(k:String)=n.child(k)?.let{math(it,issues)}.orEmpty()
  fun all()=n.children.joinToString(""){math(it,issues)}
  return when(n.name){
   "t"->n.text.replace("−","-").replace("×","\\times ").replace("∞","\\infty ").replace("∑","\\sum ").replace("∫","\\int ")
   "f"->"\\frac{${c("num")}}{${c("den")}}"
   "sPre"->"{}_{${c("sub")}}^{${c("sup")}}{${c("e")}}"
   "sSup"->"{${c("e")}}^{${c("sup")}}";"sSub"->"{${c("e")}}_{${c("sub")}}";"sSubSup"->"{${c("e")}}_{${c("sub")}}^{${c("sup")}}"
   "rad"->if(c("deg").isBlank())"\\sqrt{${c("e")}}"else "\\sqrt[${c("deg") }]{${c("e")}}"
   "m"->"\\begin{matrix}"+n.children.filter{it.name=="mr"}.joinToString(" \\\\ "){row->row.children.filter{it.name=="e"}.joinToString(" & "){math(it,issues)}}+"\\end{matrix}"
   "eqArr"->"\\begin{aligned}"+n.children.filter{it.name=="e"}.joinToString(" \\\\ "){math(it,issues)}+"\\end{aligned}"
   "nary"->{val symbol=n.child("naryPr")?.child("chr")?.attr("val")?:"∫";val op=mapOf("∫" to "\\int","∑" to "\\sum","∏" to "\\prod","∮" to "\\oint")[symbol]?:symbol;op+"_{${c("sub")}}^{${c("sup")}} ${c("e")}"}
   "d"->{val pr=n.child("dPr");val left=pr?.child("begChr")?.attr("val")?:"(";val right=pr?.child("endChr")?.attr("val")?:")";left+n.children.filter{it.name=="e"}.joinToString(","){math(it,issues)}+right}
   "limLow"->"{${c("e")}}_{${c("lim")}}";"limUpp"->"{${c("e")}}^{${c("lim")}}"
   "func"->c("fName")+" "+c("e");"bar"->if(n.child("barPr")?.child("pos")?.attr("val")=="bot")"\\underline{${c("e")}}"else "\\overline{${c("e")}}"
   "acc"->{val symbol=n.child("accPr")?.child("chr")?.attr("val").orEmpty();val command=mapOf("̂" to "hat","̅" to "bar","̇" to "dot","̈" to "ddot","⃗" to "vec","̃" to "tilde")[symbol];if(command==null){issues.add("公式重音 $symbol 需对照原文件确认");"[待核对重音]"+c("e")}else "\\$command{${c("e")}}"}
   "groupChr","borderBox","box"->{issues.add("公式含 ${n.name}，请对照原文件确认符号");c("e")}
   else->if(n.name.endsWith("Pr"))""else{if(n.name !in listOf("oMath","oMathPara","r","e","num","den","sub","sup","deg","fName","lim","mr"))issues.add("公式结构 ${n.name} 需对照原文件确认");all()}
  }
 }
 private fun word(n:Node,issues:MutableList<String>):String{
  if(n.name=="oMath")return "\\("+math(n,issues)+"\\)"
  if(n.name=="tbl"){val rows=n.children.filter{it.name=="tr"}.map{r->r.children.filter{it.name=="tc"}.map{word(it,issues).trim().replace("\n"," ").replace("|","\\|")}};val width=rows.maxOfOrNull{it.size}?:0;return "\n"+rows.mapIndexed{i,row->"| "+(0 until width).joinToString(" | "){row.getOrElse(it){""}}+" |"+if(i==0)"\n|"+" --- |".repeat(width)else ""}.joinToString("\n")+"\n"}
  if(n.name in listOf("drawing","pict"))return " [内嵌图像，见图片识别分节] "
  if(n.name=="t")return n.text
  if(n.name=="tab")return "\t"
  if(n.name in listOf("br","cr"))return "\n"
  return n.children.joinToString(""){word(it,issues)}+if(n.name in listOf("p","oMathPara"))"\n"else ""
 }
 private fun chartValues(n:Node):String=if(n.name=="v"||n.name=="t")n.text else n.children.joinToString(" "){chartValues(it)}
 fun extract(file:File):Extracted=ZipFile(file).use{zip->
  val entry=zip.getEntry("word/document.xml")?:error("DOCX 缺少正文");require(entry.size<=50L*1024*1024){"Word 正文过大"}
  val issues=mutableListOf<String>();val text=zip.getInputStream(entry).use{word(parse(it),issues)};val images=mutableListOf<Part>();val charts=StringBuilder()
  for(e in zip.entries().asSequence()){
   if(e.name.startsWith("word/media/")&&!e.isDirectory){require(e.size in 0..25L*1024*1024){"Word 内嵌图片过大"};if(e.name.substringAfterLast('.').lowercase() in listOf("png","jpg","jpeg","webp"))images.add(Part(e.name))else issues.add("${e.name.substringAfterLast('/')} 格式需对照原文件确认")}
   if(e.name.startsWith("word/charts/chart")&&e.name.endsWith(".xml")){require(e.size<=10L*1024*1024);val node=zip.getInputStream(e).use{parse(it)};charts.append("\n图表数据（需结合原图核对坐标轴、单位与图例）： "+chartValues(node));issues.add("图表 ${e.name.substringAfterLast('/')} 已提取数据，图形含义需对照原文件确认")}
  }
  Extracted(text+charts,images,issues.distinct())
 }
 fun image(file:File,part:Part):ByteArray=ZipFile(file).use{zip->val entry=zip.getEntry(part.name)?:error("Word 内嵌图像丢失");require(entry.size in 0..25L*1024*1024){"Word 内嵌图像过大"};zip.getInputStream(entry).use{input->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=25*1024*1024){"Word 内嵌图像过大"};out.write(buffer,0,n)};out.toByteArray()}}
 fun docx(file:File)=extract(file).text
}
