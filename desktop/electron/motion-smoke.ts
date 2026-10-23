import type {BrowserWindow} from 'electron';
import fs from 'node:fs';
import path from 'node:path';

// Local UI acceptance only. Uses the already loaded synthetic learning profile;
// no API requests, note saves, pairing or external navigation are performed.
export async function motionSmoke(window:BrowserWindow,output:string){
 fs.mkdirSync(output,{recursive:true});const wc=window.webContents;
 const pause=(ms:number)=>new Promise(r=>setTimeout(r,ms));
 const click=(label:string)=>wc.executeJavaScript(`[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(label)}).click()`);
 const report:Record<string,unknown>={};
 wc.debugger.attach('1.3');
 try{
  await wc.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  await wc.executeJavaScript('document.fonts.ready.then(()=>true)');await pause(700);
  report.viewTransitions=await wc.executeJavaScript("typeof document.startViewTransition==='function'");
  const frames:{ms:number,png:Buffer}[]=[];const start=Date.now();let previous=-100;
  wc.beginFrameSubscription(false,frame=>{const ms=Date.now()-start;if(ms-previous>=25&&frames.length<25){previous=ms;frames.push({ms,png:frame.toPNG()});}});
  await click('我的学习记忆');await pause(65);
  report.activeAnimations=await wc.executeJavaScript("document.getAnimations().map(a=>({name:a.animationName||'',duration:a.effect?.getTiming().duration,pseudo:a.effect?.pseudoElement||''}))");
  await pause(420);wc.endFrameSubscription();
  if(!(report.activeAnimations as unknown[]).length)throw new Error('No running page animation was observed');
  for(let i=0;i<frames.length;i++)fs.writeFileSync(path.join(output,`page-${String(i).padStart(2,'0')}.png`),frames[i].png);
  fs.writeFileSync(path.join(output,'frames.json'),JSON.stringify(frames.map((f,i)=>({file:`page-${String(i).padStart(2,'0')}.png`,ms:f.ms})),null,2));
  report.recordedFrames=frames.length;
  // An interrupted transition must commit the latest navigation request.
  await wc.executeJavaScript("for(const label of ['课程资料库','导师进化','题目与复盘'])[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===label).click()");
  await pause(500);
  report.rapidNavigation=await wc.executeJavaScript("document.querySelector('.nav-item.active')?.textContent.trim()");
  if(report.rapidNavigation!=='题目与复盘')throw new Error('Rapid navigation lost the latest request');
  await wc.executeJavaScript("document.querySelector('.question-item').click()");await pause(450);
  await wc.executeJavaScript("document.querySelector('[aria-label=\"编辑题目标题\"]').click()");await pause(75);
  report.modalAnimations=await wc.executeJavaScript("document.getAnimations().map(a=>a.animationName||'').filter(n=>n==='sheet-in'||n==='veil-appear')");
  if((report.modalAnimations as string[]).length!==2)throw new Error('Modal and backdrop transition did not run together');
  await wc.executeJavaScript("document.querySelector('[aria-label=\"关闭编辑\"]').click()");await pause(400);
  if(await wc.executeJavaScript("!!document.querySelector('.edit-modal')"))throw new Error('Interrupted modal did not close');
  await click('我的笔记');await pause(400);
  const before=await wc.executeJavaScript("document.querySelector('.note-editor textarea').getBoundingClientRect().height");
  await wc.executeJavaScript("(()=>{const el=document.querySelector('.note-editor textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(el,Array(12).fill('本地动效验收草稿，不会保存。').join('\\n'));el.dispatchEvent(new Event('input',{bubbles:true}));})()");
  await pause(60);
  const middle=await wc.executeJavaScript("document.querySelector('.note-editor textarea').getBoundingClientRect().height");await pause(260);
  const after=await wc.executeJavaScript("document.querySelector('.note-editor textarea').getBoundingClientRect().height");
  report.textareaHeights={before,middle,after};
  if(!(before<middle&&middle<after))throw new Error('Textarea resize did not interpolate');
  // Emulating this media preference changes only this local renderer, not OS settings.
  await wc.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await click('课程资料库');await pause(70);
  report.reducedAnimationCount=await wc.executeJavaScript('document.getAnimations().length');
  if(report.reducedAnimationCount!==0)throw new Error('Reduced motion still animates');
  report.status='PASS';fs.writeFileSync(path.join(output,'desktop-motion.json'),JSON.stringify(report,null,2));
 }finally{wc.endFrameSubscription();await wc.debugger.sendCommand('Emulation.setEmulatedMedia',{features:[]});wc.debugger.detach();}
}
