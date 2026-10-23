package cn.jieti.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class TeachingStyleTest {
 @Test fun learnsEverydayRequestsWithoutRememberKeyword(){
  assertTrue("visualization" to "visual" in TeachingStyle.immediate("能不能先画个图，这里看不出来"))
  assertTrue("explanation_order" to "example_first" in TeachingStyle.immediate("先举个例子吧"))
  assertTrue("hint_granularity" to "stepwise" in TeachingStyle.immediate("这一步还没懂，慢一点"))
 }
 @Test fun doesNotTreatTaskOrQuotedMaterialAsHabit(){
  assertTrue(TeachingStyle.immediate("题目：请画个图并说明函数性质").isEmpty())
  assertTrue(TeachingStyle.immediate("教材：“先举个例子”，这一段是什么意思？").isEmpty())
  assertTrue(TeachingStyle.immediate("求定积分的值").isEmpty())
 }
 @Test fun strategyEvaluationUsesConversationEvidenceAndCooldown(){
  val now=Instant.parse("2026-10-23T12:00:00Z")
  assertTrue(TeachingStyle.shouldEvaluate(8,0,3,"",now))
  assertFalse(TeachingStyle.shouldEvaluate(20,0,1,"",now))
  assertFalse(TeachingStyle.shouldEvaluate(9,8,3,"",now))
  assertFalse(TeachingStyle.shouldEvaluate(20,0,3,"2026-10-23T11:00:00Z",now))
 }
}
