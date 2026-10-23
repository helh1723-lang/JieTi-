package cn.jieti.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(context:Context){
 private val prefs=context.getSharedPreferences("protected_settings",Context.MODE_PRIVATE)
 private fun key():SecretKey {val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};return (ks.getKey("jieti-secrets",null)as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("jieti-secrets",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
 fun save(name:String,value:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());prefs.edit().putString(name,Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()}
 fun get(name:String,default:String=""):String {val encoded=prefs.getString(name,null)?:return default;return runCatching{val bytes=Base64.decode(encoded,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(c.doFinal(bytes.copyOfRange(12,bytes.size)))}.getOrDefault(default)}
}
