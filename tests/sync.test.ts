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
 const request=(route:string,method='GET',body?:unknown,token='')=>new Promise<{status:number;data:any}>((resolve,reject)=>{const req=https.request(`https://127.0.0.1:${info.port}${route}`,{method,rejectUnauthorized:false,checkServerIdentity:()=>undefined,headers:{Authorization:'Bearer '+token,'Content-Type':Buffer.isBuffer(body)?'application/octet-stream':'application/json'}},res=>{const chunks:Buffer[]=[];res.on('data',c=>chunks.push(c));res.on('end',()=>{const bytes=Buffer.concat(chunks);let data:any;try{data=JSON.parse(bytes.toString())}catch{data=bytes}resolve({status:res.statusCode!,data});});});req.on('error',reject);if(body!==undefined)req.write(Buffer.isBuffer(body)?body:JSON.stringify(body));req.end();});
 try{
  assert.equal((await request('/manifest')).status,401);
  const paired=await request('/pair','POST',{secret:info.secret,deviceId:'test-tablet'});assert.equal(paired.status,200);const token=paired.data.token;
  assert.equal((await request('/pair','POST',{secret:info.secret,deviceId:'other'})).status,401);
  const bytes=Buffer.from('test-image-contents');const hash=createHash('sha256').update(bytes).digest('hex');
  assert.equal((await request(`/assets/${hash}?offset=0&total=${bytes.length}`,'PUT',bytes.subarray(0,4),token)).data.complete,false);
  assert.equal((await request(`/assets/${hash}?offset=4&total=${bytes.length}`,'PUT',bytes.subarray(4),token)).data.complete,true);
  assert.deepEqual((await request(`/assets/${hash}`,'GET',undefined,token)).data,bytes);
  const record={id:'doc1',kind:'document',rev:'r1',baseRev:'',deleted:false,updatedAt:new Date().toISOString(),deviceId:'tablet',payload:{title:'测试资料',scope:'math',assetIds:[hash],mime:'txt',status:'queued',pages:0,processed:0,error:'',paused:false}};
  const push=await request('/records/push','POST',{records:[record]},token);assert.equal(push.status,200);assert.equal(push.data.accepted.length,1);
  assert.equal((await request('/records/pull','POST',{ids:['doc1']},token)).data.records[0].id,'doc1');
  assert.equal((await request('/complete','POST',{},token)).status,200);assert.equal(completed,1);
 }finally{server.stop();store.close();}
});
