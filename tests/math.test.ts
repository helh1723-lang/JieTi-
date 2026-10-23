import test from 'node:test';
import assert from 'node:assert/strict';
import {protectMath} from '../shared/math';

test('protects GPT delimiters, matrix rows, underscores and table pipes before Markdown',()=>{
 const source=String.raw`行内 \(a_n=\frac{1}{n}\)，另一个 $x_i$。
\[\begin{pmatrix}1&2\\3&4\end{pmatrix}\]
| 条件 | 公式 |
| --- | --- |
| 支撑 | $\left|x\right|<1$ |
$$\int_0^1 x^2\,dx$$`;
 const result=protectMath(source);assert.equal(result.parts.length,5);
 assert.equal(result.parts.filter(p=>p.display).length,2);
 assert.ok(result.parts[2].source.includes(String.raw`1&2\\3&4`));
 assert.ok(result.parts[3].source.includes('|'));
 assert.ok(!result.text.includes('frac'));
});
test('leaves code samples, escaped currency, incomplete streaming math and ordinary brackets intact',()=>{
 const source='`$x$`\n```latex\n\\[x\\]\n```\n'+String.raw`价格 \$20，普通 [a] (b)，正在流式输出 \(\frac{1}{`;
 const result=protectMath(source);assert.equal(result.parts.length,0);assert.equal(result.text,source);
});
test('handles adjacent formulae and token collisions without rewriting source',()=>{
 const result=protectMath('JIETIMATHTOKEN0END $x$ $y$ $$z$$');
 assert.equal(result.parts.length,3);assert.ok(result.parts.every(p=>p.token.startsWith('JIETIMATHTOKENX')));
 assert.equal(result.parts.map(p=>p.source).join(','),'x,y,z');
});
