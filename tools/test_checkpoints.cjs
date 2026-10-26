const{chromium}=require('playwright'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const errors=[];
 const pages=await Promise.all([0,1].map(async()=>{const ctx=await browser.newContext(),p=await ctx.newPage();p.on('console',m=>{if(/Error in AVM|Error dispatching|Error running AVM/.test(m.text()))errors.push(m.text());});await p.goto('http://127.0.0.1:17694');await p.waitForFunction(()=>window.ready);return p;}));
 for(const p of pages){await p.evaluate(()=>call('lanStart',1,12345));await p.waitForFunction(()=>call('lanState')?.phase==='play'&&call('lanState')?.frame===0);}
 const run=()=>{const commands=[],events=[];let frame=0;
  function step(fire,water){commands.push([++frame,fire,water]);const s=call('lanStep',frame,fire,water);if(!s?.ok)throw Error(JSON.stringify(s));return s;}
  let s;for(let i=0;i<30;i++)s=step(0,0);events.push({kind:'grounded',state:s});
  for(let i=0;i<72;i++)s=step(2,0);events.push({kind:'gem-before-death',state:s});const before=s;
  for(let i=0;i<130&&s.respawns[1]===0;i++)s=step(0,2);events.push({kind:'water-respawn',state:s});const waterDeath=s;
  for(let i=0;i<25;i++)s=step(0,2);events.push({kind:'held-input-stopped',state:s});
  s=step(0,0);for(let i=0;i<8;i++)s=step(0,1);events.push({kind:'water-can-move-again',state:s});
  for(let i=0;i<160&&s.respawns[0]===0;i++)s=step(2,0);events.push({kind:'fire-respawn',state:s});
  s=step(0,0);for(let i=0;i<20;i++)s=step(0,0);
  return{commands,events,final:s,before,waterDeath};};
 const result=await pages[0].evaluate(run);
 const e=Object.fromEntries(result.events.map(x=>[x.kind,x.state]));
 assert.ok(e.grounded.checkpoints.every(c=>c[3]),'both actors record actual supported checkpoints');assert.ok(result.before.red>0,'test route collects an original red gem');
 assert.equal(e['water-respawn'].respawns[1],1);assert.equal(e['water-respawn'].respawns[0],0);assert.equal(e['water-respawn'].red,result.before.red,'collected gems survive respawn');assert.ok(e['water-respawn'].fire[0]>15,'partner stays in place, not reset to spawn');assert.equal(e['water-respawn'].phase,'play');
 assert.ok(Math.abs(e['water-respawn'].water[0]-e['water-respawn'].checkpoints[1][0])<.05,'water returns to recorded ground');assert.ok(e['water-respawn'].water[0]>5,'uses recent checkpoint, not original spawn');
 assert.equal(e['held-input-stopped'].respawns[1],1);assert.ok(Math.abs(e['held-input-stopped'].water[0]-e['water-respawn'].water[0])<.01,'held direction is suppressed until released');assert.ok(e['water-can-move-again'].water[0]<e['water-respawn'].water[0]-.2,'release restores independent control');assert.equal(e['fire-respawn'].respawns[0],1);assert.equal(e['fire-respawn'].red,result.before.red);
 const replay=await pages[1].evaluate(commands=>{let s;for(const row of commands)s=call('lanStep',...row);return s;},result.commands);assert.deepEqual(replay,result.final,'independent delayed replay preserves checkpoints, deaths, world and digest');assert.deepEqual(errors,[]);
 fs.writeFileSync(path.resolve(__dirname,'../build/checkpoints-test.json'),JSON.stringify({date:new Date().toISOString(),frames:result.commands.length,events:result.events,replayIdentical:true,errors},null,2));console.log('PASS: independent safe ground, natural deaths for both roles, no held-key death loop, partner/gem progress and delayed replay');await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
