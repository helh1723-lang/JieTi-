package cn.jieti

import android.database.sqlite.SQLiteDatabase
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

/** Uses only platform APIs, so it also runs against the obfuscated, non-Debug APK. */
class OptimizedRetentionTest {
 @Test fun originalDatabaseAssetsAndEncryptedSettingsSurviveUpdate(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val baseline=File(context.filesDir,"performance-retention-baseline.json")
  assumeTrue("Explicit private retention baseline required",baseline.isFile)
  val expected=JSONObject(baseline.readText())
  val database=context.getDatabasePath("learning.sqlite")
  assertEquals(expected.getLong("databaseInode"),Os.stat(database.path).st_ino)
  SQLiteDatabase.openDatabase(database.path,null,SQLiteDatabase.OPEN_READONLY).use{db->
   assertEquals(2,db.version)
   val documents=mutableSetOf<String>()
   db.rawQuery("SELECT id FROM records WHERE kind='document'",null).use{cursor->while(cursor.moveToNext())documents.add(cursor.getString(0))}
   val ids=expected.getJSONArray("documentIds")
   for(i in 0 until ids.length())assertTrue("Original document missing",ids.getString(i) in documents)
  }
  val assets=expected.getJSONArray("assets")
  for(i in 0 until assets.length()){
   val row=assets.getJSONObject(i);val file=File(context.filesDir,"assets/"+row.getString("name"))
   assertTrue("Original asset missing",file.isFile);assertEquals(row.getLong("bytes"),file.length())
   val digest=MessageDigest.getInstance("SHA-256")
   file.inputStream().use{stream->val buffer=ByteArray(65536);while(true){val n=stream.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
   assertEquals(row.getString("sha256"),digest.digest().joinToString(""){"%02x".format(it)})
  }
  // Check ciphertexts without retaining or printing any decrypted values.
  val prefs=context.getSharedPreferences("protected_settings",0)
  val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val key=ks.getKey("jieti-secrets",null)
  var decrypted=0
  for(value in prefs.all.values){
   if(value !is String||value.isBlank())continue
   val bytes=Base64.decode(value,Base64.NO_WRAP)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   cipher.doFinal(bytes.copyOfRange(12,bytes.size));decrypted++
  }
  assertEquals(expected.getInt("encryptedSettings"),decrypted)
 }
}
