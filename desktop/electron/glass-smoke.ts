import type {BrowserWindow} from 'electron';
import fs from 'node:fs';
import path from 'node:path';

export async function glassSmoke(window:BrowserWindow,output:string){
 fs.mkdirSync(output,{recursive:true});const wc=window.webContents;const pause=(ms:number)=>new Promise(r=>setTimeout(r,ms));const report:Record<string,unknown>={};
 await pause(900);
 report.glass=await wc.executeJavaScript(`(()=>{const el=document.querySelector('.sidebar nav');return {filter:getComputedStyle(el).backdropFilter,lenses:document.querySelectorAll('feDisplacementMap').length,light:getComputedStyle(el,'::before').animationName};})()`);
 if(!(report.glass as any).lenses)throw new Error('No optical lenses attached');
 fs.writeFileSync(path.join(output,'desktop-home.png'),(await wc.capturePage()).toPNG());
 await wc.executeJavaScript("document.querySelector('.question-item').click()");await pause(600);await wc.executeJavaScript("[...document.querySelectorAll('button')].find(b=>b.textContent.trim()==='答疑过程').click()");await pause(500);
 report.chat=await wc.executeJavaScript(`(()=>{const u=document.querySelector('.message.user'),a=document.querySelector('.message.assistant');const p=u.parentElement.getBoundingClientRect(),ur=u.getBoundingClientRect(),ar=a.getBoundingClientRect();return {userRight:ur.right,parentRight:p.right,userLeft:ur.left,aiLeft:ar.left,aiWidth:ar.width,parentWidth:p.width};})()`);
 const chat=report.chat as any;if(Math.abs(chat.userRight-chat.parentRight)>2||chat.userLeft<=chat.aiLeft||Math.abs(chat.aiWidth-chat.parentWidth)>2)throw new Error('Chat alignment failed');
 fs.writeFileSync(path.join(output,'desktop-chat.png'),(await wc.capturePage()).toPNG());
 // Compare rendered pixels with exactly the same striped backdrop and glass;
 // only the displacement filter is toggled. This detects unsupported/no-op filters.
 await wc.executeJavaScript(`(()=>{const host=document.createElement('div');host.id='optical-fixture';host.className='sidebar';host.style.cssText='position:fixed;left:40px;top:140px;width:400px;height:150px;z-index:100;background:repeating-linear-gradient(90deg,#304c30 0 5px,#fff 5px 10px);padding:20px';const lens=document.createElement('nav');lens.style.cssText='flex:none;width:330px;height:95px;border-radius:42px;background:transparent!important;box-shadow:none;border:0';host.append(lens);document.body.append(host);})()`);
 await pause(350);const rect={x:60,y:160,width:330,height:95};
 // Freeze shimmer so pixel differences can only come from refraction.
 await wc.executeJavaScript("document.querySelector('#optical-fixture nav').style.setProperty('animation','none');document.querySelector('#optical-fixture').insertAdjacentHTML('beforeend','<style>#optical-fixture nav:before{display:none}</style>')");
 const refracted=(await wc.capturePage(rect)).toBitmap();
 await wc.executeJavaScript("document.querySelector('#optical-fixture nav').style.backdropFilter='none'");await pause(100);
 const flat=(await wc.capturePage(rect)).toBitmap();let changed=0;for(let i=0;i<flat.length;i+=4)if(Math.abs(flat[i]-refracted[i])+Math.abs(flat[i+1]-refracted[i+1])+Math.abs(flat[i+2]-refracted[i+2])>24)changed++;
 report.refractedPixels=changed;if(changed<100)throw new Error('Backdrop refraction has no measurable effect');
 await wc.executeJavaScript("document.querySelector('#optical-fixture').remove()");
 // Exercise the same sanitized Markdown/KaTeX conversation renderer as Android.
 await wc.executeJavaScript("document.getElementById('root').style.display='none';const test=document.createElement('main');test.id='content';test.style.width='500px';document.body.append(test)");
 await wc.executeJavaScript(fs.readFileSync(path.join(process.cwd(),'android/app/src/main/assets/renderer.js'),'utf8'));
 report.sharedRenderer=await wc.executeJavaScript(`(()=>{window.showConversation([{role:'user',content:'短问题 <img src=x onerror="window.bad=1">'},{role:'assistant',content:'先看 $a_n=(-1)^n$。'}]);const u=document.querySelector('#content .user'),a=document.querySelector('#content .assistant');const before=u.getBoundingClientRect();const aiLeft=a.getBoundingClientRect().left;const math=!!a.querySelector('.katex');window.showConversation([{role:'user',content:'短问题'},{role:'assistant',content:'先看 $a_n=(-1)^n$，再考虑极限。'}]);return {userRight:before.right,aiLeft,math,scripts:document.querySelectorAll('#content script,#content [onerror]').length,messages:document.querySelectorAll('#content article').length,historyKept:document.querySelector('#content .user').textContent.includes('短问题')};})()`);
 const shared=report.sharedRenderer as any;if(shared.userRight<=shared.aiLeft||!shared.math||shared.scripts||shared.messages!==2||!shared.historyKept)throw new Error('Shared conversation rendering failed');
 report.status='PASS';fs.writeFileSync(path.join(output,'glass.json'),JSON.stringify(report,null,2));
}
