import {protectMath} from './math';
// Display-only compatibility for legacy plain-text math. Stored records stay intact.
export function studyText(input:string):string {
 const protectedMath=protectMath(input);
 let text=protectedMath.text.replace(/(`{3,}|~{3,})[^\n]*\n[\s\S]*?\1|`+[^`\n]*`+|https?:\/\/[^\s<>]+|[A-Za-zΑ-ω0-9_{}()[\],.+*/=<>≤≥−∼~²³→∞∈∩∪⇒Φφμσπ|\\^;-]+(?:[ \t]+[A-Za-zΑ-ω0-9_{}()[\],.+*/=<>≤≥−∼~²³→∞∈∩∪⇒Φφμσπ|\\^;-]+)*/g,whole=>{
  if(whole.startsWith('`')||whole.startsWith('~~~')||whole.startsWith('http')||whole.includes('JIETIMATHTOKEN'))return whole;
  if(!/[Φφμσπ∞≤≥∼²³∩∪⇒]|[A-Za-z]_?\d|[A-Za-z][^\n]*[=<>]/.test(whole))return whole;
  let value=whole.replace(/(?<![_^])\{([^{}]*)\}/g,'\\{$1\\}').replace(/([A-Za-z])([0-9]+)/g,'$1_{$2}').replace(/Φ/g,'\\Phi ').replace(/φ/g,'\\phi ').replace(/μ/g,'\\mu ').replace(/σ/g,'\\sigma ').replace(/π/g,'\\pi ').replace(/≤/g,'\\le ').replace(/≥/g,'\\ge ').replace(/[∼~]/g,'\\sim ').replace(/−/g,'-').replace(/²/g,'^{2}').replace(/³/g,'^{3}').replace(/∞/g,'\\infty ').replace(/→/g,'\\to ').replace(/⇒/g,'\\Rightarrow ').replace(/∩/g,'\\cap ').replace(/∪/g,'\\cup ').replace(/∈/g,'\\in ');
  value=value.replace(/\b(min|max)(?=\\?\{|\()/g,'\\$1');
  return value.length>80?'\\['+value.trim()+'\\]':'$'+value.trim()+'$';
 });
 for(const part of protectedMath.parts)text=text.replace(part.token,()=>(part.display?'$$':'$')+part.source+(part.display?'$$':'$'));
 return text;
}
