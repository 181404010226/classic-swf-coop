'use strict';
Object.defineProperty(window,'devicePixelRatio',{get:()=>1,configurable:true});
let player,gameplayKeys=false;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const notify=(type,data={})=>parent.postMessage({classicEngine:true,type,...data},location.origin);
const call=(name,...args)=>player.ruffle().callExternalInterface(name,...args);
window.Engine={
 ready:false,
 async start(node,seed,menu='intro',canControl=false){
  if(node===0)return this.menu(menu,canControl);
  gameplayKeys=true;player.style.pointerEvents='none';
  call('lanStart',node,seed);await sleep(160);
  for(let i=0;i<100;i++){const s=call('lanState');if(s?.phase?.startsWith('error:'))throw Error(s.phase);if(s?.node===node&&s.phase==='play'&&s.frame===0)return s;await sleep(50);}
  throw Error('关卡创建超时');
 },
 async menu(view,canControl){
  gameplayKeys=false;
  player.style.pointerEvents=canControl?'auto':'none';call('lanMenu',view,canControl);await sleep(100);
  for(let i=0;i<100;i++){const s=call('lanState');if(s?.phase===view&&s.node===0)return s;if(s?.phase?.startsWith('error:'))throw Error(s.phase);await sleep(50);}
  throw Error('菜单加载超时');
 },
 step(row){return call('lanStep',...row);},state(){return call('lanState');},
 volume(value){if(player)player.ruffle().volume=value;}
};
window.classicMenu=(view,node)=>notify('menu',{view,node});
// Native menu clicks can leave keyboard focus inside the iframe. Keep the parent
// as the only held-key owner even when subsequent keys originate in Ruffle.
for(const kind of ['keydown','keyup'])window.addEventListener(kind,event=>{
 if(!gameplayKeys||!['ArrowLeft','ArrowRight','ArrowUp','KeyA','KeyD','KeyW','Space'].includes(event.code))return;
 event.preventDefault();notify('key',{kind,code:event.code,repeat:event.repeat});
});
window.addEventListener('DOMContentLoaded',async()=>{try{
 player=RufflePlayer.newest().createPlayer();document.body.appendChild(player);
 player.ruffle().config={autoplay:'on',unmuteOverlay:'hidden',quality:'medium',letterbox:'on',allowScriptAccess:true,allowNetworking:'all',openUrlMode:'deny',contextMenu:'off',showSwfDownload:false,logLevel:'error'};
 await player.ruffle().load({url:'game.swf',parameters:{lan:'1'},allowScriptAccess:true});
 for(let i=0;i<300;i++){const info=call('lanInit');if(info?.ok){Engine.ready=true;notify('ready',{info});return;}await sleep(50);}
 throw Error('本地同步接口未就绪');
}catch(e){notify('error',{message:e.message});}});
