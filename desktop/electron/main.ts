import {app,BrowserWindow,ipcMain,dialog,protocol,net} from 'electron';
import {Store} from '../core/store';
import {SyncServer} from '../core/sync';
import {exportData} from '../core/export';
import QRCode from 'qrcode';
import path from 'node:path';
import fs from 'node:fs';
import {pathToFileURL} from 'node:url';
import type {SyncRecord} from '../../shared/generated/protocol';
import {motionSmoke} from './motion-smoke';
import {glassSmoke} from './glass-smoke';
import {bugfixSmoke} from './bugfix-smoke';
let store:Store,server:SyncServer,window:BrowserWindow|undefined;
if(process.env.JIETI_DATA_DIR){fs.mkdirSync(process.env.JIETI_DATA_DIR,{recursive:true});app.setPath('userData',process.env.JIETI_DATA_DIR);app.setPath('crashDumps',path.join(process.env.JIETI_DATA_DIR,'crashes'));}
const primaryInstance=app.requestSingleInstanceLock();
if(!primaryInstance)app.quit();
app.on('second-instance',()=>{window?.show();window?.focus();});
protocol.registerSchemesAsPrivileged([{scheme:'jieti-asset',privileges:{standard:true,secure:true,supportFetchAPI:true}},{scheme:'jieti',privileges:{standard:true,secure:true,supportFetchAPI:true}}]);
const changed=()=>window?.webContents.send('changed');
function guard(event:Electron.IpcMainInvokeEvent){if(!window||event.sender!==window.webContents||event.senderFrame!==window.webContents.mainFrame)throw new Error('无效请求来源');}
function on(channel:string,fn:(...args:any[])=>unknown){ipcMain.handle(channel,(event,...args)=>{guard(event);return fn(...args);});}
async function exportFiles(ids?:string[]){
 const data=exportData(store,ids);const root=path.join(store.root,'exports',new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'})+'-'+Date.now());fs.mkdirSync(root,{recursive:true});
 fs.writeFileSync(path.join(root,'给GPT的学习复盘.md'),data.markdown);
 const katex=path.join(app.getAppPath(),'node_modules/katex/dist');
 const html=`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><link rel="stylesheet" href="${pathToFileURL(path.join(katex,'katex.min.css'))}"><style>@page{size:A4;margin:18mm}body{font:14px/1.8 "Microsoft YaHei",sans-serif;color:#30392f}header{padding:25px 0 45px;border-bottom:2px solid #4f6953}h1{font-size:36px;line-height:1.4}h2{color:#4f6953;font-size:20px;margin-top:30px}small,.meta{display:block;color:#73786e;font-size:11px}.question,.new-page{break-before:page}section{margin:24px 0}img{display:block;max-width:100%;max-height:450px;object-fit:contain}.math{white-space:pre-wrap;overflow-wrap:anywhere}.space{padding:30px 0}h2,h3{break-after:avoid}p,img{break-inside:avoid}</style></head><body>${data.body}<script src="${pathToFileURL(path.join(katex,'katex.min.js'))}"></script><script src="${pathToFileURL(path.join(katex,'contrib/auto-render.min.js'))}"></script><script>renderMathInElement(document.body,{delimiters:[{left:'$$',right:'$$',display:true},{left:'\\\\[',right:'\\\\]',display:true},{left:'$',right:'$',display:false},{left:'\\\\(',right:'\\\\)',display:false}],throwOnError:false});</script></body></html>`;
 const htmlFile=path.join(root,'review.html');fs.writeFileSync(htmlFile,html);
 const printWindow=new BrowserWindow({show:false,webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true}});
 try{await printWindow.loadFile(htmlFile);await printWindow.webContents.executeJavaScript('document.fonts.ready.then(()=>true)');const pdf=await printWindow.webContents.printToPDF({printBackground:true,pageSize:'A4',preferCSSPageSize:true,displayHeaderFooter:true,headerTemplate:'<span></span>',footerTemplate:'<div style="font-size:9px;color:#73786e;text-align:center;width:100%">截题 · <span class="pageNumber"></span> / <span class="totalPages"></span></div>'});fs.writeFileSync(path.join(root,'我的错题复盘.pdf'),pdf);}finally{printWindow.destroy();}
 const assets=path.join(root,'screenshots');fs.mkdirSync(assets);
 for(const q of data.questions)for(const hash of q.payload.assetIds as string[]){const src=store.assetPath(hash);if(fs.existsSync(src))fs.copyFileSync(src,path.join(assets,hash+'.png'));}
 const questionIds=new Set(data.questions.map(q=>q.id));const backup=fs.openSync(path.join(root,'backup.json'),'w');fs.writeSync(backup,'{"protocol":1,"records":[');let first=true;for(const r of store.each()){if(r.deleted||r.kind==='memory'&&store.isForgotten(r))continue;if(['question','message','artifact','note','evidence','citation'].includes(r.kind)&&!questionIds.has(r.id)&&!questionIds.has(String(r.payload.questionId)))continue;fs.writeSync(backup,(first?'':',')+JSON.stringify(r));first=false;}fs.writeSync(backup,']}');fs.closeSync(backup);
 return root;
}
if(primaryInstance)app.whenReady().then(async()=>{
 store=await Store.open(path.join(app.getPath('userData'),'learning'),path.join(app.getAppPath(),'node_modules/sql.js/dist/sql-wasm.wasm'));
 protocol.handle('jieti-asset',req=>{try{const hash=new URL(req.url).hostname;const bytes=fs.readFileSync(store.assetPath(hash));return new Response(new Uint8Array(bytes),{headers:{'Content-Type':bytes[0]===0x89?'image/png':'image/jpeg'}});}catch{return new Response('Not found',{status:404});}});
 protocol.handle('jieti',req=>{try{const u=new URL(req.url);const relative=decodeURIComponent(u.pathname).replace(/^\/+/, '')||'index.html';const root=path.resolve(__dirname,'../ui');const file=path.resolve(root,relative);if(!file.startsWith(root+path.sep))return new Response('',{status:403});const type:Record<string,string>={'.html':'text/html; charset=utf-8','.js':'text/javascript','.css':'text/css','.woff2':'font/woff2','.woff':'font/woff','.ttf':'font/ttf'};return new Response(new Uint8Array(fs.readFileSync(file)),{headers:{'Content-Type':type[path.extname(file)]||'application/octet-stream'}});}catch{return new Response('Not found',{status:404});}});
 server=new SyncServer(store,changed,async()=>{const today=new Date().toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'});await exportFiles(store.all('question').filter(r=>new Date(r.updatedAt).toLocaleDateString('sv-SE',{timeZone:'Asia/Shanghai'})===today).map(r=>r.id));});
 window=new BrowserWindow({width:1420,height:920,minWidth:800,minHeight:650,backgroundColor:'#f8f7f2',title:'截题 · 学习手记',webPreferences:{preload:path.join(__dirname,'preload.cjs'),nodeIntegration:false,contextIsolation:true,sandbox:true}});
 window.webContents.setWindowOpenHandler(()=>({action:'deny'}));window.webContents.on('will-navigate',e=>e.preventDefault());window.webContents.session.setPermissionRequestHandler((_wc,_p,cb)=>cb(false));
 on('snapshot',()=>({records:store.uiRecords(),conflicts:store.conflicts(),deviceId:store.deviceId,root:store.root}));
 on('edit',(id:string,p:Record<string,unknown>,expectedRev?:string)=>{const r=store.get(id);if(!r||!['question','memory','mastery','document','note'].includes(r.kind))throw new Error('该记录不能直接编辑');if(expectedRev!==undefined&&r.rev!==expectedRev){const base=store.history(id,expectedRev);if(!base||Object.keys(p).some(k=>JSON.stringify(base.payload[k])!==JSON.stringify(r.payload[k])))throw new Error('同一字段已更新，当前草稿保留；请复制后重新打开最新版本');}const next=store.edit(id,p);changed();return next;});
 on('forget',(id:string)=>{store.forget(id);changed();});
 on('delete',(id:string,expectedRev:string)=>{if(store.get(id)?.rev!==expectedRev)throw new Error('记录已更新，请重新确认最新内容');store.delete(id);changed();});
 on('sync',async(start:boolean)=>{if(!start){server.stop();return null;}const data=await server.start();const pairing='jieti://pair?data='+Buffer.from(JSON.stringify(data)).toString('base64url');return {...data,pairing,qr:await QRCode.toDataURL(pairing)};});
 on('import',async(scope:string)=>{const result=await dialog.showOpenDialog(window!,{title:'导入课程资料，下次同步时传到平板',properties:['openFile','multiSelections'],filters:[{name:'课程资料',extensions:['pdf','png','jpg','jpeg','docx','txt','md']}]});if(result.canceled)return [];const docs:SyncRecord[]=[];for(const file of result.filePaths){const hash=await store.addAssetFile(file);docs.push(store.put('document',{title:path.basename(file),scope,assetIds:[hash],mime:path.extname(file).slice(1),status:'awaiting_tablet',pages:0,processed:0,error:'',paused:false}));}changed();return docs;});
 on('export',exportFiles);
 on('note',(questionId:string,content:string,expectedRev?:string)=>{if(!store.get(questionId)||content.length>50000)throw new Error('笔记无效');const existing=store.all('note').find(r=>r.payload.questionId===questionId);if(expectedRev!==undefined&&(existing?.rev||'')!==expectedRev)throw new Error('笔记已更新，当前草稿仍保留；请复制后重新打开最新版本');const saved=store.put('note',{questionId,content},existing?.id);changed();return saved;});
 on('resolve',(id:string,side:'local'|'incoming')=>{if(!['local','incoming'].includes(side))throw new Error('选择无效');store.resolve(id,side);changed();});
 on('policy',(id:string)=>{const p=store.get(id);if(p?.kind!=='policy'||!['active','retired','monitoring'].includes(String(p.payload.status)))throw new Error('只能恢复已通过评测的策略');for(const r of store.all('policy'))if(['active','monitoring'].includes(String(r.payload.status)))store.edit(r.id,{status:'retired'});store.edit(id,{status:'active'});changed();});
 await window.loadURL('jieti://app/index.html');
 if(process.argv.includes('--bugfix-smoke')){try{await bugfixSmoke(window,path.join(process.cwd(),'.cache','bugfix'));app.quit();}catch(error){fs.writeFileSync(path.join(process.cwd(),'.cache','bugfix-failure.txt'),String(error));app.exit(1);}return;}
 if(process.argv.includes('--glass-smoke')){try{await glassSmoke(window,path.join(process.cwd(),'.cache','glass'));app.quit();}catch(error){fs.writeFileSync(path.join(process.cwd(),'.cache','glass-failure.txt'),String(error));app.exit(1);}return;}
 if(process.argv.includes('--motion-smoke')){try{await motionSmoke(window,path.join(process.cwd(),'.cache','motion'));app.quit();}catch(error){fs.writeFileSync(path.join(process.cwd(),'.cache','motion-failure.txt'),String(error));app.exit(1);}return;}
 if(process.argv.includes('--sync-smoke')){const pairing=await server.start();fs.writeFileSync(path.join(process.cwd(),'.cache','emulator-pairing.txt'),'jieti://pair?data='+Buffer.from(JSON.stringify({...pairing,url:`https://10.0.2.2:${pairing.port}`})).toString('base64url'));}
 if(process.argv.includes('--ui-smoke')){
  await window.webContents.executeJavaScript('document.fonts.ready.then(()=>true)');
  await window.webContents.executeJavaScript('window.jieti.snapshot()');
  await new Promise(r=>setTimeout(r,750));
  const output=path.join(process.cwd(),'.cache');
  fs.writeFileSync(path.join(output,'desktop-home.png'),(await window.webContents.capturePage()).toPNG());
  const snapshot=await window.webContents.executeJavaScript('document.body.innerText');fs.writeFileSync(path.join(output,'desktop-ui.txt'),snapshot);
  if(await window.webContents.executeJavaScript("!!document.querySelector('.question-item')")){
   await window.webContents.executeJavaScript("document.querySelector('.question-item').click()");await new Promise(r=>setTimeout(r,350));
   fs.writeFileSync(path.join(output,'desktop-reading.png'),(await window.webContents.capturePage()).toPNG());
  }
  await window.webContents.executeJavaScript("[...document.querySelectorAll('button')].find(b=>b.textContent.includes('我的学习记忆')).click()");
  await new Promise(r=>setTimeout(r,300));
  fs.writeFileSync(path.join(output,'desktop-memory.png'),(await window.webContents.capturePage()).toPNG());
  app.quit();
 }
 if(process.argv.includes('--export-smoke')){const out=await exportFiles();fs.writeFileSync(path.join(process.cwd(),'.cache','export-result.txt'),out);app.quit();}
});
app.on('window-all-closed',()=>{server?.stop();store?.close();app.quit();});
