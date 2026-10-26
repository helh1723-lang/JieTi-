package cn.jieti.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View

/** Frame-driven elastic outline; pauses/ends without retaining stale callbacks. */
class BubbleDragFeedback(private val view:View,private val glass:LiquidGlassDrawable):Runnable {
 private val spring=BubbleSpring()
 private var target=BubbleDeformation();private var dragging=false;private var scheduled=false
 private var lastInput=0L;private var lastFrame=0L
 init{view.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener{
  override fun onViewAttachedToWindow(v:View){}
  override fun onViewDetachedFromWindow(v:View){reset()}
 })}
 fun begin(){view.removeCallbacks(this);scheduled=false;lastFrame=0;target=BubbleDeformation();dragging=true;if(!spring.isAtRest())schedule()}
 fun move(dx:Float,dy:Float,elapsed:Long){
  if(!ValueAnimator.areAnimatorsEnabled()){reset();return}
  dragging=true;lastInput=SystemClock.uptimeMillis();target=BubbleSpring.drag(dx,dy,elapsed,view.resources.displayMetrics.density);schedule()
 }
 fun release(){dragging=false;target=BubbleDeformation();if(ValueAnimator.areAnimatorsEnabled())schedule()else reset()}
 fun reset(){view.removeCallbacks(this);scheduled=false;dragging=false;lastFrame=0;target=BubbleDeformation();spring.reset();glass.deformation=spring.value;view.invalidateOutline()}
 private fun schedule(){if(!scheduled&&view.isAttachedToWindow){scheduled=true;view.postOnAnimation(this)}}
 override fun run(){
  scheduled=false;if(!view.isAttachedToWindow||!ValueAnimator.areAnimatorsEnabled()){reset();return}
  val now=SystemClock.uptimeMillis();val dt=if(lastFrame==0L)1f/60f else (now-lastFrame)/1000f;lastFrame=now
  // A stationary finger lets the outline relax instead of leaving it stretched.
  val age=now-lastInput
  val persistence=if(dragging)(1f-(age-90).coerceAtLeast(0)/180f).coerceIn(0f,1f)else 0f
  val aim=BubbleDeformation(target.x*persistence,target.y*persistence)
  glass.deformation=spring.step(dt,aim);view.invalidateOutline()
  if(!spring.isAtRest()||aim!=BubbleDeformation())schedule()else lastFrame=0
 }
}
