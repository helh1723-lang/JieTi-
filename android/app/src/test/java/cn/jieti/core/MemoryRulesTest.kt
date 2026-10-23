package cn.jieti.core
import cn.jieti.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MemoryRulesTest{
 @Test fun oneObservationDoesNotPromoteAndDuplicateDoesNotCount(){assertFalse(MemoryRules.promotesPreference(listOf("a")));assertFalse(MemoryRules.promotesPreference(listOf("a","a")));assertTrue(MemoryRules.promotesPreference(listOf("a","b")))}
 @Test fun masteryNeedsThreeUnaidedAttemptsAcrossTwoDays(){val at=Instant.parse("2026-10-22T12:00:00Z");val e=(1..3).map{"e$it" to Evidence("q$it","math","极限","correct",true,0,listOf("u$it"))};val dates=mapOf("e1" to "2026-10-20T10:00:00Z","e2" to "2026-10-21T10:00:00Z","e3" to "2026-10-22T10:00:00Z");assertEquals("mastered",MemoryRules.mastery(e,dates,at));assertEquals("developing",MemoryRules.mastery(e.take(2),dates,at));assertEquals("needs_evidence",MemoryRules.mastery(e.map{it.first to it.second.copy(hintLevel=1)},dates,at));assertEquals("needs_refresh",MemoryRules.mastery(e,dates,Instant.parse("2026-12-22T12:00:00Z")))}
 @Test fun oneErrorIsEvidenceNotPermanentInability(){val e=listOf("e" to Evidence("q","math","积分","incorrect",true,0,listOf("u")));assertEquals("needs_evidence",MemoryRules.mastery(e,mapOf("e" to "2026-10-22T10:00:00Z"),Instant.parse("2026-10-22T12:00:00Z")))}
 @Test fun recallIsScopedBoundedAndExplicitFirst(){
  fun m(id:String,scope:String,value:String,origin:String="inferred",status:String="active"):SyncRecord {
   return SyncRecord(id,"memory",id,"",payload(MemoryItem("preference",scope,id,value,listOf(id),origin,1.0,now(),status,false)),false,now(),"test")
  }
  val records=listOf(m("math","math","卷积需要图解"),m("signals","signals","卷积先找重叠区间"),m("global","global","简短提示","explicit"),m("paused","signals","暂停内容",status="paused"))
  val out=MemoryRules.recall(records,"signals","卷积",700)
  assertEquals("global",out.first().id)
  assertTrue(out.none{it.id=="math"||it.id=="paused"})
  assertTrue(out.sumOf{MemoryRules.context(it).toString().toByteArray(Charsets.UTF_8).size}<=700)
 }
}
