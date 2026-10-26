import {renderMarkdown} from '../shared/markdown';
import {studyText} from '../shared/study-text';
type ChatMessage={id?:string;role:string;content?:string;append?:string;assetIds?:string[];completion?:string};
type Diagram={id:string;sourceMessageId:string;html:string;summary:string};
declare global{interface Window{setMathResources:(js:string,css:string)=>void;patchConversation:(updates:ChatMessage[],order:string[]|null,artifacts:Diagram[]|null,animate?:boolean)=>void;showConversation:(messages:ChatMessage[],animate?:boolean,artifacts?:Diagram[],bootstrap?:string,css?:string)=>void;showMarkdown:(s:string,followTail?:boolean,animate?:boolean,study?:boolean)=>void;showArtifact:(s:string,bootstrap:string,css:string,animate?:boolean)=>void}}
const root=document.getElementById('content')!;
let entrance:Animation|undefined,mathBootstrap='',mathStyles='';
window.setMathResources=(js,css)=>{mathBootstrap=js;mathStyles=css;};
const articles=new Map<string,HTMLElement>();let threadDiagrams:Diagram[]=[];
function markdown(node:HTMLElement,s:string){node.innerHTML=renderMarkdown(s);}
function drawDiagrams(article:HTMLElement,id:string){
 const diagrams=threadDiagrams.filter(a=>article.dataset.role==='assistant'&&a.sourceMessageId===id);const key=JSON.stringify(diagrams);
 if(article.dataset.diagrams===key)return;
 article.querySelector('.inline-diagrams')?.remove();if(diagrams.length){const group=document.createElement('section');group.className='inline-diagrams';for(const a of diagrams){const caption=document.createElement('p');caption.className='diagram-caption';caption.textContent=a.summary;group.append(caption,artifactFrame(a.html,mathBootstrap,mathStyles));}article.append(group);}article.dataset.diagrams=key;
}
window.patchConversation=(updates,order,artifacts,animate=false)=>{
 const follow=root.childElementCount===0||document.documentElement.scrollHeight-window.scrollY-window.innerHeight<90;
 if(root.className!=='conversation'){root.replaceChildren();root.className='conversation';articles.clear();threadDiagrams=[];}
 if(artifacts!==null)threadDiagrams=artifacts;
 for(const m of updates){const id=m.id!;let article=articles.get(id);
  if(!article){article=document.createElement('article');article.dataset.id=id;const label=document.createElement('div');label.className='chat-role';const body=document.createElement('div');body.className='chat-body';const images=document.createElement('div');images.className='chat-images';const status=document.createElement('p');status.className='completion-status';article.append(label,body,images,status);articles.set(id,article);root.append(article);}
  article.className='chat-message '+(m.role==='user'?'user':'assistant');article.dataset.role=m.role;article.children[0].textContent=m.role==='user'?'我':'导师';
  const content=m.append!==undefined?(article.dataset.content||'')+m.append:(m.content||'');
  if(article.dataset.content!==content){markdown(article.children[1] as HTMLElement,content);article.dataset.content=content;}
  const hashes=(m.assetIds||[]).filter(h=>/^[a-f0-9]{64}$/.test(h));const imageKey=hashes.join(',');
  if(article.dataset.images!==imageKey){const images=article.children[2];images.replaceChildren();for(const hash of hashes){const detail=document.createElement('details');detail.open=true;const label=document.createElement('summary');label.textContent='已发送的图片 · 点击折叠';const img=document.createElement('img');img.src='https://jieti.local/media/'+hash;img.alt='我发送的图片';img.loading='lazy';const open=document.createElement('a');open.href='https://jieti.local/focus/'+hash+'?source='+encodeURIComponent(id);open.setAttribute('aria-label','放大图片并圈选追问');open.append(img);const hint=document.createElement('small');hint.textContent='点击图片放大 / 圈选追问';detail.append(label,open,hint);images.append(detail);}article.dataset.images=imageKey;}
  article.children[3].textContent=m.completion&&m.completion!=='complete'?'未完成回答 · 不计入学习证据':'';drawDiagrams(article,id);
 }
 if(order!==null){const keep=new Set(order);for(const [id,node]of articles)if(!keep.has(id)){node.remove();articles.delete(id);}order.forEach((id,i)=>{const node=articles.get(id);if(node&&root.children[i]!==node)root.insertBefore(node,root.children[i]||null);});}
 if(artifacts!==null)for(const [id,node]of articles)drawDiagrams(node,id);
 if(follow)window.scrollTo(0,document.documentElement.scrollHeight);if(animate)reveal(true);
};
window.showConversation=(messages,animate=false,artifacts=[],bootstrap='',css='')=>{
 if(bootstrap)window.setMathResources(bootstrap,css);
 const rows=messages.map((m,i)=>({...m,id:m.id||String(i)}));window.patchConversation(rows,rows.map(m=>m.id),artifacts,animate);
};
function reveal(enabled:boolean){entrance?.cancel();if(enabled&&!matchMedia('(prefers-reduced-motion: reduce)').matches)entrance=root.animate([{opacity:.15,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}],{duration:230,easing:'cubic-bezier(.22,.7,.2,1)'});}
window.showMarkdown=(s:string,followTail=true,animate=false,study=false)=>{const follow=followTail&&(root.childElementCount===0||document.documentElement.scrollHeight-window.scrollY-window.innerHeight<90);root.className='';markdown(root,study?studyText(s):s);if(!followTail)window.scrollTo(0,0);else if(follow)window.scrollTo(0,document.documentElement.scrollHeight);if(animate)reveal(true);};
function artifactFrame(html:string,bootstrap:string,css:string){
 const frame=document.createElement('iframe');frame.sandbox.add('allow-scripts');frame.setAttribute('title','交互图解');
 const csp="<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src https://jieti.local data:; connect-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none';\">";
 const auto="<script>document.addEventListener('DOMContentLoaded',()=>{try{renderMathInElement(document.body,{delimiters:[{left:'$$',right:'$$',display:true},{left:'\\\\[',right:'\\\\]',display:true},{left:'\\\\(',right:'\\\\)',display:false},{left:'$',right:'$',display:false}],throwOnError:false});}catch(e){}});</script>";
 frame.srcdoc='<!doctype html><html><head>'+csp+'<meta name="viewport" content="width=device-width, initial-scale=1"><style>html,body{background:transparent;margin:0}*{box-sizing:border-box}svg,canvas{max-width:100%}.katex-display{overflow-x:auto;overflow-y:hidden}'+css+'</style><script>'+bootstrap.replace(/<\/script/gi,'<\\/script')+'</script>'+auto+'</head><body>'+html+'</body></html>';return frame;
}
window.showArtifact=(html:string,bootstrap:string,css:string,animate=false)=>{
 root.className='';root.replaceChildren(artifactFrame(html,bootstrap,css));window.scrollTo(0,0);reveal(animate);
};
