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
 private val secretKey:SecretKey by lazy {val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey("jieti-secrets",null)as? SecretKey)?:KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{init(KeyGenParameterSpec.Builder("jieti-secrets",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()}
 private data class Cached(val encoded:String,val plain:String)
 private val cache=java.util.concurrent.ConcurrentHashMap<String,Cached>()
 fun save(name:String,value:String){val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,secretKey);val encoded=Base64.encodeToString(c.iv+c.doFinal(value.toByteArray()),Base64.NO_WRAP);prefs.edit().putString(name,encoded).apply();cache[name]=Cached(encoded,value)}
 fun get(name:String,default:String=""):String {val encoded=prefs.getString(name,null)?:return default;cache[name]?.takeIf{it.encoded==encoded}?.let{return it.plain};return runCatching{val bytes=Base64.decode(encoded,Base64.NO_WRAP);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,secretKey,GCMParameterSpec(128,bytes.copyOfRange(0,12)));String(c.doFinal(bytes.copyOfRange(12,bytes.size))).also{cache[name]=Cached(encoded,it)}}.getOrDefault(default)}
}
