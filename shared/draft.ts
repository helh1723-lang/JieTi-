export type Draft={value:string;baseRev:string};
export function readDraft(storage:Pick<Storage,'getItem'>,key:string):Draft|undefined{
 try{const d=JSON.parse(storage.getItem(key)||'null');if(d&&typeof d.value==='string'&&typeof d.baseRev==='string')return d;}catch{}return undefined;
}
export function writeDraft(storage:Pick<Storage,'setItem'>,key:string,draft:Draft):boolean{
 try{storage.setItem(key,JSON.stringify(draft));return true;}catch{return false;}
}
