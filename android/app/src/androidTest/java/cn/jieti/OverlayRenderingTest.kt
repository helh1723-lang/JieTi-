package cn.jieti

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.jieti.ui.RichAnswer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class) class OverlayRenderingTest {
 @Test fun transparentConversationUpdatesKeepPageAndFormulaRenderer(){
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  lateinit var view:RichAnswer
  fun js(script:String):String{val done=CountDownLatch(1);var result="";instrumentation.runOnMainSync{view.evaluateJavascript(script){result=it;done.countDown()}};assertTrue(done.await(8,TimeUnit.SECONDS));return result}
  instrumentation.runOnMainSync{view=RichAnswer(instrumentation.targetContext);view.setOverlayStyle()}
  try{
   val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12)
   while(js("typeof window.showConversation")!="\"function\""&&System.nanoTime()<deadline)Thread.sleep(80)
   assertEquals("\"function\"",js("typeof window.showConversation"))
   js("window.acceptanceMarker='same-page'")
   instrumentation.runOnMainSync{view.showConversation(listOf("user" to "合成题", "assistant" to "公式：\\(x^2\\)"))}
   assertEquals("\"rgba(0, 0, 0, 0)\"",js("getComputedStyle(document.body).backgroundColor"))
   assertEquals("\"true\"",js("document.documentElement.dataset.overlay"))
   instrumentation.runOnMainSync{view.showConversation(listOf("user" to "合成题", "assistant" to "公式：\\(x^2\\)，继续推导。"))}
   assertEquals("\"same-page\"",js("window.acceptanceMarker"))
   assertEquals("1",js("document.querySelectorAll('.assistant .katex').length"))
   assertEquals("2",js("document.querySelectorAll('.chat-message').length"))
   assertEquals("true",js("document.querySelector('.assistant').textContent.includes('继续推导')"))
   assertEquals("\"rgba(0, 0, 0, 0)\"",js("getComputedStyle(document.querySelector('.assistant')).backgroundColor"))
  }finally{instrumentation.runOnMainSync{view.destroy()}}
 }
}
