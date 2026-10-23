package cn.jieti.core

import cn.jieti.ai.ConversationContext
import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
 @Test fun retainsRecentExchangesAndNewestQuestion(){
  val rows=(0 until 101).map{Message(questionId="q",role=if(it%2==0)"user"else "assistant",content="message-$it")}
  val result=ConversationContext.recent(rows,maxMessages=40)
  assertEquals("user",result.first().role);assertEquals("message-100",result.last().content)
  assertTrue(result.size<=40);assertTrue(result.any{it.role=="assistant"})
 }
 @Test fun boundsContextButKeepsNewestLargeRequest(){
  val result=ConversationContext.recent(listOf(Message(role="user",content="old"),Message(role="assistant",content="x".repeat(300)),Message(role="user",content="z".repeat(200))),budget=100)
  assertEquals(1,result.size);assertEquals(100,result.single().content.length)
 }
 @Test fun includesImageIdentityAndPreservesOrder(){
  val original=Message(role="user",content="截图",assetIds=listOf("image"));val follow=Message(role="user",content="为什么？")
  assertEquals(listOf(original,follow),ConversationContext.recent(listOf(original,follow)))
 }
}
