import fs from 'node:fs';
const schema=JSON.parse(fs.readFileSync('shared/protocol.schema.json','utf8'));
const ts=t=>t.type==='array'?`${ts(t.items)}[]`:t.enum?t.enum.map(JSON.stringify).join('|'):({integer:'number',number:'number',boolean:'boolean',object:'Record<string,unknown>'}[t.type]||'string');
const kt=t=>t.type==='array'?`List<${kt(t.items)}>`:({integer:'Int',number:'Double',boolean:'Boolean',object:'JsonObject'}[t.type]||'String');
const def=t=>t.type==='array'?'emptyList()':({integer:'0',number:'0.0',boolean:'false',object:'JsonObject(emptyMap())'}[t.type]||'""');
let types='// Generated from shared/protocol.schema.json. Do not edit.\n';
let kotlin='// Generated from shared/protocol.schema.json.\npackage cn.jieti.core\nimport kotlinx.serialization.Serializable\nimport kotlinx.serialization.json.JsonObject\n';
for(const [name,obj] of Object.entries(schema.definitions)){
types+=`export interface ${name} { ${Object.entries(obj.properties).map(([k,v])=>`${k}: ${ts(v)}`).join('; ')} }\n`;
kotlin+=`@Serializable data class ${name}(\n${Object.entries(obj.properties).map(([k,v])=>` val ${k}: ${kt(v)} = ${def(v)}`).join(',\n')}\n)\n`;
}
types+=`export type Kind = ${schema.properties.kind.enum.map(JSON.stringify).join('|')};\nexport interface SyncRecord {id:string;kind:Kind;rev:string;baseRev:string;payload:Record<string,unknown>;deleted:boolean;updatedAt:string;deviceId:string}\n`;
kotlin+='@Serializable data class SyncRecord(val id:String,val kind:String,val rev:String,val baseRev:String="",val payload:JsonObject,val deleted:Boolean=false,val updatedAt:String,val deviceId:String)\n';
fs.mkdirSync('shared/generated',{recursive:true});fs.writeFileSync('shared/generated/protocol.ts',types);
const target='android/app/src/main/java/cn/jieti/core';fs.mkdirSync(target,{recursive:true});fs.writeFileSync(`${target}/Protocol.kt`,kotlin);
fs.mkdirSync('android/app/src/main/assets',{recursive:true});fs.copyFileSync('shared/protocol.schema.json','android/app/src/main/assets/protocol.schema.json');
console.log('Generated TypeScript and Kotlin protocol v'+schema.version);
