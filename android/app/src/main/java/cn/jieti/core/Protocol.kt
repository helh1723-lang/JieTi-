// Generated from shared/protocol.schema.json.
package cn.jieti.core
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
@Serializable data class MemoryItem(
 val category: String = "",
 val scope: String = "",
 val key: String = "",
 val value: String = "",
 val sourceIds: List<String> = emptyList(),
 val origin: String = "",
 val confidence: Double = 0.0,
 val observedAt: String = "",
 val status: String = "",
 val locked: Boolean = false
)
@Serializable data class MasterySnapshot(
 val scope: String = "",
 val concept: String = "",
 val state: String = "",
 val evidenceIds: List<String> = emptyList(),
 val observedAt: String = ""
)
@Serializable data class TeachingPolicyVersion(
 val parentId: String = "",
 val status: String = "",
 val reason: String = "",
 val instructions: String = "",
 val evidenceIds: List<String> = emptyList(),
 val observations: Int = 0,
 val negativeFeedback: Int = 0
)
@Serializable data class PolicyEvaluation(
 val policyId: String = "",
 val passed: Boolean = false,
 val criticalViolations: Int = 0,
 val baselineScore: Double = 0.0,
 val candidateScore: Double = 0.0,
 val caseIds: List<String> = emptyList(),
 val notes: String = ""
)
@Serializable data class ForgetRule(
 val memoryId: String = "",
 val scope: String = "",
 val key: String = "",
 val sourceIds: List<String> = emptyList(),
 val reason: String = ""
)
@Serializable data class Question(
 val title: String = "",
 val scope: String = "",
 val concepts: List<String> = emptyList(),
 val transcription: String = "",
 val studentWork: String = "",
 val assetIds: List<String> = emptyList(),
 val classification: String = "",
 val errorTypes: List<String> = emptyList(),
 val diagnosis: String = "",
 val solution: String = "",
 val status: String = "",
 val classificationOrigin: String = "",
 val userEditedFields: List<String> = emptyList()
)
@Serializable data class Message(
 val questionId: String = "",
 val role: String = "",
 val content: String = "",
 val mode: String = "",
 val memoryIds: List<String> = emptyList(),
 val assetIds: List<String> = emptyList(),
 val policyId: String = ""
)
@Serializable data class Evidence(
 val questionId: String = "",
 val scope: String = "",
 val concept: String = "",
 val outcome: String = "",
 val independent: Boolean = false,
 val hintLevel: Int = 0,
 val sourceIds: List<String> = emptyList()
)
@Serializable data class Document(
 val title: String = "",
 val scope: String = "",
 val assetIds: List<String> = emptyList(),
 val mime: String = "",
 val status: String = "",
 val pages: Int = 0,
 val processed: Int = 0,
 val error: String = "",
 val paused: Boolean = false,
 val supplementalText: String = ""
)
@Serializable data class Citation(
 val documentId: String = "",
 val page: Int = 0,
 val text: String = "",
 val questionId: String = ""
)
@Serializable data class Artifact(
 val questionId: String = "",
 val mode: String = "",
 val html: String = "",
 val summary: String = "",
 val status: String = "",
 val assetIds: List<String> = emptyList()
)
@Serializable data class Note(
 val questionId: String = "",
 val content: String = ""
)
@Serializable data class Setting(
 val key: String = "",
 val value: String = ""
)
@Serializable data class SyncRecord(val id:String,val kind:String,val rev:String,val baseRev:String="",val payload:JsonObject,val deleted:Boolean=false,val updatedAt:String,val deviceId:String)
