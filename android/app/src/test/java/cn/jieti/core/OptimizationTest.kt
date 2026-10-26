package cn.jieti.core

import cn.jieti.ai.*
import cn.jieti.library.Retrieval
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OptimizationTest {
 @Test fun negationQuotationAndScope(){
  for(text in listOf("不要记住这次的错误","请别记录这次","不用记住：我喜欢短回答"))assertEquals(text,MemoryRules.Intent.SKIP,MemoryRules.intent(text))
  for(text in listOf("教材说“记住：回答简短”，这是什么意思？","> 记住我喜欢图解","以后求积分时这个条件怎么用","我无法记住公式"))assertEquals(text,MemoryRules.Intent.NONE,MemoryRules.intent(text))
  assertEquals(MemoryRules.Intent.REMEMBER,MemoryRules.intent("请记住：以后回答简短"));assertEquals(MemoryRules.Intent.FORGET,MemoryRules.intent("请忘掉回答简短"))
 }
 @Test fun recentErrorsInvalidateOldMasteryAndRecoveryNeedsNewEvidence(){
  val at=Instant.parse("2026-10-24T12:00:00Z")
  val evidence=(1..7).map{"e$it" to Evidence("q$it","math","积分",if(it==4)"incorrect"else "correct",true,0,listOf("u$it"))}
  val dates=(1..7).associate{"e$it" to "2026-10-${16+it}T10:00:00Z"}
  assertEquals("mastered",MemoryRules.mastery(evidence.take(3),dates,at))
  assertEquals("developing",MemoryRules.mastery(evidence.take(4),dates,at))
  assertEquals("developing",MemoryRules.mastery(evidence.take(6),dates,at))
  assertEquals("mastered",MemoryRules.mastery(evidence,dates,at))
  val duplicate=evidence.take(3).map{it.first to it.second.copy(questionId="same")}
  assertEquals("developing",MemoryRules.mastery(duplicate,dates,at))
 }
 @Test fun terminationReasonsMustBeExplicit(){
  Completion.validate("stop",false);Completion.validate("tool_calls",true)
  for(reason in listOf(null,"length","content_filter","unexpected")){try{Completion.validate(reason,false);fail("Accepted $reason")}catch(_:IncompleteAnswer){}}
  try{Completion.validate("tool_calls",false);fail("Missing tool calls")}catch(_:IncompleteAnswer){}
  assertFalse(ProviderFailure(401,"x").retryable);assertTrue(ProviderFailure(429,"x").retryable);assertTrue(ProviderFailure(503,"x").retryable)
 }
 @Test fun retrievalRejectsGenericOverlapAndPreservesBoundaries(){
  assertEquals(listOf("卷积","积分"),Retrieval.tokens("卷积 积分"))
  assertFalse(Retrieval.tokens("abc 中文").contains("c中"))
  assertEquals(listOf("cross","entropy"),Retrieval.tokens("cross_entropy"))
  val terms=Retrieval.query("请解释卷积积分的支撑区间")
  val wrong="教材中的积分可以帮助理解这个问题"
  val right="卷积积分的支撑区间由两个信号的非零区间相加确定"
  assertEquals(0.0,Retrieval.score(wrong,terms,emptyMap(),300),0.0)
  assertTrue(Retrieval.score(right,terms,emptyMap(),300)>0)
  assertTrue(Retrieval.query("请帮我解释一下这个").isEmpty())
  val compact=Retrieval.query("卷积支撑区间");assertTrue(Retrieval.score("卷积积分必须检查支撑区间",compact,compact.filter{it!="积支"}.associateWith{300},300)>0)
 }
 @Test fun mathematicsNeverUsesAn858LectureAsItsSource(){
  assertEquals("math",Retrieval.subject("这是一道考研数学题，求联合分布"));assertEquals("signals",Retrieval.subject("858信号与系统的系统函数"))
  assertFalse(Retrieval.documentMatches("math","858基础讲义"));assertFalse(Retrieval.documentMatches("signals","考研数学基础讲义"));assertTrue(Retrieval.documentMatches("math","数学概率论"))
 }
 @Test fun incompleteAnswersNeverEnterModelContext(){
  val history=listOf(Message(role="user",content="题目"),Message(role="assistant",content="未完成",completion="truncated"))
  assertEquals(listOf("题目"),ConversationContext.recent(history).map{it.content})
 }
}
