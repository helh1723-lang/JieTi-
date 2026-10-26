package cn.jieti.core
import cn.jieti.ai.Curriculum
import cn.jieti.data.*
import org.junit.Assert.*
import org.junit.Test

class CurriculumTest {
 @Test fun mathUsesBoardChaptersAndCoversAllThreeTracks(){
  val chapters=Curriculum.chapters("math");assertEquals(23,chapters.size);assertEquals(157,chapters.sumOf{it.topics.size});assertEquals(listOf(9,6,8),listOf("高等数学","线性代数","概率论与数理统计").map{group->chapters.count{it.group==group}})
  for(concept in listOf("方向导数","三重积分","高斯公式","正定矩阵","区间估计","假设检验"))assertNotNull(Curriculum.topicFor("math",concept))
  assertTrue(chapters.all{it.startPage>0&&it.source.isNotBlank()&&it.topics.all{topic->topic.page==0}})
 }
 private fun mastery(id:String,concept:String,state:String,deleted:Boolean=false)=SyncRecord(id,"mastery","1",payload=payload(MasterySnapshot("signals",concept,state)),deleted=deleted,updatedAt=now(),deviceId="fixture")
 @Test fun allChaptersAndFormerlyExcludedSectionsRemain(){
  val chapters=Curriculum.chapters("signals");assertEquals(8,chapters.size);assertEquals(52,chapters.sumOf{it.topics.size})
  assertEquals("希尔伯特变换",chapters[4].topics.first{it.id=="4.5"}.title);assertEquals("outline",chapters[6].availability);assertTrue(chapters[6].topics.all{it.page==0});assertEquals(18,chapters[7].topics.first{it.id=="7.7"}.page)
 }
 @Test fun aliasesRespectScopeAndAmbiguity(){
  assertEquals("6.3",Curriculum.topicFor("signals","离散时间傅里叶变换"));assertEquals("3.4",Curriculum.topicFor("signals","傅里叶变换"));assertEquals("7.2",Curriculum.topicFor("signals","Z 变换收敛域"));assertNull(Curriculum.topicFor("signals","收敛域"));assertNull(Curriculum.topicFor("signals","全新专题"));assertNull(Curriculum.topicFor("math","傅里叶变换"));assertEquals("p1.3",Curriculum.topicFor("math","条件概率"))
 }
 @Test fun chapterStatesDoNotInventProgress(){
  val rows=listOf(mastery("a","傅里叶变换","mastered"),mastery("b","CTFT","needs_refresh"),mastery("c","傅里叶级数","mastered",true))
  assertEquals("diagnostic",Curriculum.state(emptyList(),"signals","3.4"));assertEquals("needs_refresh",Curriculum.state(rows,"signals","3.4"));assertEquals("diagnostic",Curriculum.state(rows,"signals","3.2"));assertEquals("needs_evidence",Curriculum.state(listOf(mastery("d","CTFT","unknown")),"signals","3.4"))
 }
}
