package cn.jieti.retention;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.SystemClock;
import android.system.Os;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** No app classes, Kotlin or AndroidX dependency: usable against a minified target. */
public final class Check extends Instrumentation {
 private Bundle args;
 @Override public void onCreate(Bundle value){super.onCreate(value);args=value;start();}
 private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 private static String read(File file)throws IOException{
  ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
 }
 private static String hash(File file)throws Exception{
  MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
  StringBuilder out=new StringBuilder();for(byte b:md.digest())out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();
 }
 private static boolean hasImage(AccessibilityNodeInfo node){
  if(node==null)return false;
  if("资料原页，点击缩放圈选".contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return true;
  for(int i=0;i<node.getChildCount();i++)if(hasImage(node.getChild(i)))return true;return false;
 }
 @Override public void onStart(){
  Bundle result=new Bundle();
  try{
   Context context=getTargetContext();File baseline=new File(context.getFilesDir(),"performance-retention-baseline.json");
   require(baseline.isFile(),"Explicit private baseline missing");JSONObject expected=new JSONObject(read(baseline));
   File database=context.getDatabasePath("learning.sqlite");require(Os.stat(database.getPath()).st_ino==expected.getLong("databaseInode"),"Database inode changed");
   Set<String> ids=new HashSet<>();String pdfId=null;
   try(SQLiteDatabase db=SQLiteDatabase.openDatabase(database.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
    require(db.getVersion()==2,"Schema version changed");
    try(Cursor rows=db.rawQuery("SELECT id,json FROM records WHERE kind='document'",null)){
     while(rows.moveToNext()){ids.add(rows.getString(0));JSONObject record=new JSONObject(rows.getString(1));if(!record.optBoolean("deleted")&&record.getJSONObject("payload").optString("mime").contains("pdf"))pdfId=rows.getString(0);}
    }
   }
   JSONArray documents=expected.getJSONArray("documentIds");for(int i=0;i<documents.length();i++)require(ids.contains(documents.getString(i)),"Original document missing");
   JSONArray assets=expected.getJSONArray("assets");for(int i=0;i<assets.length();i++){
    JSONObject row=assets.getJSONObject(i);File file=new File(context.getFilesDir(),"assets/"+row.getString("name"));
    require(file.isFile()&&file.length()==row.getLong("bytes")&&hash(file).equals(row.getString("sha256")),"Original asset changed");
   }
   KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);Key key=ks.getKey("jieti-secrets",null);int decrypted=0;
   for(Object value:context.getSharedPreferences("protected_settings",0).getAll().values()){
    if(!(value instanceof String)||((String)value).isEmpty())continue;
    byte[] bytes=Base64.decode((String)value,Base64.NO_WRAP);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Arrays.copyOfRange(bytes,0,12)));cipher.doFinal(Arrays.copyOfRange(bytes,12,bytes.length));decrypted++;
   }
   require(decrypted==expected.getInt("encryptedSettings"),"Encrypted setting count changed");
   result.putInt("originalAssetsVerified",assets.length());result.putInt("originalDocumentsVerified",documents.length());result.putInt("encryptedSettingsDecrypted",decrypted);result.putBoolean("databaseInodeUnchanged",true);
   if(pdfId!=null){
    context.startActivity(new Intent().setClassName("cn.jieti","cn.jieti.ui.SourceActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("documentId",pdfId).putExtra("page",1));
    long until=SystemClock.uptimeMillis()+15000;boolean rendered=false;
    while(SystemClock.uptimeMillis()<until){if(hasImage(getUiAutomation().getRootInActiveWindow())){rendered=true;break;}SystemClock.sleep(200);}
    require(rendered,"Optimized PDF page did not become visible");result.putBoolean("optimizedPdfRendered",true);
   }
   if(Boolean.parseBoolean(args.getString("cleanup","false")))require(baseline.delete(),"Baseline cleanup failed");
   result.putBoolean("success",true);finish(0,result);
  }catch(Throwable error){result.putBoolean("success",false);result.putString("error",error.getClass().getSimpleName()+": "+error.getMessage());finish(1,result);}
 }
}
