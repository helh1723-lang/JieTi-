package cn.jieti.core

import cn.jieti.ai.Curriculum
import cn.jieti.data.*
import org.junit.Assert.*
import org.junit.Test

class CurriculumProgressTest {
 private fun mastery(id:String,scope:String,concept:String,state:String,deleted:Boolean=false)=SyncRecord(id,"mastery","1",payload=payload(MasterySnapshot(scope,concept,state)),deleted=deleted,updatedAt=now(),deviceId="fixture")
 @Test fun aggregationPreservesUnknownsTombstonesAndConservativeAliasMerging(){
  val rows=listOf(mastery("a","signals","傅里叶变换","mastered"),mastery("b","signals","CTFT","needs_refresh"),mastery("c","signals","傅里叶级数","mastered",true),mastery("d","math","傅里叶级数","mastered"),mastery("e","signals","收敛域","mastered"),mastery("f","signals","新专题","unknown"))
  val progress=Curriculum.progress(rows,"signals")
  assertEquals("needs_refresh",progress.state("3.4"));assertEquals("diagnostic",progress.state("3.2"));assertEquals(0,progress.mastered);assertEquals(1,progress.refresh);assertEquals(52,progress.total)
  assertEquals(setOf("收敛域","新专题"),progress.unassigned.toSet())
 }
 @Test fun fullMathProfileAndRepeatedRebuildsHaveTheSameCounts(){
  val topics=Curriculum.chapters("math").flatMap{it.topics};val rows=topics.mapIndexed{i,t->mastery("m$i","math",t.title,if(i%3==0)"mastered"else if(i%3==1)"developing"else "needs_refresh")}
  val first=Curriculum.progress(rows,"math");assertEquals(157,first.total);assertEquals(53,first.mastered);assertEquals(52,first.refresh)
  repeat(5){assertEquals(first,Curriculum.progress(rows.shuffled(),"math"))}
 }
 @Test fun boundedMatcherCacheKeepsAmbiguityAndKnownAliasesCorrect(){
  repeat(600){assertNull(Curriculum.topicFor("signals","未知标签$it"))}
  assertNull(Curriculum.topicFor("signals","收敛域"));assertEquals("6.3",Curriculum.topicFor("signals","DTFT"));assertEquals("3.4",Curriculum.topicFor("signals","CTFT"))
 }
}
