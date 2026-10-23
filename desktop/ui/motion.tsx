import {useLayoutEffect,useRef,type TextareaHTMLAttributes} from 'react';
import {flushSync} from 'react-dom';

type Transition={skipTransition():void;finished:Promise<void>};
let active:Transition|undefined,sequence=0;
export const reducedMotion=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;

// Only navigation/disclosure changes use snapshots. Typing, sync and streamed
// content stay live, so a response does not restart a transition on every token.
export function moveUI(update:()=>void,kind:'page'|'overlay'='page'){
 const ticket=++sequence;
 active?.skipTransition();
 document.documentElement.dataset.transitionKind=kind;
 const doc=document as Document&{startViewTransition?:(update:()=>void)=>Transition};
 if(reducedMotion()){flushSync(update);return;}
 if(doc.startViewTransition){
  active=doc.startViewTransition(()=>{if(ticket===sequence)flushSync(update);});
  void active.finished.catch(()=>{});
 }else{
  flushSync(update);
  const body=document.querySelector('.page-body');
  body?.animate([{opacity:.3,transform:'translateY(8px)'},{opacity:1,transform:'none'}],{duration:260,easing:'cubic-bezier(.22,.7,.2,1)'});
 }
}

export function MotionTextarea(props:TextareaHTMLAttributes<HTMLTextAreaElement>){
 const field=useRef<HTMLTextAreaElement>(null),height=useRef<number|undefined>(undefined);
 useLayoutEffect(()=>{
  const el=field.current;if(!el)return;
  el.style.transition='none';el.style.height='auto';
  const target=Math.min(440,Math.max(90,el.scrollHeight));
  if(height.current!==undefined&&Math.abs(target-height.current)>1&&!reducedMotion()){
   el.style.height=height.current+'px';void el.offsetHeight;
   el.style.transition='height 220ms cubic-bezier(.22,.7,.2,1), box-shadow 220ms ease';
  }else el.style.transition='box-shadow 220ms ease';
  el.style.height=target+'px';height.current=target;
 },[props.value]);
 return <textarea {...props} ref={field} className={'motion-textarea '+(props.className||'')}/>;
}
