import test from 'node:test';
import assert from 'node:assert/strict';
import {readDraft,writeDraft} from '../shared/draft';
test('draft survives editor recreation, isolates questions, retains original revision and reports storage failures',()=>{
 const rows=new Map<string,string>();const storage={getItem:(k:string)=>rows.get(k)||null,setItem:(k:string,v:string)=>{rows.set(k,v);}};
 assert.equal(writeDraft(storage,'q1',{value:'未保存内容',baseRev:'old'}),true);
 assert.deepEqual(readDraft(storage,'q1'),{value:'未保存内容',baseRev:'old'});assert.equal(readDraft(storage,'q2'),undefined);
 assert.equal(writeDraft({setItem:()=>{throw Error('quota');}},'q1',{value:'x',baseRev:''}),false);
 rows.set('bad','invalid');assert.equal(readDraft(storage,'bad'),undefined);
});
