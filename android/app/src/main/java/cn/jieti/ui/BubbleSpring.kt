package cn.jieti.ui

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

data class BubbleDeformation(val x:Float=0f,val y:Float=0f)

/** Visual feedback only. Window position and touch bounds are never animated here. */
class BubbleSpring {
 var value=BubbleDeformation();private set
 private var vx=0f;private var vy=0f
 fun reset(){value=BubbleDeformation();vx=0f;vy=0f}
 fun step(seconds:Float,target:BubbleDeformation=BubbleDeformation()):BubbleDeformation {
  var left=seconds.coerceIn(0f,.05f)
  var x=value.x;var y=value.y
  while(left>0f){val dt=minOf(left,1f/120f);left-=dt
   vx+=((target.x-x)*320f-vx*16f)*dt;vy+=((target.y-y)*320f-vy*16f)*dt
   x=(x+vx*dt).coerceIn(-1.1f,1.1f);y=(y+vy*dt).coerceIn(-1.1f,1.1f)
  }
  if(target==BubbleDeformation()&&abs(x)+abs(y)+abs(vx)+abs(vy)<.002f){reset();return value}
  value=BubbleDeformation(x,y);return value
 }
 fun isAtRest()=value==BubbleDeformation()&&vx==0f&&vy==0f
 companion object {
  fun drag(deltaX:Float,deltaY:Float,milliseconds:Long,density:Float):BubbleDeformation {
   val dt=milliseconds.coerceIn(8,80)/1000f
   val x=deltaX/max(.1f,density)/dt;val y=deltaY/max(.1f,density)/dt
   val speed=hypot(x,y);if(speed==0f)return BubbleDeformation()
   // Tablet drags are often slow. A perceptual response keeps those visible,
   // while direction and density remain independent of sampling frequency.
   val amount=(speed/300f).coerceIn(0f,1f).pow(.55f)
   return BubbleDeformation(x/speed*amount,y/speed*amount)
  }
 }
}
