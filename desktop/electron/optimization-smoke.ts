import type {BrowserWindow} from 'electron';
import fs from 'node:fs';
import path from 'node:path';
export async function optimizationSmoke(window:BrowserWindow,output:string){
 fs.mkdirSync(output,{recursive:true});fs.rmSync(path.join(output,'desktop.json'),{force:true});const wc=window.webContents;const run=async(s:string)=>{try{return await wc.executeJavaScript(s);}catch(e){throw Error(s+' :: '+String(e));}};const report:Record<string,unknown>={};
 const wait=async(s:string)=>{for(let i=0;i<120;i++){if(await run(`(()=>{try{return Boolean(${s})}catch{return false}})()`))return;await new Promise(r=>setTimeout(r,50));}throw Error('Timeout: '+s);};
 const click=async(text:string)=>{await wait(`[...document.querySelectorAll('button')].some(b=>b.textContent.trim()===${JSON.stringify(text)})`);await run(`[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)}).click()`);};
 await wait("!!document.querySelector('.question-item')");await run("Object.keys(localStorage).filter(k=>k.startsWith('jieti:note-draft:')).forEach(k=>localStorage.removeItem(k))");await run("document.querySelector('.question-item').click()");await click('答疑过程');
 await wait("!!document.querySelector('.message-image img')?.naturalWidth");report.sentImageVisible=true;
 await click('我的笔记');await click('编辑笔记');await wait("!!document.querySelector('.note-editor textarea')");
 await run("(()=>{const input=document.querySelector('.note-editor textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(input,'合成草稿：先判断支撑区间，再写积分上下限。'+Date.now());input.dispatchEvent(new Event('input',{bubbles:true}));})()");
 await wait("document.querySelector('.draft-status').textContent.includes('草稿已保存')");await click('原题与复盘');await click('我的笔记');
 await wait("document.querySelector('.note-editor').textContent.includes('先判断支撑区间')");report.tabDraft=true;
 await wc.reload();await wait("!!document.querySelector('.question-item')");await run("document.querySelector('.question-item').click()");await click('我的笔记');await wait("document.querySelector('.note-editor').textContent.includes('先判断支撑区间')");report.restartDraft=true;
 await click('保存笔记');await wait("document.querySelector('.draft-status').textContent.includes('正式保存')");report.saved=true;
 await click('答疑过程');await new Promise(r=>setTimeout(r,400));fs.writeFileSync(path.join(output,'desktop-light.png'),(await wc.capturePage()).toPNG());
 await run("document.documentElement.dataset.theme='dark'");await new Promise(r=>setTimeout(r,300));fs.writeFileSync(path.join(output,'desktop-dark.png'),(await wc.capturePage()).toPNG());
 await run("document.documentElement.dataset.theme='light'");await click('我的学习记忆');await wait("document.querySelectorAll('.memory-entry').length>0");
 report.continuousMemoryLayout=await run("[...document.querySelectorAll('.memory-entry')].every(n=>getComputedStyle(n).backgroundColor==='rgba(0, 0, 0, 0)'&&getComputedStyle(n).borderRadius==='0px')");
 if(!report.continuousMemoryLayout)throw Error('Memory list has per-record panels');
 await run("document.querySelector('.memory-actions summary').click()");await wait("document.querySelector('.memory-actions[open] .memory-action-menu')");report.memoryActionsRetained=true;await run("document.querySelector('.memory-actions summary').click()");
 await new Promise(r=>setTimeout(r,300));fs.writeFileSync(path.join(output,'memory-light.png'),(await wc.capturePage()).toPNG());
 await run("document.documentElement.dataset.theme='dark'");await new Promise(r=>setTimeout(r,300));fs.writeFileSync(path.join(output,'memory-dark.png'),(await wc.capturePage()).toPNG());
 await run("document.documentElement.dataset.theme='light'");await click('学习规划');await wait("!!document.querySelector('.coach-journal')");
 report.coachJournalVisible=true;await new Promise(r=>setTimeout(r,300));fs.writeFileSync(path.join(output,'coach-light.png'),(await wc.capturePage()).toPNG());
 await run("document.documentElement.dataset.theme='dark'");await new Promise(r=>setTimeout(r,300));fs.writeFileSync(path.join(output,'coach-dark.png'),(await wc.capturePage()).toPNG());
 report.status='PASS';fs.writeFileSync(path.join(output,'desktop.json'),JSON.stringify(report,null,2));
}
