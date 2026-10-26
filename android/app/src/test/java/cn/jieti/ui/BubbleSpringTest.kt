package cn.jieti.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class BubbleSpringTest {
 @Test fun ordinarySlowDragIsVisibleAndReleaseHasOneElasticRebound(){
  // 120dp/s used to request only 0.13 of the already subtle deformation.
  val target=BubbleSpring.drag(3.84f,0f,16,2f)
  assertTrue("Slow tablet drags need visible feedback",target.x>.55f)
  val spring=BubbleSpring();repeat(6){spring.step(1f/60,target)}
  assertTrue("Feedback should be visible within 100ms",spring.value.x>.45f)
  repeat(24){spring.step(1f/60,target)}
  var minimum=0f;repeat(60){minimum=minOf(minimum,spring.step(1f/60).x)}
  assertTrue("Release must have a visible opposite rebound",minimum<-.07f)
  repeat(120){spring.step(1f/60)};assertTrue(spring.isAtRest())
 }
 @Test fun directionSpeedAndDensityControlTheEffect(){
  val horizontal=BubbleSpring.drag(24f,0f,16,2f)
  assertTrue(horizontal.x>0f);assertEquals(0f,horizontal.y,0f)
  val vertical=BubbleSpring.drag(0f,-24f,16,2f)
  assertTrue(vertical.y<0f);assertEquals(0f,vertical.x,0f)
  assertEquals(horizontal,BubbleSpring.drag(48f,0f,16,4f))
  assertTrue(BubbleSpring.drag(2f,0f,16,2f).x<horizontal.x)
 }
 @Test fun fastOrDuplicateEventsStayBounded(){
  for(dt in listOf(-1L,0L,1L,16L,500L)){
   val target=BubbleSpring.drag(10000f,-20000f,dt,2f)
   assertTrue(target.x.isFinite()&&target.y.isFinite());assertTrue(hypot(target.x,target.y)<=1.0001f)
  }
 }
 @Test fun stretchRelaxesExactlyAndCanBeInterrupted(){
  val spring=BubbleSpring();repeat(30){spring.step(1f/60,BubbleDeformation(1f,.25f))}
  assertTrue(spring.value.x>.8f);assertTrue(spring.value.y>.2f)
  repeat(6){spring.step(1f/60)};val releasing=spring.value
  repeat(10){spring.step(1f/60,BubbleDeformation(-1f,0f))}
  assertTrue(spring.value.x<releasing.x)
  repeat(180){spring.step(1f/60)}
  assertTrue(spring.isAtRest());assertEquals(BubbleDeformation(),spring.value)
 }
 @Test fun frameRatesAndLongPausesDoNotExplode(){
  val a=BubbleSpring();val b=BubbleSpring();val target=BubbleDeformation(.8f,-.4f)
  repeat(30){a.step(1f/60,target)};repeat(60){b.step(1f/120,target)}
  assertEquals(a.value.x,b.value.x,.015f);assertEquals(a.value.y,b.value.y,.015f)
  val paused=a.step(5f,target);assertTrue(paused.x.isFinite()&&paused.y.isFinite());assertTrue(hypot(paused.x,paused.y)<1.5f)
  a.reset();assertTrue(a.isAtRest())
 }
}
