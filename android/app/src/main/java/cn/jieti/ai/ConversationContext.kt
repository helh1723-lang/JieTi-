package cn.jieti.ai

import cn.jieti.core.Message

object ConversationContext {
 // Keep complete recent exchanges. The question snapshot and its original images
 // are supplied separately, so a long conversation never evicts the original task.
 fun recent(history:List<Message>,budget:Int=48000,maxMessages:Int=40):List<Message>{
  val selected=mutableListOf<Message>();var used=0
  for(m in history.filter{it.completion in listOf("","complete")}.asReversed()){
   if(selected.size>=maxMessages||selected.isNotEmpty()&&used+m.content.length>budget)break
   selected.add(if(selected.isEmpty()&&m.content.length>budget)m.copy(content=m.content.take(budget))else m);used+=m.content.length
  }
  val result=selected.asReversed().toMutableList()
  while(result.firstOrNull()?.role=="assistant")result.removeAt(0)
  return result
 }
}
