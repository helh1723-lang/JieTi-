import catalog from './curriculum.json';
import type {SyncRecord} from './generated/protocol';
export type CourseTopic={id:string;title:string;aliases:string[];page:number};
export type CourseChapter={id:string;scope:string;number:string;title:string;source:string;availability:string;goal:string;topics:CourseTopic[];group?:string;startPage?:number};
export const curriculum=catalog;
export const chaptersFor=(scope:string):CourseChapter[]=>catalog.chapters.filter(c=>c.scope===scope);
export const normalizeConcept=(text:string)=>text.toLocaleLowerCase().replace(/[\s·（）()，,。.:：\-]/g,'');
const names=catalog.chapters.flatMap(c=>c.topics.map(t=>({scope:c.scope,id:t.id,names:[t.title,...t.aliases].map(normalizeConcept)})));
const matches=new Map<string,string|undefined>();
// Exact names win; ambiguous short labels stay unclassified rather than inventing a diagnosis.
export function topicFor(scope:string,concept:string):string|undefined {
 const value=normalizeConcept(concept);if(!value)return;
 const key=scope+':'+value;if(matches.has(key))return matches.get(key);
 const candidates=names.filter(t=>t.scope===scope).map(t=>({id:t.id,score:Math.max(0,...t.names.map(n=>n===value?10000+n.length:n.length>=3&&value.includes(n)?n.length:0))})).filter(t=>t.score>0).sort((a,b)=>b.score-a.score);
 const result=candidates.length&&candidates[0].score!==candidates[1]?.score?candidates[0].id:undefined;
 if(matches.size>=1024)matches.clear();matches.set(key,result);return result;
}
export const stateLabel=(state:string)=>({mastered:'已掌握',needs_refresh:'待复习',developing:'巩固中',needs_evidence:'待诊断',diagnostic:'待诊断'}[state]||'待诊断');
export function topicState(records:SyncRecord[],scope:string,id:string):string {
 const rows=records.filter(r=>!r.deleted&&r.kind==='mastery'&&r.payload.scope===scope&&topicFor(scope,String(r.payload.concept||''))===id);
 if(!rows.length)return 'diagnostic';
 const states=rows.map(r=>String(r.payload.state));
 return states.includes('needs_refresh')?'needs_refresh':states.some(s=>!['mastered','developing'].includes(s))?'needs_evidence':states.includes('developing')?'developing':'mastered';
}
export function chapterStats(records:SyncRecord[],chapter:CourseChapter){
 const states=chapter.topics.map(t=>topicState(records,chapter.scope,t.id));
 return {total:states.length,mastered:states.filter(s=>s==='mastered').length,refresh:states.filter(s=>s==='needs_refresh').length,developing:states.filter(s=>s==='developing').length};
}
