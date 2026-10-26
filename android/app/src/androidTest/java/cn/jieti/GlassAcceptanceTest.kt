package cn.jieti

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ui.LiquidGlassDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.pow

@RunWith(AndroidJUnit4::class) class GlassAcceptanceTest {
 @Test fun animatedRimMovesWithoutChangingReadingSurface(){
  assertTrue("Enable system animations for the rim acceptance",android.animation.ValueAnimator.areAnimatorsEnabled())
  var time=0L
  val drawable=LiquidGlassDrawable(1f,20f,clear=true,clock={time}).apply{setBounds(0,0,160,100);start()}
  val a=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);val b=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888)
  try{
   drawable.draw(Canvas(a));time=2250L;drawable.draw(Canvas(b))
   assertEquals("Only the rim should animate",a.getPixel(80,50),b.getPixel(80,50))
   var changed=0;for(y in 0 until 100)for(x in 0 until 160)if(a.getPixel(x,y)!=b.getPixel(x,y))changed++
   assertTrue("Rim highlight must travel",changed>20)
   drawable.stop();a.eraseColor(Color.TRANSPARENT);b.eraseColor(Color.TRANSPARENT)
   time=5000L;drawable.draw(Canvas(a));time=7000L;drawable.draw(Canvas(b))
   assertTrue("A stopped drawable must remain still",a.sameAs(b))
  }finally{drawable.stop();a.recycle();b.recycle()}
 }
 @Test fun glassCompositesCurrentBackgroundAtEveryFrostLevel(){
  // Replaces the obsolete frozen-screenshot lens contract: body AND edge must
  // react to a changed underlying surface even at maximum readability/frost.
  for(level in listOf(0f,.4f,1f)){
   val glass=LiquidGlassDrawable(1f,20f,clear=true,frost={level}).apply{setBounds(0,0,160,100)}
   val bitmap=Bitmap.createBitmap(160,100,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
   try{
    canvas.drawColor(Color.RED);glass.draw(canvas);val redCenter=bitmap.getPixel(80,50);val redEdge=bitmap.getPixel(6,50)
    canvas.drawColor(Color.BLUE);glass.draw(canvas);val blueCenter=bitmap.getPixel(80,50);val blueEdge=bitmap.getPixel(6,50)
    assertTrue("Current red surface must show through the body",Color.red(redCenter)>Color.blue(redCenter)+15)
    assertTrue("New blue surface must replace the old red body",Color.blue(blueCenter)>Color.red(blueCenter)+15)
    assertTrue("The rim must also show the current surface",Color.red(redEdge)>Color.blue(redEdge)&&Color.blue(blueEdge)>Color.red(blueEdge))
   }finally{bitmap.recycle()}
  }
 }
 @Test fun elasticOutlineDeformsAndRestoresWithoutChangingBounds(){
  val glass=LiquidGlassDrawable(2f,28f,clear=true,elastic=true).apply{setBounds(0,0,192,104)}
  val rest=Bitmap.createBitmap(192,104,Bitmap.Config.ARGB_8888);val moving=Bitmap.createBitmap(192,104,Bitmap.Config.ARGB_8888);val restored=Bitmap.createBitmap(192,104,Bitmap.Config.ARGB_8888)
  try{
   glass.draw(Canvas(rest));glass.deformation=cn.jieti.ui.BubbleDeformation(1f,.4f);glass.draw(Canvas(moving))
   assertFalse("Dragging must visibly taper the outline",rest.sameAs(moving));assertEquals(Rect(0,0,192,104),glass.bounds)
   var changed=0;for(y in 0 until 104)for(x in 0 until 192)if(rest.getPixel(x,y)!=moving.getPixel(x,y))changed++
   assertTrue("Expected visible fluid deformation",changed>100)
   glass.deformation=cn.jieti.ui.BubbleDeformation();glass.draw(Canvas(restored));assertTrue("Release must return to exactly the original silhouette",rest.sameAs(restored))
  }finally{rest.recycle();moving.recycle();restored.recycle()}
 }
 @Test fun ordinaryDragHasAnObviouslyTaperedTailAndNoClippedRim(){
  val glass=LiquidGlassDrawable(2f,28f,clear=true,elastic=true).apply{setBounds(0,0,192,104)}
  val image=Bitmap.createBitmap(192,104,Bitmap.Config.ARGB_8888)
  fun thickness(x:Int):Int{val ys=(0 until image.height).filter{Color.alpha(image.getPixel(x,it))>40};return if(ys.isEmpty())0 else ys.last()-ys.first()+1}
  try{
   glass.draw(Canvas(image));val restingTail=thickness(40)
   image.eraseColor(Color.TRANSPARENT)
   glass.deformation=cn.jieti.ui.BubbleSpring.drag(3.84f,0f,16,2f);glass.draw(Canvas(image))
   assertTrue("Slow drags must visibly narrow the trailing shoulder",restingTail-thickness(40)>=12)
   assertTrue("Leading shoulder must remain fuller than the tail",thickness(152)-thickness(40)>=10)
   for(direction in listOf(cn.jieti.ui.BubbleDeformation(1f,0f),cn.jieti.ui.BubbleDeformation(-1f,0f),cn.jieti.ui.BubbleDeformation(0f,1f),cn.jieti.ui.BubbleDeformation(0f,-1f),cn.jieti.ui.BubbleDeformation(.7f,.7f),cn.jieti.ui.BubbleDeformation(.7f,-.7f),cn.jieti.ui.BubbleDeformation(-.7f,.7f),cn.jieti.ui.BubbleDeformation(-.7f,-.7f))){
    image.eraseColor(Color.TRANSPARENT);glass.deformation=direction;glass.draw(Canvas(image))
    for(x in 0 until image.width){assertEquals("Top rim clipped",0,Color.alpha(image.getPixel(x,0)));assertEquals("Bottom rim clipped",0,Color.alpha(image.getPixel(x,image.height-1)))}
    for(y in 0 until image.height){assertEquals("Left rim clipped",0,Color.alpha(image.getPixel(0,y)));assertEquals("Right rim clipped",0,Color.alpha(image.getPixel(image.width-1,y)))}
   }
  }finally{image.recycle()}
 }
 @Test fun localSendSmokeHasNoApiKey(){
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as JietiApp
  assertTrue("An API key exists; do not run the offline send smoke.",app.settings.get("apiKey").isBlank())
 }
 @Test fun repeatedPaintKeepsOpacityAndButtonContrast(){
  fun luminance(c:Int):Double{fun linear(v:Int):Double{val s=v/255.0;return if(s<=.04045)s/12.92 else ((s+.055)/1.055).pow(2.4)};return .2126*linear(Color.red(c))+.7152*linear(Color.green(c))+.0722*linear(Color.blue(c))}
  for(primary in listOf(false,true)){
   val drawable=LiquidGlassDrawable(1f,22f,button=true,primary=primary).apply{setBounds(0,0,160,44)}
   val bitmap=Bitmap.createBitmap(160,44,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
   canvas.drawColor(Color.BLACK);drawable.draw(canvas);val first=bitmap.getPixel(80,22)
   canvas.drawColor(Color.BLACK);drawable.draw(canvas);val second=bitmap.getPixel(80,22)
   assertEquals("A previous rim must not lower the next body's alpha",first,second)
   val text=if(primary)0xFF263E29.toInt()else 0xFF354332.toInt()
   val contrast=(luminance(second)+.05)/(luminance(text)+.05)
   assertTrue("Button contrast $contrast is below 4.5",contrast>=4.5)
   bitmap.recycle()
  }
  val panel=LiquidGlassDrawable(1f).apply{setBounds(0,0,400,500)}
  val bitmap=Bitmap.createBitmap(400,500,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap)
  repeat(3){canvas.drawColor(Color.BLACK);panel.draw(canvas);val center=bitmap.getPixel(200,250);assertTrue(Color.red(center)>210&&Color.green(center)>210&&Color.blue(center)>210)}
  bitmap.recycle()
 }
}
