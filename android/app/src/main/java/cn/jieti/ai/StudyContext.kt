package cn.jieti.ai

import cn.jieti.core.SyncRecord
import cn.jieti.data.*
import kotlinx.serialization.json.*

object StudyContext {
 val inputFields=listOf("scope","transcription","studentWork","assetIds","effectiveConditions","conditionSourceIds","temporaryAssumption","assumptionSourceId","inputChangedAt")
 fun inputHash(q:SyncRecord,messages:List<SyncRecord>)=sha(buildJsonObject{for(k in inputFields)put(k,q.payload[k]?:JsonNull);putJsonArray("messages"){for(m in messages)add(buildJsonObject{put("id",m.id);put("rev",m.rev)})}}.toString().toByteArray())
 fun conditionChange(text:String):String=when{Regex("^(?:请)?(?:不要|别|不用|无需|不必)(?:把|将|再)?[^。！？\\n]{0,60}(?:改|更正|纠正|换)").containsMatchIn(text)->"";Regex("^(?:假设|如果|若把|要是)|(?:如果|假如).*(?:改|换|变|取|为)").containsMatchIn(text)->"temporary";Regex("改为|改成|改一下|更正|纠正|条件改|条件是|应为|应该是|不是.+而是|现在(?:取|设|求)|从现在|后面都按|接下来按|令.+[=＝]|(?:参数|条件|目标|取值).*(?:换|变|改)").containsMatchIn(text)->"current";else->""}
 // Apply only explicit scalar assignments. Do not rewrite expressions such as a=2x.
 fun resolvedStem(q:SyncRecord):String {
  var stem=q.payload.s("transcription")
  val assignment=Regex("(?<![A-Za-z0-9_])([A-Za-z][A-Za-z0-9_]*)\\s*[=＝]\\s*([-+]?\\d+(?:\\.\\d+)?(?:/\\d+)?)(?=\\s*(?:$|[，。；;,:：)）\\n]))")
  for(line in q.payload.s("effectiveConditions").lines())for(m in assignment.findAll(line)){
   val variable=m.groupValues[1];val value=m.groupValues[2]
   val old=Regex("(?<![A-Za-z0-9_])"+Regex.escape(variable)+"\\s*[=＝]\\s*[-+]?\\d+(?:\\.\\d+)?(?:/\\d+)?(?=\\s*(?:$|[，。；;,:：)）\\n]))")
   stem=old.replace(stem){"$variable=$value"}
  }
  return stem
 }
 fun conditions(q:SyncRecord,userId:String="")=buildJsonObject{put("当前生效条件",q.payload.s("effectiveConditions").ifBlank{"沿用原题"});put("条件来源消息",q.payload["conditionSourceIds"]?:JsonArray(emptyList()));if(userId.isBlank()||q.payload.s("assumptionSourceId")==userId)put("仅本轮临时假设",q.payload.s("temporaryAssumption"));put("规则","当前条件是用户纠正记录，后条覆盖前条同一条件；原图只作原始参考，临时假设不覆盖当前条件。")}.toString()
 fun hasSource(q:SyncRecord,messages:List<SyncRecord>)=q.payload.s("transcription").isNotBlank()||messages.any{it.payload.s("role")=="user"&&it.payload.s("content").isNotBlank()}
 fun note(records:List<SyncRecord>,questionId:String)=records.filter{!it.deleted&&it.kind=="note"&&it.payload.s("questionId")==questionId&&it.payload.s("content")!="用户明确反馈：本次讲法不合适"}.sortedByDescending{it.updatedAt}.let{rows->rows.firstOrNull{it.id=="study-note:$questionId"}?:rows.firstOrNull()}
 fun snapshot(q:SyncRecord,messages:List<SyncRecord>,mode:String)=buildJsonObject{
  put("conditions",conditions(q));put("title",q.payload.s("title"));put("transcription",resolvedStem(q));if(q.payload.s("effectiveConditions").isNotBlank())put("originalTranscriptionHistoricalOnly",q.payload.s("transcription"));put("studentWork",q.payload.s("studentWork"));put("diagnosis",q.payload.s("diagnosis"));put("mode",mode)
  put("assetIds",q.payload["assetIds"]?:JsonArray(emptyList()))
  putJsonArray("recentConversation"){for(m in messages) add(buildJsonObject{put("id",m.id);put("role",m.payload.s("role"));put("content",m.payload.s("content").take(6000));put("assetIds",m.payload["assetIds"]?:JsonArray(emptyList()))})}
 }.toString()
}
