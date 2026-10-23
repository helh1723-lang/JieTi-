import initSqlJs, {type Database} from 'sql.js';
import fs from 'node:fs';
import path from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {pipeline} from 'node:stream/promises';
import {Transform} from 'node:stream';
import Ajv from 'ajv';
import schema from '../../shared/protocol.schema.json';
import type {Kind,SyncRecord,MemoryItem} from '../../shared/generated/protocol';

const ajv=new Ajv({strict:false});
const validate=ajv.compile(schema);
const payloadValidators=new Map(Object.entries(schema.definitions).map(([k,v])=>[k,ajv.compile(v)]));
const names:Record<Kind,string>={memory:'MemoryItem',mastery:'MasterySnapshot',policy:'TeachingPolicyVersion',evaluation:'PolicyEvaluation',forget:'ForgetRule',question:'Question',message:'Message',evidence:'Evidence',document:'Document',citation:'Citation',artifact:'Artifact',note:'Note',setting:'Setting'};
export function assertRecord(v:unknown):asserts v is SyncRecord {
  if(!validate(v))throw new Error('协议格式不正确');
  const record=v as unknown as SyncRecord;
  if(!payloadValidators.get(names[record.kind])!(record.payload))throw new Error(`${record.kind} 数据不完整`);
  if(!/^[a-zA-Z0-9_.:-]{1,160}$/.test(record.id))throw new Error('记录 ID 不合法');
}
export interface Conflict {id:string;recordId:string;local:SyncRecord;incoming:SyncRecord;fields:string[]}
const same=(a:unknown,b:unknown)=>JSON.stringify(a)===JSON.stringify(b);
export function mergeRecords(local:SyncRecord,incoming:SyncRecord,base?:SyncRecord):{record:SyncRecord;fields:string[]} {
  if(local.kind!==incoming.kind)throw new Error('不能改变记录类型');
  if(local.rev===incoming.rev)return {record:local,fields:[]};
  if(local.deleted||incoming.deleted)return {record:local.deleted?local:incoming,fields:[]};
  if(same(local.payload,incoming.payload))return {record:incoming,fields:[]};
  if(incoming.baseRev===local.rev)return {record:incoming,fields:[]};
  if(local.baseRev===incoming.rev)return {record:local,fields:[]};
  if(!base)return {record:local,fields:['无共同版本']};
  const merged={...local.payload};const fields:string[]=[];
  for(const key of new Set([...Object.keys(local.payload),...Object.keys(incoming.payload)])){
    const l=local.payload[key],r=incoming.payload[key],b=base.payload[key];
    if(same(l,r))continue;
    if(same(l,b))merged[key]=r;
    else if(!same(r,b))fields.push(key);
  }
  if(fields.length)return {record:local,fields};
  return {record:{...local,payload:merged,baseRev:local.rev,rev:randomUUID(),updatedAt:new Date().toISOString()},fields};
}
export class Store {
  private constructor(readonly db:Database,readonly root:string,readonly deviceId:string){}
  static async open(root:string,wasmPath?:string){
    fs.mkdirSync(path.join(root,'assets'),{recursive:true});
    const SQL=await initSqlJs(wasmPath?{locateFile:()=>wasmPath}:{});
    const file=path.join(root,'learning.sqlite');
    const db=new SQL.Database(fs.existsSync(file)?fs.readFileSync(file):undefined);
    db.run(`CREATE TABLE IF NOT EXISTS records(id TEXT PRIMARY KEY,kind TEXT NOT NULL,rev TEXT NOT NULL,data TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS history(id TEXT,rev TEXT,data TEXT NOT NULL,PRIMARY KEY(id,rev));
      CREATE TABLE IF NOT EXISTS conflicts(id TEXT PRIMARY KEY,data TEXT NOT NULL);
      CREATE TABLE IF NOT EXISTS local_settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);
      PRAGMA user_version=1;`);
    const row=db.exec("SELECT value FROM local_settings WHERE key='deviceId'")[0]?.values[0]?.[0];
    const deviceId=String(row||randomUUID());
    db.run('INSERT OR IGNORE INTO local_settings VALUES(?,?)',['deviceId',deviceId]);
    const store=new Store(db,root,deviceId);store.flush();return store;
  }
  flush(){const file=path.join(this.root,'learning.sqlite');const tmp=file+'.tmp';fs.writeFileSync(tmp,this.db.export());fs.renameSync(tmp,file);}
  get(id:string):SyncRecord|undefined {const s=this.db.prepare('SELECT data FROM records WHERE id=?');try{s.bind([id]);return s.step()?JSON.parse(s.getAsObject().data as string):undefined;}finally{s.free();}}
  history(id:string,rev:string):SyncRecord|undefined {const s=this.db.prepare('SELECT data FROM history WHERE id=? AND rev=?');try{s.bind([id,rev]);return s.step()?JSON.parse(s.getAsObject().data as string):undefined;}finally{s.free();}}
  all(kind?:Kind,includeDeleted=false):SyncRecord[]{const s=this.db.prepare(kind?'SELECT data FROM records WHERE kind=?':'SELECT data FROM records');const result:SyncRecord[]=[];try{if(kind)s.bind([kind]);while(s.step()){const r=JSON.parse(s.getAsObject().data as string);if(includeDeleted||!r.deleted)result.push(r);}}finally{s.free();}return result.sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));}
  heads(){return (this.db.exec('SELECT id,rev FROM records')[0]?.values||[]).map(r=>({id:String(r[0]),rev:String(r[1])}));}
  uiRecords():SyncRecord[]{const s=this.db.prepare("SELECT data FROM records WHERE kind NOT IN ('artifact','evidence')");const records:SyncRecord[]=[];try{while(s.step()){const r=JSON.parse(s.getAsObject().data as string);if(!r.deleted)records.push(r);}}finally{s.free();}return records.sort((a,b)=>b.updatedAt.localeCompare(a.updatedAt));}
  *each():Generator<SyncRecord>{const s=this.db.prepare('SELECT data FROM records');try{while(s.step())yield JSON.parse(s.getAsObject().data as string);}finally{s.free();}}
  write(r:SyncRecord){assertRecord(r);const data=JSON.stringify(r);this.db.run('INSERT OR IGNORE INTO history VALUES(?,?,?)',[r.id,r.rev,data]);this.db.run('INSERT OR REPLACE INTO records VALUES(?,?,?,?)',[r.id,r.kind,r.rev,data]);}
  put(kind:Kind,payload:object,id=randomUUID() as string){const old=this.get(id);if(old?.deleted)throw new Error('已删除记录不能直接恢复');const r:SyncRecord={id,kind,payload:payload as Record<string,unknown>,baseRev:old?.rev||'',rev:randomUUID(),deleted:false,updatedAt:new Date().toISOString(),deviceId:this.deviceId};this.write(r);this.flush();return r;}
  edit(id:string,changes:Record<string,unknown>){const r=this.get(id);if(!r)throw new Error('记录不存在');const p={...r.payload,...changes};if(r.kind==='memory'&&changes.value!==undefined){p.origin='explicit';p.confidence=1;p.observedAt=new Date().toISOString();}if(r.kind==='question'){if(changes.classification!==undefined)p.classificationOrigin='user';p.userEditedFields=[...new Set([...(r.payload.userEditedFields as string[]||[]),...Object.keys(changes)])];}return this.put(r.kind,p,id);}
  private parentDeleted(r:SyncRecord){return [r.payload.questionId,r.payload.documentId].some(id=>typeof id==='string'&&this.get(id)?.deleted);}
  private cascadeDeleted(){for(const r of this.all())if(this.parentDeleted(r))this.write({...r,deleted:true,baseRev:r.rev,rev:randomUUID(),updatedAt:new Date().toISOString(),deviceId:this.deviceId});}
  delete(id:string){const r=this.get(id);if(!r||!['question','document'].includes(r.kind))throw new Error('只能删除题目或资料');this.db.run('BEGIN');try{if(!r.deleted)this.write({...r,deleted:true,baseRev:r.rev,rev:randomUUID(),updatedAt:new Date().toISOString(),deviceId:this.deviceId});this.cascadeDeleted();for(const c of this.conflicts())if(this.get(c.recordId)?.deleted)this.db.run('DELETE FROM conflicts WHERE id=?',[c.id]);this.db.run('COMMIT');this.flush();}catch(e){this.db.run('ROLLBACK');throw e;}}
  forget(id:string){const r=this.get(id);if(!r||r.kind!=='memory')throw new Error('记忆不存在');const m=r.payload as unknown as MemoryItem;this.db.run('BEGIN');try{this.write({...r,deleted:true,baseRev:r.rev,rev:randomUUID(),updatedAt:new Date().toISOString()});this.write({id:randomUUID(),kind:'forget',rev:randomUUID(),baseRev:'',deleted:false,updatedAt:new Date().toISOString(),deviceId:this.deviceId,payload:{memoryId:id,scope:m.scope,key:m.key,sourceIds:m.sourceIds,reason:'用户明确遗忘'}});this.db.run('COMMIT');this.flush();}catch(e){this.db.run('ROLLBACK');throw e;}}
  isForgotten(r:SyncRecord){return r.kind==='memory'&&this.all('forget').some(f=>f.payload.memoryId===r.id||(f.payload.scope===r.payload.scope&&(f.payload.key===r.payload.key||(f.payload.sourceIds as string[]).some(id=>(r.payload.sourceIds as string[]).includes(id)))));}
  ingest(records:SyncRecord[]){
    records.forEach(assertRecord);const accepted:SyncRecord[]=[];const conflicts:Conflict[]=[];
    this.db.run('BEGIN');
    try{
      for(const incoming of [...records].sort((a,b)=>Number(b.kind==='forget')-Number(a.kind==='forget'))){
        const local=this.get(incoming.id);
        let r=incoming;
        if(this.isForgotten(incoming)||this.parentDeleted(incoming))r={...incoming,deleted:true,rev:local?.deleted?local.rev:randomUUID()};
        const result=local?mergeRecords(local,r,this.history(local.id,r.baseRev)):{record:r,fields:[]};
        if(result.fields.length){const c={id:randomUUID(),recordId:r.id,local:local!,incoming:r,fields:result.fields};this.db.run('INSERT INTO conflicts VALUES(?,?)',[c.id,JSON.stringify(c)]);conflicts.push(c);}
        else {this.write(r);this.write(result.record);accepted.push(result.record);}
      }
      // A forget rule may arrive after a memory in another synchronization batch.
      for(const m of this.all('memory'))if(this.isForgotten(m))this.write({...m,deleted:true,baseRev:m.rev,rev:randomUUID()});
      this.cascadeDeleted();
      for(let i=0;i<accepted.length;i++)accepted[i]=this.get(accepted[i].id)!;
      this.db.run('COMMIT');this.flush();return {accepted,conflicts};
    }catch(e){this.db.run('ROLLBACK');throw e;}
  }
  conflicts():Conflict[]{return (this.db.exec('SELECT data FROM conflicts')[0]?.values||[]).map(r=>JSON.parse(String(r[0])));}
  resolve(id:string,side:'local'|'incoming'){const c=this.conflicts().find(c=>c.id===id);if(!c)throw new Error('冲突已处理');const chosen=c[side];if(this.isForgotten(chosen)||this.get(chosen.id)?.deleted||this.parentDeleted(chosen))throw new Error('已删除或遗忘内容不能恢复');const old=this.get(chosen.id);const r={...chosen,baseRev:old?.rev||'',rev:randomUUID(),updatedAt:new Date().toISOString(),deviceId:this.deviceId};this.write(r);this.db.run('DELETE FROM conflicts WHERE id=?',[id]);this.flush();return r;}
  local(key:string,value?:string){if(value!==undefined){this.db.run('INSERT OR REPLACE INTO local_settings VALUES(?,?)',[key,value]);this.flush();return value;}const s=this.db.prepare('SELECT value FROM local_settings WHERE key=?');try{s.bind([key]);return s.step()?String(s.getAsObject().value):undefined;}finally{s.free();}}
  assetPath(hash:string){if(!/^[a-f0-9]{64}$/.test(hash))throw new Error('附件标识不合法');return path.join(this.root,'assets',hash);}
  addAsset(bytes:Buffer){const hash=createHash('sha256').update(bytes).digest('hex');const file=this.assetPath(hash);if(!fs.existsSync(file))fs.writeFileSync(file,bytes);return hash;}
  async addAssetFile(file:string){const temporary=path.join(this.root,'assets',randomUUID()+'.import');const digest=createHash('sha256');try{await pipeline(fs.createReadStream(file),new Transform({transform(chunk,_encoding,cb){digest.update(chunk);cb(null,chunk);}}),fs.createWriteStream(temporary));const hash=digest.digest('hex');const target=this.assetPath(hash);if(fs.existsSync(target))fs.unlinkSync(temporary);else fs.renameSync(temporary,target);return hash;}catch(e){if(fs.existsSync(temporary))fs.unlinkSync(temporary);throw e;}}
  close(){this.flush();this.db.close();}
}
