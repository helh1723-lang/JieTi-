package cn.jieti.library

import kotlin.math.ln

/** Unicode word boundaries matter: never create a token across spaces or languages. */
object Retrieval {
 private val noise=Regex("请问|请帮我|帮我|能不能|可不可以|请|解释一下|解释|讲解|说明|为什么|怎么|如何|什么是|是什么|这道题|这题|这个|一下|教材|资料|根据|关于|中的|可以|需要|帮助|理解|截图|当前|教学|模式|完整|回答|只回复|只写结果")
 fun tokens(text:String):List<String> = Regex("[a-z][a-z0-9]*|[\\p{IsHan}]+|[0-9]+(?:\\.[0-9]+)?").findAll(text.lowercase()).flatMap { m ->
  val word=m.value
  if(word.first() in 'a'..'z') sequenceOf(word) else if(word.first().isDigit()) emptySequence() else word.windowed(2).asSequence()
 }.distinct().toList()
 fun query(text:String)=tokens(noise.replace(text.lowercase()," ")).filter{it.length>=2}.take(32)
 fun subject(text:String):String? {
  val math=Regex("考研数学|数学|数一|数学题|高等数学|线性代数|概率论|求极限|不定积分|多重积分|数列|特征值|概率密度|联合分布|偏导").containsMatchIn(text)
  val signals=Regex("858|信号与系统|信号系统|傅里叶变换|拉普拉斯变换|频率响应|冲激响应|系统函数").containsMatchIn(text)
  return when{math&&!signals->"math";signals&&!math->"signals";else->null}
 }
 fun documentMatches(scope:String,title:String)=subject(title).let{it==null||it==scope||scope !in listOf("math","signals")}
 fun score(text:String,terms:List<String>,frequency:Map<String,Int>,count:Int):Double {
  if(terms.isEmpty())return 0.0
  val words=tokens(text).toSet()
  fun weight(t:String)=ln(1.0+(count+1.0)/((frequency[t]?:0)+1.0))
  val matched=terms.filter{it in words}
  // Unobserved cross-word bigrams must not outweigh all actually matched terms.
  val coverage=matched.size.toDouble()/terms.size
  // Common question words or an isolated shared bigram are not textbook evidence.
  if(coverage<.48||matched.size<minOf(2,terms.size))return 0.0
  val lengthPenalty=1.0+ln(1.0+words.size/100.0)
  return coverage*matched.sumOf(::weight)/lengthPenalty
 }
}
