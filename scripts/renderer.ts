import {renderMarkdown} from '../shared/markdown';
type ChatMessage={role:string;content:string};
declare global{interface Window{showConversation:(messages:ChatMessage[],animate?:boolean)=>void;showMarkdown:(s:string,followTail?:boolean,animate?:boolean)=>void;showArtifact:(s:string,bootstrap:string,css:string,animate?:boolean)=>void}}
const root=document.getElementById('content')!;
let entrance:Animation|undefined;
function markdown(node:HTMLElement,s:string){node.innerHTML=renderMarkdown(s);}
window.showConversation=(messages,animate=false)=>{
 const follow=root.childElementCount===0||document.documentElement.scrollHeight-window.scrollY-window.innerHeight<90;
 if(root.className!=='conversation'){root.replaceChildren();root.className='conversation';}
 messages.forEach((m,index)=>{let article=root.children[index] as HTMLElement|undefined;
  if(!article){article=document.createElement('article');const label=document.createElement('div');label.className='chat-role';const body=document.createElement('div');body.className='chat-body';article.append(label,body);root.append(article);}
  const key=m.role+'\u0000'+m.content;if(article.dataset.message!==key){article.className='chat-message '+(m.role==='user'?'user':'assistant');article.children[0].textContent=m.role==='user'?'我':'导师';markdown(article.children[1] as HTMLElement,m.content);article.dataset.message=key;}
 });while(root.children.length>messages.length)root.lastElementChild?.remove();
 if(follow)window.scrollTo(0,document.documentElement.scrollHeight);if(animate)reveal(true);
};
function reveal(enabled:boolean){entrance?.cancel();if(enabled&&!matchMedia('(prefers-reduced-motion: reduce)').matches)entrance=root.animate([{opacity:.15,transform:'translateY(6px)'},{opacity:1,transform:'translateY(0)'}],{duration:230,easing:'cubic-bezier(.22,.7,.2,1)'});}
window.showMarkdown=(s:string,followTail=true,animate=false)=>{const follow=followTail&&(root.childElementCount===0||document.documentElement.scrollHeight-window.scrollY-window.innerHeight<90);root.className='';markdown(root,s);if(!followTail)window.scrollTo(0,0);else if(follow)window.scrollTo(0,document.documentElement.scrollHeight);if(animate)reveal(true);};
window.showArtifact=(html:string,bootstrap:string,css:string,animate=false)=>{
 root.className='';const frame=document.createElement('iframe');frame.sandbox.add('allow-scripts');frame.setAttribute('title','交互图解');
 const csp="<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src https://jieti.local data:; connect-src 'none'; frame-src 'none'; form-action 'none'; base-uri 'none';\">";
 frame.srcdoc='<!doctype html><html><head>'+csp+'<meta name="viewport" content="width=device-width, initial-scale=1"><style>'+css+'</style><script>'+bootstrap.replace(/<\/script/gi,'<\\/script')+'</script></head><body>'+html+'</body></html>';root.replaceChildren(frame);window.scrollTo(0,0);reveal(animate);
};
