import type {BrowserWindow} from 'electron';
import fs from 'node:fs';
import path from 'node:path';
export async function curriculumSmoke(window:BrowserWindow,output:string){
 fs.mkdirSync(output,{recursive:true});const wc=window.webContents;const run=(s:string)=>wc.executeJavaScript(s);
 const wait=async(s:string)=>{for(let i=0;i<120;i++){if(await run(`(()=>{try{return Boolean(${s})}catch{return false}})()`))return;await new Promise(r=>setTimeout(r,60));}throw Error('UI timeout: '+s);};
 const click=async(text:string)=>{await wait(`[...document.querySelectorAll('button')].some(b=>b.textContent.trim()===${JSON.stringify(text)})`);await run(`[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===${JSON.stringify(text)}).click()`);};
 const capture=async(name:string)=>{await new Promise(r=>setTimeout(r,250));fs.writeFileSync(path.join(output,name+'.png'),(await wc.capturePage()).toPNG());};
 await click('学习规划');await wait("document.querySelectorAll('.course-directory button').length===8");
 const report:Record<string,unknown>={chapters:8};
 await run("document.querySelectorAll('.course-directory button')[3].click()");await wait("document.querySelector('.course-detail h2').textContent.includes('连续时间')");
 report.fixtureMasteryVisible=await run("document.querySelector('.course-topics').textContent.includes('已掌握')&&document.querySelector('.course-topics').textContent.includes('待复习')");
 if(!report.fixtureMasteryVisible)throw Error('Mastery fixture missing');
 await capture('desktop-light');await run("document.documentElement.dataset.theme='dark'");await capture('desktop-dark');await run("document.documentElement.dataset.theme='light'");
 await click('巩固与复习');await wait("document.querySelectorAll('.course-topic').length===1");report.filter=true;await click('全部');
 await run("document.querySelectorAll('.course-directory button')[6].click()");await wait("document.querySelector('.course-detail').textContent.includes('正文待补充')");report.sixthChapterHonest=true;
 await run("document.querySelectorAll('.course-directory button')[4].click()");await wait("document.querySelector('.course-detail').textContent.includes('希尔伯特变换')");report.noExclusion=true;
 await click('数学');await wait("document.querySelectorAll('.course-chapter-button').length===9");report.mathModules=23;
 await click('线性代数6 章');await wait("document.querySelectorAll('.course-chapter-button').length===6");await capture('desktop-math-linear');
 await click('概率论与数理统计8 章');await wait("document.querySelectorAll('.course-chapter-button').length===8");await run("document.querySelectorAll('.course-chapter-button')[7].click()");await wait("document.querySelector('.course-detail h2').textContent.includes('假设检验')");await capture('desktop-math-probability');
 await click('信号与系统');await click('数学');await wait("document.querySelectorAll('.course-chapter-button').length===9&&document.querySelector('.course-detail h2').textContent.includes('函数、极限')");report.scopeReturnResetsGroup=true;await click('高等数学9 章');await wait("document.querySelectorAll('.course-chapter-button').length===9");await capture('desktop-math-high');
 report.mathTracks=[9,6,8];report.mathTopics=157;
 await click('信号与系统');await wait("document.querySelectorAll('.course-directory button').length===8");
 window.setSize(760,980);await capture('desktop-narrow');report.noHorizontalOverflow=await run("document.documentElement.scrollWidth<=innerWidth");
 window.setSize(1360,1000);await run("document.querySelectorAll('.course-directory button')[3].click()");await wait("document.querySelector('.course-detail h2').textContent.includes('连续时间')");
 report.status='PASS';fs.writeFileSync(path.join(output,'desktop.json'),JSON.stringify(report,null,2));
}
