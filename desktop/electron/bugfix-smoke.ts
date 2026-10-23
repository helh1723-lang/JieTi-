import type {BrowserWindow} from 'electron';
import fs from 'node:fs';
import path from 'node:path';

export async function bugfixSmoke(window:BrowserWindow,output:string){
 fs.mkdirSync(output,{recursive:true});const wc=window.webContents;const report:Record<string,unknown>={};
 const run=(js:string)=>wc.executeJavaScript(js);
 const wait=async(js:string)=>{for(let i=0;i<100;i++){if(await run(js))return;await new Promise(r=>setTimeout(r,50));}throw new Error('Timed out: '+js);};
 const clickText=async(text:string)=>run(`[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)}).click()`);
 await wait("!!document.querySelector('.question-item')");await run("document.querySelector('.question-item').click()");await wait("!!document.querySelector('[aria-label=删除题目]')");
 await clickText('答疑过程');await wait("document.querySelectorAll('.message .katex').length>=2");
 report.desktopMath=await run("({math:document.querySelectorAll('.message .katex').length,errors:document.querySelectorAll('.katex-error').length})");
 if((report.desktopMath as any).errors)throw new Error('Desktop formula errors');
 await new Promise(r=>setTimeout(r,650));
 fs.writeFileSync(path.join(output,'desktop-formulas.png'),(await wc.capturePage()).toPNG());
 await run("document.querySelector('[aria-label=编辑题目标题]').click()");await wait("!!document.querySelector('.edit-modal textarea')");
 await run("(()=>{const e=document.querySelector('.edit-modal textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,'手工修正标题');e.dispatchEvent(new Event('input',{bubbles:true}));})()");await clickText('保存修改');await wait("!document.querySelector('.edit-modal')");
 const snapshot=await run('window.jieti.snapshot()');if(!snapshot.records.some((r:any)=>r.kind==='question'&&r.payload.title==='手工修正标题'))throw new Error('Question edit failed');report.questionEdit=true;
 await clickText('课程资料库');await wait("!!document.querySelector('.document-row')");await clickText('补充纠正');await wait("!!document.querySelector('.edit-modal textarea')");
 await run("(()=>{const e=document.querySelector('.edit-modal textarea');Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype,'value').set.call(e,'纠正：本例支撑区间为零到一。');e.dispatchEvent(new Event('input',{bubbles:true}));})()");await clickText('保存修改');await wait("!document.querySelector('.edit-modal')");
 report.documentEdit=(await run('window.jieti.snapshot()')).records.some((r:any)=>r.kind==='document'&&r.payload.supplementalText?.includes('零到一'));if(!report.documentEdit)throw new Error('Document edit failed');
 await new Promise(r=>setTimeout(r,650));
 fs.writeFileSync(path.join(output,'desktop-management.png'),(await wc.capturePage()).toPNG());
 await run("document.querySelector('.document-row .danger').click()");await wait("!!document.querySelector('[role=alertdialog]')");await clickText('取消');await wait("!document.querySelector('[role=alertdialog]')");if(!(await run("!!document.querySelector('.document-row')")))throw new Error('Cancel removed a document');
 await run("document.querySelector('.document-row .danger').click()");await clickText('确认删除');await wait("!document.querySelector('.document-row')");report.documentDelete=true;
 await clickText('题目与复盘');await wait("!!document.querySelector('[aria-label=删除题目]')");await run("document.querySelector('[aria-label=删除题目]').click()");await clickText('确认删除');await wait("!document.querySelector('.question-item')");report.questionDelete=true;
 // Run the exact WebView bundle, including real KaTeX and sanitizer, in Chromium.
 await run("document.getElementById('root').style.display='none';const root=document.createElement('main');root.id='content';document.body.append(root)");
 await run(fs.readFileSync(path.join(process.cwd(),'android/app/src/main/assets/renderer.js'),'utf8'));
 const formula=String.raw`行内 \(a_n=\frac{1}{n}\) 与 $x_i$。
\[\begin{pmatrix}1&2\\3&4\end{pmatrix}\]
| 条件 | 公式 |
| --- | --- |
| 支撑 | $\left|x\right|<1$ |
$$\int_0^1 x^2\,dx$$

`+'`$code$` <img src=x onerror="window.bad=1">';
 await run(`window.showConversation(${JSON.stringify([{role:'user',content:'第一问'},{role:'assistant',content:formula}])});window.originalMessage=document.querySelector('#content article');`);
 report.webViewMath=await run("({math:document.querySelectorAll('#content .katex').length,errors:document.querySelectorAll('#content .katex-error').length,tableCells:document.querySelector('#content tbody tr').children.length,code:document.querySelector('#content code').textContent,scripts:document.querySelectorAll('#content script,#content [onerror]').length,tokens:document.querySelector('#content').textContent.includes('JIETIMATHTOKEN')})");
 const math=report.webViewMath as any;if(math.math!==5||math.errors||math.tableCells!==2||math.code!=='$code$'||math.scripts||math.tokens)throw new Error('WebView math failed: '+JSON.stringify(math));
 await run(`window.showConversation(${JSON.stringify([{role:'user',content:'第一问'},{role:'assistant',content:formula+'继续流式回答。'}])})`);
 report.keepsMessageNode=await run("window.originalMessage===document.querySelector('#content article')");if(!report.keepsMessageNode)throw new Error('Streaming rebuilt old messages');
 report.status='PASS';fs.writeFileSync(path.join(output,'results.json'),JSON.stringify(report,null,2));
}
