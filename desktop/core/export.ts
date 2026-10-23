import type {SyncRecord} from '../../shared/generated/protocol';
import {Store} from './store';
import fs from 'node:fs';
export const escapeHtml=(v:unknown)=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]!));
const text=(r:SyncRecord,k:string)=>String(r.payload[k]??'');
const human=(value:string)=>({math:'数学一',signals:'858 信号与系统',global:'通用',help:'求助记录',wrong:'错题',uncertain:'待确认',explicit:'明确偏好',inferred:'交互推断',imported:'历史导入'}[value]||value);
export function exportData(store:Store,ids?:string[]){
  const questions=store.all('question').filter(r=>!ids||ids.includes(r.id)).reverse();
  const memories=store.all('memory').filter(r=>r.payload.status==='active'&&!store.isForgotten(r));
  const mastery=store.all('mastery');
  let md='# 学习复盘资料\n\n以下包含用户证据与 AI 推断，请区分来源，不把求助等同于做错。原题截图见配套 PDF 同编号题目。\n\n## 当前有效长期记忆\n\n';
  for(const r of memories)md+=`- [${text(r,'scope')} / ${text(r,'origin')}] ${text(r,'value')}（证据：${(r.payload.sourceIds as string[]).join(', ')}）\n`;
  md+='\n## 知识掌握快照\n\n';
  for(const r of mastery)md+=`- ${text(r,'scope')} / ${text(r,'concept')}：${text(r,'state')}；观察时间 ${text(r,'observedAt')}\n`;
  let body=`<header><span>截题 · 学习复盘</span><h1>让每一次卡住，<br>成为下一次的起点。</h1><p>${questions.length} 道题 · ${memories.length} 条有效记忆</p></header><h2>近期学习档案</h2>`;
  body+=memories.map(r=>`<p>${escapeHtml(text(r,'value'))}<small>${escapeHtml(human(text(r,'scope')))} · ${escapeHtml(human(text(r,'origin')))}</small></p>`).join('');
  for(const [index,r] of questions.entries()){
    const no=String(index+1).padStart(3,'0');
    const messages=store.all('message').filter(m=>m.payload.questionId===r.id).reverse();
    md+=`\n## Q${no} ${text(r,'title')}\n\n记录ID：${r.id}\n学科：${text(r,'scope')}\n分类：${text(r,'classification')}\n\n### 原题转写\n${text(r,'transcription')||'尚未完成识别，请查看 PDF 原图。'}\n\n### 学生过程\n${text(r,'studentWork')||'无可确认的作答证据'}\n\n### 对话证据\n`;
    for(const m of messages)md+=`\n[${text(m,'role')} / ${text(m,'mode')}] ${text(m,'content')}\n`;
    const notes=store.all('note').filter(n=>n.payload.questionId===r.id);if(notes.length)md+='\n### 用户复盘笔记\n'+notes.map(n=>text(n,'content')).join('\n\n')+'\n';
    md+=`\n### 后台诊断（AI 生成）\n${text(r,'diagnosis')||'待分析'}\n\n### 解析（AI 生成，核验状态见记录）\n${text(r,'solution')||'待分析'}\n`;
    body+=`<section class="question"><small>题目练习区</small><h2>Q${no} · ${escapeHtml(text(r,'title'))}</h2><p class="meta">${escapeHtml(human(text(r,'scope')))} / ${escapeHtml(human(text(r,'classification')))}</p>`;
    for(const hash of r.payload.assetIds as string[]){try{const bytes=fs.readFileSync(store.assetPath(hash));body+=`<img alt="Q${no} 原始截图" src="data:image/${bytes[0]===0x89?'png':'jpeg'};base64,${bytes.toString('base64')}">`;}catch{body+='<p>原图尚未同步完整</p>';}}
    body+=`<div class="math">${escapeHtml(text(r,'transcription'))}</div><p class="space">独立尝试：________________________________</p>${notes.map(n=>`<h3>我的笔记</h3><div class="math">${escapeHtml(text(n,'content'))}</div>`).join('')}</section>`;
  }
  body+='<h2 class="new-page">解析与复盘区</h2>';
  for(const [i,r] of questions.entries())body+=`<section><h2>Q${String(i+1).padStart(3,'0')} · 复盘</h2><h3>卡点与诊断</h3><div class="math">${escapeHtml(text(r,'diagnosis')||'待分析；尚无充分证据。')}</div><h3>关键方法与解析</h3><div class="math">${escapeHtml(text(r,'solution')||'待分析')}</div><small>AI 生成内容，请结合原题及核验记录复习。</small></section>`;
  for(const a of store.all('artifact').filter(a=>questions.some(q=>q.id===a.payload.questionId))){body+=`<section><h3>图解说明 · ${escapeHtml(text(a,'questionId'))}</h3><p>${escapeHtml(text(a,'summary'))}</p>`;for(const hash of a.payload.assetIds as string[]){try{body+=`<img alt="交互图解静态快照" src="data:image/png;base64,${fs.readFileSync(store.assetPath(hash)).toString('base64')}">`;}catch{body+='<small>暂无静态截图，保留文字说明。</small>';}}body+='</section>';md+=`\n### 图解说明（${text(a,'questionId')}）\n${text(a,'summary')}\n`;}
  return {markdown:md,body,questions,memories};
}
