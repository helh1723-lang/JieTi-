package cn.jieti
import android.app.Application
import cn.jieti.ai.*
import cn.jieti.core.MemoryEngine
import cn.jieti.data.*
import cn.jieti.library.Library
import cn.jieti.sync.SyncClient
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.MutableStateFlow

class JietiApp:Application(){
 val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
 val foreground=AtomicInteger(0)
 val background=Mutex()
 val documents=Mutex()
 val uiTheme=MutableStateFlow("system")
 val uiFontScale=MutableStateFlow(1f)
 lateinit var store:LearningStore;lateinit var settings:SecureSettings;lateinit var provider:Provider;lateinit var memory:MemoryEngine;lateinit var library:Library;lateinit var agents:Agents;lateinit var sync:SyncClient
 override fun onCreate(){super.onCreate();store=LearningStore(this);settings=SecureSettings(this);uiTheme.value=settings.get("appearance.theme","system");uiFontScale.value=settings.get("appearance.font","1").toFloatOrNull()?:1f;provider=Provider(settings,foreground);memory=MemoryEngine(store);library=Library(this,store,provider,settings,foreground);agents=Agents(this,store,provider,memory,library,foreground);sync=SyncClient(this,store,settings);scope.launch{memory.seed();memory.refreshStale()}}
}
