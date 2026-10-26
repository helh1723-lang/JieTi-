package cn.jieti.core

import cn.jieti.ai.Curriculum
import cn.jieti.data.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CourseDataTest {
 private fun document(id:String="doc",scope:String="signals",title:String="第三章讲义.pdf",pages:Int=39,processed:Int=0,deleted:Boolean=false)=SyncRecord(id,"document","$processed",payload=buildJsonObject{put("scope",scope);put("title",title);put("pages",pages);put("processed",processed);put("status",if(processed==0)"queued"else "processing");put("quality","pending");put("error",""+processed)},deleted=deleted,updatedAt=now(),deviceId="test")
 @Test fun processingProgressDoesNotInvalidateLectureBindings(){
  assertEquals(courseDocuments(listOf(document())),courseDocuments(listOf(document(processed=30))))
  val index=CourseSources(courseDocuments(listOf(document())))
  assertEquals("doc" to 17,index.resolve("signals-3",17))
  assertNull(index.resolve("signals-3",40));assertNull(index.resolve("signals-3",0))
 }
 @Test fun linksUpdateForPageCountRenameScopeAndDeletion(){
  assertNull(CourseSources(courseDocuments(listOf(document(pages=10)))).resolve("signals-3",17))
  assertNull(CourseSources(courseDocuments(listOf(document(title="其他讲义.pdf")))).resolve("signals-3",17))
  assertNull(CourseSources(courseDocuments(listOf(document(scope="math")))).resolve("signals-3",17))
  assertNull(CourseSources(courseDocuments(listOf(document(deleted=true)))).resolve("signals-3",17))
 }
 @Test fun directLectureWinsButShortDirectFallsBackToCombinedAndOutlineStaysMissing(){
  val combined=document("combined",title="1-5章讲义.pdf",pages=136)
  val complete=CourseSources(courseDocuments(listOf(document(),combined)))
  assertEquals("doc" to 17,complete.resolve("signals-3",17))
  val short=CourseSources(courseDocuments(listOf(document(pages=10),combined)))
  assertEquals("combined" to 69,short.resolve("signals-3",17))
  assertNull(short.resolve("signals-6",1));assertNull(short.resolve("signals-7",1))
 }
 @Test fun projectedMasteryRetainsConservativeMergingAndBothCourses(){
  val entries=listOf(Curriculum.MasteryEntry("signals","CTFT","mastered"),Curriculum.MasteryEntry("signals","傅里叶变换","needs_refresh"),Curriculum.MasteryEntry("math","数列极限","developing"),Curriculum.MasteryEntry("signals","未知标签","mastered"))
  assertEquals("needs_refresh",Curriculum.progressFromMastery(entries,"signals").state("3.4"))
  assertEquals(listOf("未知标签"),Curriculum.progressFromMastery(entries,"signals").unassigned)
  assertEquals(157,Curriculum.progressFromMastery(entries,"math").total)
  assertEquals(52,Curriculum.progressFromMastery(entries,"signals").total)
 }
}
