import type {SyncRecord} from './generated/protocol';
export function studyNote(records:SyncRecord[],questionId:string){
 const rows=records.filter(r=>!r.deleted&&r.kind==='note'&&r.payload.questionId===questionId&&r.payload.content!=='用户明确反馈：本次讲法不合适').sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));
 return rows.find(r=>r.id===`study-note:${questionId}`)||rows[0];
}
