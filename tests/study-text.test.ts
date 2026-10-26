import {test} from 'node:test';
import assert from 'node:assert/strict';
import katex from 'katex';
import {studyText} from '../shared/study-text';
import {protectMath} from '../shared/math';
test('legacy tablet probability formulae render without rewriting Chinese or code',()=>{
 const source='设Φ为标准正态分布函数。由于X3与X1,X2独立。\n（Ⅰ）F_{X1,Y}(x,y)=P(X1≤x,Y≤y)=1/2Φ(x)Φ(y)+1/2Φ(min{x,y})。\n（Ⅱ）Y∼N(0,1)。\n`X1=2`';
 const result=studyText(source);assert.ok(result.includes('标准正态分布函数。'));assert.ok(result.includes('`X1=2`'));
 const {parts}=protectMath(result);assert.ok(parts.length>=4);
 for(const part of parts)assert.doesNotThrow(()=>katex.renderToString(part.source,{throwOnError:true}));
 assert.ok(parts.some(p=>p.source.includes('X_{1}')));assert.ok(parts.some(p=>p.source.includes('\\Phi')));
 assert.ok(studyText('概率P{X1<x,X2<y}。').includes('P\\{X_{1}<x,X_{2}<y\\}'));
});
test('preserves standard math, ordinary English, URLs, brackets and fenced code',()=>{
 const text='论文 ResNet architecture uses residual connections. [说明](https://example.com)\n\\[F_{X,Y}(x,y)=\\frac12\\Phi(x)\\]\n```text\nX1=3\n```';
 const result=studyText(text);assert.ok(result.includes('论文 ResNet architecture uses residual connections.'));assert.ok(result.includes('[说明](https://example.com)'));assert.ok(result.includes('```text\nX1=3\n```'));assert.equal(protectMath(result).parts[0].source,'F_{X,Y}(x,y)=\\frac12\\Phi(x)');assert.equal(protectMath(result).parts[0].display,true);
});
