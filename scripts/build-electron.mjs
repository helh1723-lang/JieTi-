import {build} from 'esbuild';
import fs from 'node:fs';
await build({entryPoints:['desktop/electron/main.ts'],outfile:'dist/electron/main.cjs',bundle:true,platform:'node',format:'cjs',external:['electron','sql.js'],loader:{'.md':'text'}});
await build({entryPoints:['desktop/electron/preload.ts'],outfile:'dist/electron/preload.cjs',bundle:true,platform:'node',format:'cjs',external:['electron']});
await build({entryPoints:['scripts/renderer.ts'],outfile:'android/app/src/main/assets/renderer.js',bundle:true,platform:'browser',format:'iife',target:'chrome95',minify:true});
function copyTree(src,dest){fs.mkdirSync(dest,{recursive:true});for(const item of fs.readdirSync(src,{withFileTypes:true})){if(item.isDirectory())copyTree(`${src}/${item.name}`,`${dest}/${item.name}`);else fs.writeFileSync(`${dest}/${item.name}`,fs.readFileSync(`${src}/${item.name}`));}}
copyTree('node_modules/katex/dist','android/app/src/main/assets/katex');
