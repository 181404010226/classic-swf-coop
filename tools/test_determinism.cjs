const {chromium}=require('playwright');
const assert=require('node:assert/strict'),fs=require('fs'),path=require('path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const pages=await Promise.all([0,1].map(async()=>{const ctx=await browser.newContext({viewport:{width:820,height:620}}),p=await ctx.newPage();p.on('console',m=>{if(/ERROR|PROBE/.test(m.text()))console.log(m.text());});await p.goto('http://127.0.0.1:17694');await p.waitForFunction(()=>window.ready);return p;}));
 await pages[0].waitForTimeout(1500);const results=[];
 for(let node=1;node<=32;node++){
  await Promise.all(pages.map(async p=>{await p.evaluate(n=>call('lanStart',n,12345),node);await p.waitForTimeout(300);await p.waitForFunction(n=>{let s=call('lanState');return s?.node===n&&s?.phase==='play'&&s.frame===0},node,{timeout:10000}).catch(async e=>{console.log('FAILSTATE',await p.evaluate(()=>call('lanState')));throw e;});}));
  const zero=await Promise.all(pages.map(p=>p.evaluate(()=>call('lanState',true))));assert.deepEqual(zero[0],zero[1],`node ${node} initial`);
  const commands=Array.from({length:300},(_,i)=>[i+1,(i%120<40?2:i%120<80?1:0)|(i%50<8?4:0),(i%90<30?2:i%90<60?1:0)|(i%39<5?4:0)]);
  let a=await pages[0].evaluate(c=>c.map(x=>call('lanStep',...x)),commands);
  await pages[1].waitForTimeout(100);
  let b=await pages[1].evaluate(c=>c.map(x=>call('lanStep',...x)),commands);
  assert.equal(a.length,300);for(let i=0;i<a.length;i++){assert.ok(a[i]?.ok,`node ${node} frame ${i+1}`);assert.deepEqual(a[i],b[i],`node ${node} frame ${i+1}`);}
  const frozen=await pages[0].evaluate(()=>call('lanState'));await pages[0].waitForTimeout(120);assert.deepEqual(await pages[0].evaluate(()=>call('lanState')),frozen,`node ${node} idle`);
  results.push({node,frames:300,hash:a.at(-1).hash,phase:a.at(-1).phase,bodyCount:a.at(-1).bodyCount});console.log('PASS',results.at(-1));
 }
 await pages[0].screenshot({path:path.resolve(__dirname,'../build/matrix.png')});
 fs.writeFileSync(path.resolve(__dirname,'../build/determinism.json'),JSON.stringify({date:new Date().toISOString(),runtime:'Edge / Ruffle 0.7.1, two isolated browser contexts',results},null,2));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});







