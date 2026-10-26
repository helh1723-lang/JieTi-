import test from 'node:test';
import assert from 'node:assert/strict';
import https from 'node:https';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {createHash} from 'node:crypto';
import {Store} from '../desktop/core/store';
import {SyncServer} from '../desktop/core/sync';
test('TLS pairing, auth, resumable attachment transfer and commit round trip',async()=>{
 const store=await Store.open(fs.mkdtempSync(path.join(os.tmpdir(),'jieti-sync-')));let completed=0;const server=new SyncServer(store,()=>{},async()=>{completed++});const info=await server.start();
 const request=(route:string,method='GET',body?:unknown,token='')=>new Promise<{status:number;data:any;headers:import('node:http').IncomingHttpHeaders}>((resolve,reject)=>{const req=https.request(`https://127.0.0.1:${info.port}${route}`,{method,rejectUnauthorized:false,checkServerIdentity:()=>undefined,headers:{Authorization:'Bearer '+token,'Content-Type':Buffer.isBuffer(body)?'application/octet-stream':'application/json'}},res=>{const chunks:Buffer[]=[];res.on('data',c=>chunks.push(c));res.on('end',()=>{const bytes=Buffer.concat(chunks);let data:any;try{data=JSON.parse(bytes.toString())}catch{data=bytes}resolve({status:res.statusCode!,data,headers:res.headers});});});req.on('error',reject);if(body!==undefined)req.write(Buffer.isBuffer(body)?body:JSON.stringify(body));req.end();});
 try{
  assert.deepEqual((await request('/health')).data,{protocol:1,status:'ready'});
  assert.equal((await request('/manifest')).status,401);
  const paired=await request('/pair','POST',{secret:info.secret,deviceId:'test-tablet'});assert.equal(paired.status,200);const token=paired.data.token;
  assert.equal((await request('/pair','POST',{secret:info.secret,deviceId:'other'})).status,401);
  const bytes=Buffer.from('test-image-contents');const hash=createHash('sha256').update(bytes).digest('hex');
  const missing=await request(`/assets/${hash}`,'HEAD',undefined,token);assert.equal(missing.status,404);assert.equal(missing.headers['x-offset'],'0');
  assert.equal((await request(`/assets/${hash}?offset=0&total=${bytes.length}`,'PUT',bytes.subarray(0,4),token)).data.complete,false);
  assert.equal((await request(`/assets/${hash}`,'HEAD',undefined,token)).headers['x-offset'],'4');
  assert.equal((await request(`/assets/${hash}?offset=4&total=${bytes.length}`,'PUT',bytes.subarray(4),token)).data.complete,true);
  assert.deepEqual((await request(`/assets/${hash}`,'GET',undefined,token)).data,bytes);
  assert.equal((await request(`/assets/${hash}`,'HEAD',undefined,token)).status,200);
  assert.deepEqual((await request(`/assets/${hash}?offset=4`,'GET',undefined,token)).data,bytes.subarray(4));
  const record={id:'doc1',kind:'document',rev:'r1',baseRev:'',deleted:false,updatedAt:new Date().toISOString(),deviceId:'tablet',payload:{title:'测试资料',scope:'math',assetIds:[hash],mime:'txt',status:'queued',pages:0,processed:0,error:'',paused:false}};
  const push=await request('/records/push','POST',{records:[record]},token);assert.equal(push.status,200);assert.equal(push.data.accepted.length,1);
  assert.equal((await request('/manifest','GET',undefined,token)).data.records.some((r:any)=>r.id==='doc1'),true);
  assert.equal((await request('/records/pull','POST',{ids:['doc1']},token)).data.records[0].id,'doc1');
  let previous=record.rev;const chain=Array.from({length:12},(_,i)=>{const r={...record,baseRev:previous,rev:`offline-${String(i).padStart(2,'0')}`,payload:{...record.payload,title:`离线第${i+1}次`}};previous=r.rev;return r;});
  for(let i=0;i<chain.length;i+=10)assert.equal((await request('/history/push','POST',{records:chain.slice(i,i+10)},token)).status,200);
  const offline=await request('/records/push','POST',{records:[chain.at(-1)]},token);assert.equal(offline.data.conflicts.length,0);assert.equal(offline.data.accepted[0].payload.title,'离线第12次');
  let cursor='',historyCount=0;do{const page=(await request('/history/pull','POST',{id:'doc1',rev:cursor},token)).data;historyCount+=page.records.length;cursor=page.next;}while(cursor);assert.equal(historyCount,13);
  assert.equal((await request('/complete','POST',{},token)).status,200);assert.equal(completed,1);
 }finally{server.stop();store.close();}
});

test('document generation is idempotent while running and export failure cannot undo synced records',async()=>{
 const store=await Store.open(fs.mkdtempSync(path.join(os.tmpdir(),'jieti-export-')));let attempts=0;let failExport:()=>void=()=>{};
 const server=new SyncServer(store,()=>{},()=>{attempts++;return new Promise<void>((_,reject)=>{failExport=()=>reject(Error('synthetic export failure'));});});const info=await server.start();
 const request=(route:string,method='GET',body?:unknown,token='')=>new Promise<any>((resolve,reject)=>{const req=https.request(`https://127.0.0.1:${info.port}${route}`,{method,rejectUnauthorized:false,headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'}},res=>{const chunks:Buffer[]=[];res.on('data',c=>chunks.push(c));res.on('end',()=>resolve(JSON.parse(Buffer.concat(chunks).toString())));});req.on('error',reject);if(body!==undefined)req.write(JSON.stringify(body));req.end();});
 try{
  const {token}=await request('/pair','POST',{secret:info.secret,deviceId:'tablet'});
  const one=await request('/complete','POST',{},token);assert.equal(one.complete,true);assert.equal(one.export,'running');
  await request('/complete','POST',{},token);assert.equal(attempts,1);assert.equal((await request('/export-status','GET',undefined,token)).status,'running');
  assert.ok(Array.isArray((await request('/manifest','GET',undefined,token)).records));
  failExport();await new Promise(r=>setTimeout(r,10));assert.equal((await request('/export-status','GET',undefined,token)).status,'failed');
  assert.ok(Array.isArray((await request('/manifest','GET',undefined,token)).records));
 }finally{failExport();server.stop();store.close();}
});
