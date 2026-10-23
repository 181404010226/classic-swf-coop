'use strict';
Object.defineProperty(window,'devicePixelRatio',{get:()=>1,configurable:true});
let player;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const notify=(type,data={})=>parent.postMessage({classicEngine:true,type,...data},location.origin);
const call=(name,...args)=>player.ruffle().callExternalInterface(name,...args);
window.Engine={
 ready:false,
 async start(node,seed){
  call('lanStart',node,seed);await sleep(160);
  for(let i=0;i<100;i++){const s=call('lanState');if(s?.phase?.startsWith('error:'))throw Error(s.phase);if(s?.node===node&&s.phase==='play'&&s.frame===0)return s;await sleep(50);}
  throw Error('关卡创建超时');
 },
 step(row){return call('lanStep',...row);},state(){return call('lanState');},
 volume(value){if(player)player.ruffle().volume=value;}
};
window.addEventListener('DOMContentLoaded',async()=>{try{
 player=RufflePlayer.newest().createPlayer();document.body.appendChild(player);
 player.ruffle().config={autoplay:'on',unmuteOverlay:'hidden',quality:'medium',letterbox:'on',allowScriptAccess:true,allowNetworking:'all',openUrlMode:'deny',contextMenu:'off',showSwfDownload:false,logLevel:'error'};
 await player.ruffle().load({url:'game.swf',parameters:{lan:'1'},allowScriptAccess:true});
 for(let i=0;i<300;i++){const info=call('lanInit');if(info?.ok){Engine.ready=true;notify('ready',{info});return;}await sleep(50);}
 throw Error('本地同步接口未就绪');
}catch(e){notify('error',{message:e.message});}});
