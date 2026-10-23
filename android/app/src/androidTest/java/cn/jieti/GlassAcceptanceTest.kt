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
