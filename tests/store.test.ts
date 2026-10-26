import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {Store,mergeRecords,assertRecord} from '../desktop/core/store';
import {exportData} from '../desktop/core/export';
import type {SyncRecord} from '../shared/generated/protocol';
const memory={category:'preference',scope:'math',key:'hint',value:'提示简短',sourceIds:['user:1'],origin:'explicit',confidence:1,observedAt:new Date().toISOString(),status:'active',locked:false};
async function setup(){return Store.open(fs.mkdtempSync(path.join(os.tmpdir(),'jieti-test-')));}
test('explicit memory survives process/database reopen',async()=>{const s=await setup();const r=s.put('memory',memory);const root=s.root;s.close();const reopened=await Store.open(root);assert.equal(reopened.get(r.id)?.payload.value,'提示简短');reopened.close();});
test('disjoint field edits merge; same field edits preserve a conflict',async()=>{const s=await setup();const original=s.put('memory',memory);const local=s.edit(original.id,{value:'每次只提示一步'});const incoming={...original,baseRev:original.rev,rev:'tablet-rev',payload:{...original.payload,locked:true}};const merged=s.ingest([incoming]);assert.equal(merged.conflicts.length,0);assert.equal(s.get(original.id)?.payload.locked,true);assert.equal(s.get(original.id)?.payload.value,'每次只提示一步');const conflict={...original,baseRev:original.rev,rev:'tablet-rev-2',payload:{...original.payload,value:'每次完整解答'}};assert.equal(s.ingest([conflict]).conflicts.length,1);assert.equal(s.get(original.id)?.payload.value,'每次只提示一步');s.close();});
test('forget tombstone blocks stale copies and re-extraction under a different key',async()=>{const s=await setup();const r=s.put('memory',memory);s.forget(r.id);s.ingest([{...r,rev:'old-copy'}]);assert.equal(s.get(r.id)?.deleted,true);const reconstructed={...r,id:'new-memory',rev:'reconstructed',payload:{...r.payload,key:'other-key'}};s.ingest([reconstructed]);assert.equal(s.get('new-memory')?.deleted,true);assert.equal(exportData(s).memories.length,0);s.close();});
test('schema rejects broken domain data and unsafe IDs',async()=>{const s=await setup();assert.throws(()=>s.put('memory',{value:'missing provenance'}));const r=s.put('memory',memory);assert.throws(()=>assertRecord({...r,id:'../../secret'}));assert.throws(()=>s.assetPath('../secret'));s.close();});
test('deletion wins over independent newer edits',async()=>{const s=await setup();const r=s.put('memory',memory);const deleted={...r,deleted:true,rev:'deleted'};assert.equal(mergeRecords(deleted,{...r,rev:'new',payload:{...r.payload,value:'stale'}}).record.deleted,true);s.close();});
test('question deletion cascades across sync batches, survives reopen and excludes exports',async()=>{
 const s=await setup();const q=s.put('question',{title:'临时题',scope:'math',concepts:[],transcription:'x',studentWork:'',assetIds:[],classification:'help',errorTypes:[],diagnosis:'',solution:'',status:'open'});
 const message=s.put('message',{questionId:q.id,role:'user',content:'临时问题',mode:'轻提示',memoryIds:[],assetIds:[],policyId:''});
 s.delete(q.id);assert.equal(s.get(message.id)?.deleted,true);assert.throws(()=>s.edit(q.id,{title:'不能复活'}));
 s.ingest([{...q,rev:'stale-rev'},{...message,id:'late-message',rev:'late-rev'}]);assert.equal(s.get('late-message')?.deleted,true);assert.equal(s.all('question').length,0);assert.equal(exportData(s).questions.length,0);
 const root=s.root;s.close();const reopened=await Store.open(root);assert.equal(reopened.get(q.id)?.deleted,true);reopened.close();
});
test('remote deletion removes already received children regardless of ordering and preserves attachments',async()=>{
 const s=await setup();const hash=s.addAsset(Buffer.from('keep original'));
 const doc=s.put('document',{title:'误传资料',scope:'math',assetIds:[hash],mime:'txt',status:'ready',pages:1,processed:1,error:'',paused:false});
 s.put('citation',{documentId:doc.id,page:1,text:'片段',questionId:'q'});
 s.ingest([{...doc,deleted:true,rev:'deleted'}]);assert.equal(s.all('citation').length,0);assert.equal(fs.readFileSync(s.assetPath(hash),'utf8'),'keep original');s.close();
});
test('manual question edits retain protection metadata across synchronization',async()=>{
 const s=await setup();const q=s.put('question',{title:'手工纠正',scope:'math',concepts:[],transcription:'old',studentWork:'',assetIds:[],classification:'help',errorTypes:[],diagnosis:'',solution:'',status:'open'});
 const corrected=s.edit(q.id,{transcription:'corrected',classification:'wrong'});assert.deepEqual(corrected.payload.userEditedFields,['transcription','classification']);assert.equal(corrected.payload.classificationOrigin,'user');s.close();
});
test('exports share stable question numbers, exclude paused memories and escape HTML',async()=>{const s=await setup();s.put('memory',{...memory,status:'paused'});s.put('question',{title:'自编验收题<script>',scope:'math',concepts:['极限'],transcription:'求 $\\lim_{x\\to0}\\sin x/x$',studentWork:'尚未尝试',assetIds:[],classification:'help',errorTypes:[],diagnosis:'只求助，尚无错误证据',solution:'待核验',status:'open'});const out=exportData(s);assert.match(out.markdown,/Q001/);assert.match(out.body,/Q001/);assert.doesNotMatch(out.body,/<script>/);assert.equal(out.memories.length,0);s.close();});

test('interrupted output keeps an explicit draft label when exported',async()=>{
 const s=await setup();try{const q=s.put('question',{title:'中断验收',scope:'math',concepts:[],transcription:'题干',studentWork:'',assetIds:[],classification:'help',errorTypes:[],diagnosis:'',solution:'',status:'open'});s.put('message',{questionId:q.id,role:'assistant',content:'尚未完成的推导',mode:'轻提示',memoryIds:[],assetIds:[],policyId:'',completion:'truncated'});assert.match(exportData(s).markdown,/未完成草稿，不作为学习证据/);}finally{s.close();}
});
