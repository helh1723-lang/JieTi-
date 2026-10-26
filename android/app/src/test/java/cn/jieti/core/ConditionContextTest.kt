package cn.jieti.core
import cn.jieti.ai.StudyContext
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
class ConditionContextTest {
 @Test fun negatedCorrectionsAreNotStoredAsNewConditions(){assertEquals("",StudyContext.conditionChange("不要把 a=2 改成 a=3"));assertEquals("current",StudyContext.conditionChange("请更正参数为 a=3"));assertEquals("temporary",StudyContext.conditionChange("如果把 a 改成3呢"))}

 private fun q(stem:String,conditions:String)=SyncRecord("q","question","r",payload=buildJsonObject{put("transcription",stem);put("effectiveConditions",conditions)},updatedAt="2026-10-24",deviceId="test")
 @Test fun latestExplicitAssignmentsProduceOneCurrentStemWithoutMutatingOriginal(){val source=q("原题 a=2，b=5；求 a 的平方","参数改成 a=3\nb=7\na=4");assertEquals("原题 a=4，b=7；求 a 的平方",StudyContext.resolvedStem(source));assertEquals("原题 a=2，b=5；求 a 的平方",source.payload["transcription"]!!.jsonPrimitive.content)}
 @Test fun identifiersExpressionsAndUnparsedConditionsAreNotGuessed(){assertEquals("ba=2，a=2x；f=2.5；目标不变",StudyContext.resolvedStem(q("ba=2，a=2x；f=2.5；目标不变","a=3")));assertEquals("a=2，求极限",StudyContext.resolvedStem(q("a=2，求极限","改成右侧极限")));assertEquals("a=-0.5，求平方",StudyContext.resolvedStem(q("a=2，求平方","a=-0.5")))}
}
