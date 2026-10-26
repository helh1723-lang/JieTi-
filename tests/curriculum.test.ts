import {test} from 'node:test';
import assert from 'node:assert/strict';
import {chaptersFor,topicFor,topicState,chapterStats} from '../shared/curriculum';
import type {SyncRecord} from '../shared/generated/protocol';
const mastery=(id:string,concept:string,state:string,deleted=false):SyncRecord=>({id,kind:'mastery',rev:'1',baseRev:'',payload:{scope:'signals',concept,state,evidenceIds:[]},deleted,updatedAt:'2026-10-25T00:00:00Z',deviceId:'fixture'});
test('858 follows all lecture sections, including formerly excluded topics and the missing sixth chapter',()=>{
 const chapters=chaptersFor('signals');assert.equal(chapters.length,8);assert.equal(chapters.flatMap(c=>c.topics).length,52);
 assert.equal(chapters[4].topics.find(t=>t.id==='4.5')?.title,'希尔伯特变换');assert.equal(chapters[6].availability,'outline');assert.ok(chapters[6].topics.every(t=>t.page===0));
 assert.equal(chapters[7].topics.find(t=>t.id==='7.7')?.page,18);
});
test('Math I follows the three board collections at chapter level with complete specialist topics',()=>{
 const chapters=chaptersFor('math');assert.equal(chapters.length,23);assert.equal(chapters.flatMap(c=>c.topics).length,157);
 assert.deepEqual(['高等数学','线性代数','概率论与数理统计'].map(g=>chapters.filter(c=>c.group===g).length),[9,6,8]);
 for(const concept of ['方向导数','三重积分','高斯公式','正定矩阵','区间估计','假设检验'])assert.ok(topicFor('math',concept),concept);
 assert.ok(chapters.every(c=>c.source&&c.startPage&&c.startPage>0));assert.ok(chapters.every(c=>c.topics.every(t=>t.page===0)),'No invented per-topic pages for handwritten boards');
});
test('concept matching separates continuous, discrete and math scopes; uncertain names remain unclassified',()=>{
 assert.equal(topicFor('signals','离散时间傅里叶变换'),'6.3');assert.equal(topicFor('signals','傅里叶变换'),'3.4');assert.equal(topicFor('signals','Z 变换收敛域'),'7.2');
 assert.equal(topicFor('signals','卷积积分'),'2.4');assert.equal(topicFor('signals','全新专题'),undefined);assert.equal(topicFor('signals','收敛域'),undefined);
 assert.equal(topicFor('math','傅里叶变换'),undefined);assert.equal(topicFor('math','条件概率'),'p1.3');
});
test('chapter mastery requires records, ignores tombstones, and conservatively combines aliases',()=>{
 const c=chaptersFor('signals')[3];const records=[mastery('a','傅里叶变换','mastered'),mastery('b','CTFT','needs_refresh'),mastery('c','傅里叶级数','mastered',true)];
 assert.equal(topicState([], 'signals','3.4'),'diagnostic');assert.equal(topicState(records,'signals','3.4'),'needs_refresh');assert.equal(topicState(records,'signals','3.2'),'diagnostic');
 assert.deepEqual(chapterStats(records,c),{total:6,mastered:0,refresh:1,developing:0});
 assert.equal(topicState([mastery('unknown','CTFT','invented-state')],'signals','3.4'),'needs_evidence');
});
