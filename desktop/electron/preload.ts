import {contextBridge,ipcRenderer} from 'electron';
contextBridge.exposeInMainWorld('jieti',{
 snapshot:()=>ipcRenderer.invoke('snapshot'),
 edit:(id:string,changes:Record<string,unknown>,expectedRev?:string)=>ipcRenderer.invoke('edit',id,changes,expectedRev),
 forget:(id:string)=>ipcRenderer.invoke('forget',id),
 delete:(id:string,expectedRev:string)=>ipcRenderer.invoke('delete',id,expectedRev),
 sync:(start:boolean)=>ipcRenderer.invoke('sync',start),
 importDocuments:(scope:string)=>ipcRenderer.invoke('import',scope),
 export:(ids?:string[])=>ipcRenderer.invoke('export',ids),
 note:(questionId:string,content:string,expectedRev?:string)=>ipcRenderer.invoke('note',questionId,content,expectedRev),
 resolve:(id:string,side:'local'|'incoming')=>ipcRenderer.invoke('resolve',id,side),
 policy:(id:string)=>ipcRenderer.invoke('policy',id),
 onChange:(fn:()=>void)=>{const cb=()=>fn();ipcRenderer.on('changed',cb);return()=>ipcRenderer.removeListener('changed',cb);}
});
