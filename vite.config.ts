import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({root:'desktop/ui',base:'./',plugins:[react()],build:{outDir:'../../dist/ui',emptyOutDir:true},server:{fs:{allow:['../..']}}});
