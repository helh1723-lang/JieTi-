package cn.jieti.capture

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class CapturePermissionActivity:ComponentActivity(){
 private val capture=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==Activity.RESULT_OK&&r.data!=null)startForegroundService(Intent(this,CaptureService::class.java).putExtra("code",r.resultCode).putExtra("projection",r.data))
  finish()
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);if(savedInstanceState==null)capture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())}
}
