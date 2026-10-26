package cn.jieti.ai

class IncompleteAnswer(val completion:String,message:String):IllegalStateException(message)
class ProviderFailure(val statusCode:Int,message:String):IllegalStateException(message){val retryable get()=statusCode==429||statusCode in 500..599}
object Completion {
 fun validate(reason:String?,hasTools:Boolean){
  when(reason){
   "stop"->if(hasTools)throw IncompleteAnswer("tool_error","工具调用未正常结束，请重试本次提问")
   "tool_calls"->if(!hasTools)throw IncompleteAnswer("tool_error","工具调用信息缺失，请重试本次提问")
   "length"->throw IncompleteAnswer("truncated","回答达到长度限制，未完成内容已保留，可重试或缩小问题范围")
   "content_filter"->throw IncompleteAnswer("filtered","回答被服务过滤，未作为完整学习证据")
   null,""->throw IncompleteAnswer("interrupted","连接提前中断，未完成内容已保留，请重试本次提问")
   else->throw IncompleteAnswer("interrupted","模型异常终止（$reason），未作为完整学习证据")
  }
 }
}
