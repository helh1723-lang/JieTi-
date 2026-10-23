package cn.jieti.core

import java.time.Instant

object TeachingStyle {
 val choices=mapOf(
  "response_length" to mapOf("concise" to "通常先简洁回答，按需展开。","detailed" to "通常需要较细的推导和解释。"),
  "hint_granularity" to mapOf("stepwise" to "倾向一次只推进一个关键步骤。","overview" to "倾向先看到整体思路，再逐步推导。"),
  "visualization" to mapOf("visual" to "图形、示意图和可视化通常更有帮助。","symbolic" to "倾向清楚的符号推导，减少不必要的图解。"),
  "explanation_order" to mapOf("example_first" to "通常先看具体例子，再归纳原理。","principle_first" to "通常先说明原理，再应用到例子。")
 )
 fun value(family:String,choice:String)=choices[family]?.get(choice)
 fun family(key:String)=key.substringBefore(":observed:")
 fun immediate(text:String):List<Pair<String,String>> {
  // Only conversational teaching requests; pasted tasks and quotations need review.
  if(text.length>220||Regex("题目[：:]|资料[：:]|教材[：:]|```|[“\"].*[”\"]").containsMatchIn(text))return emptyList()
  val found=mutableListOf<Pair<String,String>>()
  if(Regex("(能不能|可以|帮我|先|请|我想).{0,10}(画个图|画图解释|用图|画出来)|这样画.{0,6}(懂了|明白)|图.{0,4}更直观").containsMatchIn(text))found.add("visualization" to "visual")
  if(Regex("不用画图|别画图|不要图解|用公式推导就行").containsMatchIn(text))found.add("visualization" to "symbolic")
  if(Regex("(先|能不能|请|给我).{0,8}(举个例子|举一个例子|来个例子)|这样举例.{0,6}(懂了|明白)").containsMatchIn(text))found.add("explanation_order" to "example_first")
  if(Regex("先讲原理|先说原理|原理讲清楚再").containsMatchIn(text))found.add("explanation_order" to "principle_first")
  if(Regex("太啰嗦|简短一点|简洁一点|短一点说|不用展开").containsMatchIn(text))found.add("response_length" to "concise")
  if(Regex("详细一点|再详细点|过程展开|推导写全").containsMatchIn(text))found.add("response_length" to "detailed")
  if(Regex("一步一步|一次一步|先别往下|这一步还没懂|先只讲这步").containsMatchIn(text))found.add("hint_granularity" to "stepwise")
  if(Regex("先给整体思路|先看全貌|先说整体框架").containsMatchIn(text))found.add("hint_granularity" to "overview")
  return found.groupBy{it.first}.values.filter{it.size==1}.map{it.single()}
 }
 fun shouldEvaluate(turns:Int,lastTurns:Int,questionCount:Int,lastAt:String,at:Instant=Instant.now()):Boolean {
  val last=runCatching{Instant.parse(lastAt)}.getOrNull()
  return turns-lastTurns>=8&&questionCount>=3&&(last==null||at.epochSecond-last.epochSecond>=86400)
 }
}
