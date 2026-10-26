import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {Store} from '../desktop/core/store';
import {studyNote} from '../shared/study-note';
test('synced agent notes remain canonical and desktop edits become manual',async()=>{
 const store=await Store.open(fs.mkdtempSync(path.join(os.tmpdir(),'jieti-note-')));
 try{
  const note=store.put('note',{questionId:'q',content:'条件识别与考场启动',origin:'agent',sourceHash:'abc'},'study-note:q');
  store.put('note',{questionId:'q',content:'用户明确反馈：本次讲法不合适'});
  assert.equal(studyNote(store.all('note'),'q')?.id,note.id);
  const changed=store.edit(note.id,{content:'我补充的条件检查'});assert.equal(changed.payload.origin,'user');assert.equal(changed.payload.sourceHash,'');
 }finally{store.close()}
});
