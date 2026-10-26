package cn.jieti.data

import android.content.Context
import org.json.JSONObject

/** Local drafts never enter the sync protocol or learning evidence. */
class LocalDrafts(context:Context){
 private val prefs=context.getSharedPreferences("local_drafts",Context.MODE_PRIVATE)
 data class Draft(val text:String,val baseRev:String)
 fun read(key:String):Draft?=runCatching{prefs.getString(key,null)?.let{val o=JSONObject(it);Draft(o.getString("text"),o.optString("baseRev"))}}.getOrNull()
 fun save(key:String,text:String,baseRev:String=""){prefs.edit().putString(key,JSONObject().put("text",text).put("baseRev",baseRev).toString()).apply()}
 fun remove(key:String){prefs.edit().remove(key).apply()}
}
