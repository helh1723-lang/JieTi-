import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import {createHash,randomBytes,timingSafeEqual} from 'node:crypto';
import forge from 'node-forge';
import {Store,assertRecord} from './store';
import type {SyncRecord} from '../../shared/generated/protocol';
const limit=16*1024*1024;
export class SyncServer {
 private server?:https.Server;private secret='';private expires=0;
 private exportJob?:Promise<void>;private exportState:{status:'idle'|'running'|'complete'|'failed'}={status:'idle'};
 constructor(readonly store:Store,readonly changed:()=>void,readonly completed:()=>Promise<void>){ }
 private identity(){
  const file=path.join(this.store.root,'tls.json');
  if(fs.existsSync(file))return JSON.parse(fs.readFileSync(file,'utf8')) as {key:string;cert:string};
  const pair=forge.pki.rsa.generateKeyPair(2048);const cert=forge.pki.createCertificate();cert.publicKey=pair.publicKey;cert.serialNumber='01'+randomBytes(16).toString('hex');cert.validity.notBefore=new Date();cert.validity.notAfter=new Date(Date.now()+3650*86400000);
  cert.setSubject([{name:'commonName',value:'Jieti Local Sync'}]);cert.setIssuer(cert.subject.attributes);cert.setExtensions([{name:'basicConstraints',cA:false},{name:'keyUsage',digitalSignature:true,keyEncipherment:true},{name:'extKeyUsage',serverAuth:true}]);cert.sign(pair.privateKey,forge.md.sha256.create());
  const identity={key:forge.pki.privateKeyToPem(pair.privateKey),cert:forge.pki.certificateToPem(cert)};fs.writeFileSync(file,JSON.stringify(identity),{mode:0o600});return identity;
 }
 async start(){
  if(this.server)this.stop();const identity=this.identity();this.secret=randomBytes(32).toString('base64url');this.expires=Date.now()+300000;
  const der=Buffer.from(forge.asn1.toDer(forge.pki.certificateToAsn1(forge.pki.certificateFromPem(identity.cert))).getBytes(),'binary');
  const fingerprint=createHash('sha256').update(der).digest('hex');
  const server=https.createServer(identity,(req,res)=>{void this.handle(req,res).catch(()=>{if(!res.headersSent)res.writeHead(400,{'Content-Type':'application/json'});res.end(JSON.stringify({error:'请求未完成，请重试并检查数据'}));});});this.server=server;
  await new Promise<void>((resolve,reject)=>{server.once('error',reject);server.listen(Number(this.store.local('syncPort')||0),'0.0.0.0',()=>{server.off('error',reject);resolve();});});
  const port=(server.address() as {port:number}).port;
  this.store.local('syncPort',String(port));
  const addresses=Object.values(os.networkInterfaces()).flat().filter(a=>a?.family==='IPv4'&&!a.internal).map(a=>a!.address);
  const url=`https://${addresses[0]||'127.0.0.1'}:${port}`;
  return {url,fingerprint,secret:this.secret,expiresAt:new Date(this.expires).toISOString(),addresses,port};
 }
 stop(){this.server?.close();this.server=undefined;this.secret='';this.expires=0;}
 private authorized(token:string){const hashed=createHash('sha256').update(token).digest('hex');return !!this.store.local('device:'+hashed);}
 private async body(req:import('node:http').IncomingMessage){const buffers:Buffer[]=[];let size=0;for await(const part of req){const b=Buffer.from(part);size+=b.length;if(size>limit)throw new Error('请求过大');buffers.push(b);}return Buffer.concat(buffers);}
 private async handle(req:import('node:http').IncomingMessage,res:import('node:http').ServerResponse){
  res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
  const url=new URL(req.url||'/', 'https://local');
  const send=(data:unknown,status=200)=>{res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify(data));};
  if(url.pathname==='/health'&&req.method==='GET')return send({protocol:1,status:'ready'});
  if(url.pathname==='/pair'&&req.method==='POST'){
   const data=JSON.parse((await this.body(req)).toString());
   const given=Buffer.from(String(data.secret||''));const expected=Buffer.from(this.secret);
   if(Date.now()>this.expires||!expected.length||given.length!==expected.length||!timingSafeEqual(given,expected))return send({error:'配对信息已失效'},401);
   if(typeof data.deviceId!=='string'||data.deviceId.length>100)return send({error:'设备标识不合法'},400);
   const token=randomBytes(32).toString('base64url');this.store.local('device:'+createHash('sha256').update(token).digest('hex'),data.deviceId);this.secret='';this.expires=0;return send({token,deviceId:this.store.deviceId,protocol:1});
  }
  const token=(req.headers.authorization||'').replace(/^Bearer /,'');
  if(!this.authorized(token))return send({error:'请重新配对'},401);
  if(url.pathname==='/manifest'&&req.method==='GET')return send({records:this.store.heads(),deviceId:this.store.deviceId});
  if(url.pathname==='/history/push'&&req.method==='POST'){const {records}=JSON.parse((await this.body(req)).toString());if(!Array.isArray(records)||records.length>10)throw new Error('批次过大');this.store.remember(records);return send({complete:true});}
  if(url.pathname==='/history/pull'&&req.method==='POST'){const {id,rev}=JSON.parse((await this.body(req)).toString());return send(this.store.historyPage(String(id),String(rev||'')));}
  if(url.pathname==='/records/pull'&&req.method==='POST'){
   const {ids}=JSON.parse((await this.body(req)).toString());if(!Array.isArray(ids)||ids.length>100)throw new Error('批次过大');return send({records:ids.map(id=>this.store.get(String(id))).filter(Boolean)});
  }
  if(url.pathname==='/records/push'&&req.method==='POST'){
   const {records}=JSON.parse((await this.body(req)).toString());if(!Array.isArray(records)||records.length>100)throw new Error('批次过大');records.forEach(assertRecord);
   for(const r of (records as SyncRecord[]).filter(r=>!r.deleted))for(const hash of [...(r.payload.assetIds as string[]||[]),...(typeof r.payload.focusAssetId==='string'&&/^[a-f0-9]{64}$/.test(r.payload.focusAssetId)?[r.payload.focusAssetId]:[])])if(!fs.existsSync(this.store.assetPath(hash)))return send({error:'附件尚未完整到达'},409);
   const result=this.store.ingest(records);this.changed();return send(result);
  }
  const asset=url.pathname.match(/^\/assets\/([a-f0-9]{64})$/);
  if(asset){
   const target=this.store.assetPath(asset[1]);
   if(req.method==='GET'){
    if(!fs.existsSync(target))return send({exists:false},404);
    const total=fs.statSync(target).size;const offset=Number(url.searchParams.get('offset')||0);if(!Number.isSafeInteger(offset)||offset<0||offset>total)throw new Error('偏移无效');const bytes=Buffer.alloc(Math.min(1048576,total-offset));const fd=fs.openSync(target,'r');try{fs.readSync(fd,bytes,0,bytes.length,offset);}finally{fs.closeSync(fd);}res.writeHead(200,{'Content-Type':'application/octet-stream','X-Total-Size':String(total)});res.end(bytes);return;
   }
   if(req.method==='HEAD'){const exists=fs.existsSync(target);const tmp=target+'.part';res.writeHead(exists?200:404,{'X-Offset':String(exists?fs.statSync(target).size:fs.existsSync(tmp)?fs.statSync(tmp).size:0)});res.end();return;}
   if(req.method==='PUT'){
    if(fs.existsSync(target))return send({complete:true});
    const offset=Number(url.searchParams.get('offset')||0),total=Number(url.searchParams.get('total'));
    const tmp=target+'.part';const existing=fs.existsSync(tmp)?fs.statSync(tmp).size:0;
    if(!Number.isSafeInteger(total)||total<0||total>8*1024**3||offset!==existing)throw new Error('附件偏移冲突');
    const bytes=await this.body(req);if(existing+bytes.length>total)throw new Error('附件长度无效');fs.appendFileSync(tmp,bytes);
    const complete=fs.statSync(tmp).size===total;
    if(complete){const digest=await new Promise<string>((resolve,reject)=>{const h=createHash('sha256');const s=fs.createReadStream(tmp);s.on('data',b=>h.update(b));s.on('end',()=>resolve(h.digest('hex')));s.on('error',reject);});if(digest!==asset[1]){fs.unlinkSync(tmp);throw new Error('附件校验失败');}fs.renameSync(tmp,target);}
    return send({complete,offset:fs.statSync(complete?target:tmp).size});
   }
  }
  if(url.pathname==='/export-status'&&req.method==='GET')return send(this.exportState);
  if(url.pathname==='/complete'&&req.method==='POST'){
   if(!this.exportJob){this.exportState={status:'running'};this.exportJob=Promise.resolve().then(()=>this.completed()).then(()=>{this.exportState={status:'complete'};},()=>{this.exportState={status:'failed'};}).finally(()=>{this.exportJob=undefined;this.changed();});}
   return send({complete:true,export:this.exportState.status});
  }
  send({error:'接口不存在'},404);
 }
}
