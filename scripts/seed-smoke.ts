// Deliberately synthetic acceptance data. Uses an isolated database only.
import {Store} from '../desktop/core/store';
import fs from 'node:fs';
import path from 'node:path';
const root=process.env.JIETI_DATA_DIR;
if(!root||!root.includes('.runtime'))throw new Error('Synthetic fixture requires isolated .runtime directory');
const store=await Store.open(path.join(root,'learning'));
if(!store.get('acceptance:q1')){
 const image=process.argv[2];const assets=image?[store.addAsset(fs.readFileSync(image))]:[];
 const q=store.put('question',{title:'自编验收题：有界与收敛',scope:'math',concepts:['数列极限'],transcription:'判断：有界数列一定收敛。请说明理由。',studentWork:'验收用例，无真实学生作答。',assetIds:assets,classification:'help',errorTypes:[],diagnosis:'自编验收数据：需区分有界与收敛，求助本身不构成错误证据。启动信号：检查结论是否要求唯一的极限。',solution:'自编验收解析：考虑 $a_n=(-1)^n$，它有界但不收敛。\n\n收敛数列一定有界；有界不是收敛的充分条件。',status:'done'},'acceptance:q1');
 store.put('memory',{category:'preference',scope:'math',key:'sample-hint',value:'验收示例：每次只提示一个最值得思考的方向。',sourceIds:['acceptance:user1'],origin:'explicit',confidence:1,observedAt:new Date().toISOString(),status:'active',locked:false},'acceptance:memory1');
 store.put('message',{questionId:q.id,role:'user',content:'验收用例：只给一点提示。',mode:'轻提示',memoryIds:[],assetIds:assets,policyId:''},'acceptance:user1');
 store.put('message',{questionId:q.id,role:'assistant',content:'有界只限制了数列的活动范围。你能想到一个在两个数之间反复跳动的例子吗？',mode:'轻提示',memoryIds:['acceptance:memory1'],assetIds:[],policyId:''},'acceptance:assistant1');
}
store.close();
