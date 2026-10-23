// Run only with the isolated acceptance desktop application stopped.
import {Store} from '../desktop/core/store';
import path from 'node:path';
const root=process.env.JIETI_DATA_DIR;
if(!root?.includes('.runtime'))throw new Error('Acceptance directory required');
const s=await Store.open(path.join(root,'learning'));
const q=s.get('acceptance:androidq');
if(!q)throw new Error('Tablet fixture was not synchronized');
s.edit(q.id,{title:'自编验收：有界与收敛（电脑修正）'});
s.put('note',{questionId:q.id,content:'自编验收：此笔记从电脑回传，用于验证双向同步。'},'acceptance:desktopnote');
console.log(JSON.stringify({questions:s.all('question').length,messages:s.all('message').length,memories:s.all('memory').length,assets:s.all('question').flatMap(r=>r.payload.assetIds as string[]).length,conflicts:s.conflicts().length}));
s.close();
