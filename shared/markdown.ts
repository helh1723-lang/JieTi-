import {marked} from 'marked';
import DOMPurify from 'dompurify';
import katex from 'katex';
import {protectMath} from './math';

export function renderMarkdown(source:string):string {
 const {text,parts}=protectMath(source);
 const safe=DOMPurify.sanitize(marked.parse(text,{async:false}) as string,{FORBID_TAGS:['iframe','script','style','form'],FORBID_ATTR:['style']});
 // Only replace text nodes: a token inside an HTML attribute must never inject markup.
 const template=document.createElement('template');template.innerHTML=safe;
 const walker=document.createTreeWalker(template.content,NodeFilter.SHOW_TEXT);const nodes:Text[]=[];
 while(walker.nextNode())nodes.push(walker.currentNode as Text);
 const byToken=new Map(parts.map(p=>[p.token,p]));
 for(const node of nodes){
  if(node.parentElement?.closest('pre,code'))continue;
  const matches=[...node.data.matchAll(/JIETIMATHTOKENX*\d+END/g)].filter(m=>byToken.has(m[0]));if(!matches.length)continue;
  const fragment=document.createDocumentFragment();let from=0;
  for(const m of matches){fragment.append(node.data.slice(from,m.index));const p=byToken.get(m[0])!;const span=document.createElement(p.display?'div':'span');span.className=p.display?'math-block':'math-inline';span.innerHTML=katex.renderToString(p.source,{displayMode:p.display,throwOnError:false,trust:false,strict:'ignore',maxExpand:1000});fragment.append(span);from=m.index!+m[0].length;}
  fragment.append(node.data.slice(from));node.replaceWith(fragment);
 }
 return template.innerHTML;
}
