// Extract math before Markdown can consume backslashes, underscores or table pipes.
export type MathPart={token:string;source:string;display:boolean};
export function protectMath(input:string){
 let prefix='JIETIMATHTOKEN';while(input.includes(prefix))prefix+='X';
 const parts:MathPart[]=[];
 const text=input.replace(/(`{3,}|~{3,})[^\n]*\n[\s\S]*?\1|`+[^`\n]*`+|\\\[([\s\S]*?)\\\]|\\\(([\s\S]*?)\\\)|(?<!\\)\$\$([\s\S]*?)(?<!\\)\$\$|(?<![\\$])\$(?!\s|\$)([^\n]*?\S|\S)(?<!\\)\$(?!\$)/g,
 (whole,code,bracket,paren,block,inline)=>{
  if(whole.startsWith('`')||whole.startsWith('~~~'))return whole;
  const token=prefix+parts.length+'END';parts.push({token,source:bracket??paren??block??inline,display:bracket!==undefined||block!==undefined});return token;
 });
 return {text,parts};
}
