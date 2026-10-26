import fs from 'node:fs';
const catalog=JSON.parse(fs.readFileSync('shared/curriculum.json','utf8'));
const q=value=>JSON.stringify(value).replace(/\$/g,'\\$');
const list=values=>`listOf(${values.join(',')})`;
const chapters=catalog.chapters.map(c=>`CourseChapter(${[c.id,c.scope,c.number,c.title,c.source,c.availability,c.goal].map(q).join(',')},${list(c.topics.map(t=>`CourseTopic(${q(t.id)},${q(t.title)},${list(t.aliases.map(q))},${t.page})`))},${q(c.group||'')},${c.startPage||1})`);
fs.writeFileSync('android/app/src/main/java/cn/jieti/core/CurriculumCatalog.kt',`// Generated from shared/curriculum.json. Do not edit.\npackage cn.jieti.core\ndata class CourseTopic(val id:String,val title:String,val aliases:List<String>,val page:Int)\ndata class CourseChapter(val id:String,val scope:String,val number:String,val title:String,val source:String,val availability:String,val goal:String,val topics:List<CourseTopic>,val group:String="",val startPage:Int=1)\nobject CurriculumCatalog { const val version=${q(catalog.version)};val chapters=${list(chapters)} }\n`);
