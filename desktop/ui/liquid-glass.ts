// A convex lens displacement map: flat centre, refracting rounded perimeter.
// This samples only the renderer's own backdrop, never the desktop or files.
const ns='http://www.w3.org/2000/svg';
let sequence=0;
function lensMap(width:number,height:number,radius:number){
 const ratio=Math.min(1,512/Math.max(width,height));const w=Math.max(2,Math.round(width*ratio)),h=Math.max(2,Math.round(height*ratio));
 const canvas=document.createElement('canvas');canvas.width=w;canvas.height=h;const ctx=canvas.getContext('2d')!;const image=ctx.createImageData(w,h);const r=Math.min(radius*ratio,w/2,h/2),rim=Math.min(12*ratio,r*.65);
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){
  const px=x-w/2,py=y-h/2;const qx=Math.abs(px)-(w/2-r),qy=Math.abs(py)-(h/2-r);const ox=Math.max(qx,0),oy=Math.max(qy,0);const length=Math.hypot(ox,oy);
  const d=length+Math.min(Math.max(qx,qy),0)-r;
  let nx=0,ny=0;if(length>0){nx=ox/length*Math.sign(px);ny=oy/length*Math.sign(py);}else if(qx>qy)nx=Math.sign(px);else ny=Math.sign(py);
  const bend=d<=0?Math.pow(Math.max(0,1+d/Math.max(rim,1)),1.6):0;const i=(y*w+x)*4;
  image.data[i]=128+Math.round(nx*bend*115);image.data[i+1]=128+Math.round(ny*bend*115);image.data[i+2]=128;image.data[i+3]=255;
 }
 ctx.putImageData(image,0,0);return canvas.toDataURL();
}
export function installLiquidGlass(){
 const svg=document.createElementNS(ns,'svg');svg.setAttribute('aria-hidden','true');svg.style.cssText='position:fixed;width:0;height:0;pointer-events:none';const defs=document.createElementNS(ns,'defs');svg.append(defs);document.body.append(svg);
 const attached=new Map<HTMLElement,{filter:SVGFilterElement;observer:ResizeObserver}>();
 const selector='.sidebar nav,.nav-active-pill,.button.primary,.note-editor textarea,.edit-modal,.toast';
 const scan=()=>{
  for(const [el,item]of attached)if(!el.isConnected){item.observer.disconnect();item.filter.remove();attached.delete(el);}
  document.querySelectorAll<HTMLElement>(selector).forEach(el=>{
   if(attached.has(el))return;const id='liquid-lens-'+sequence++;const filter=document.createElementNS(ns,'filter');filter.id=id;filter.setAttribute('color-interpolation-filters','sRGB');filter.setAttribute('x','0%');filter.setAttribute('y','0%');filter.setAttribute('width','100%');filter.setAttribute('height','100%');
   const map=document.createElementNS(ns,'feImage');map.setAttribute('result','lens');map.setAttribute('preserveAspectRatio','none');map.setAttribute('width','100%');map.setAttribute('height','100%');
   const bend=document.createElementNS(ns,'feDisplacementMap');bend.setAttribute('in','SourceGraphic');bend.setAttribute('in2','lens');bend.setAttribute('scale','16');bend.setAttribute('xChannelSelector','R');bend.setAttribute('yChannelSelector','G');filter.append(map,bend);defs.append(filter);
   let previous='';const observer=new ResizeObserver(()=>{const box=el.getBoundingClientRect();const key=Math.round(box.width)+':'+Math.round(box.height);if(box.width<2||box.height<2||previous===key)return;previous=key;map.setAttribute('href',lensMap(box.width,box.height,parseFloat(getComputedStyle(el).borderRadius)||28));});observer.observe(el);
   el.classList.add('liquid-glass');el.style.setProperty('--lens',`url(#${id})`);attached.set(el,{filter,observer});
  });
 };
 let queued=false;new MutationObserver(()=>{if(!queued){queued=true;requestAnimationFrame(()=>{queued=false;scan();});}}).observe(document.getElementById('root')!,{childList:true,subtree:true});scan();
}
