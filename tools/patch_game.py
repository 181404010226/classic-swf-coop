"""Game-specific adapter. Never edits the archived original. Build with FFDec -replace."""
import pathlib,re,json,hashlib
root=pathlib.Path(__file__).resolve().parents[1];src=root/'build'/'game'/'export'/'scripts';dst=root/'build'/'game'/'patched';dst.mkdir(exist_ok=True)
def replace_method(s,name,body):
    m=re.search(r'\bfunction\s+'+name+r'\s*\([^\n]*',s);a=s.index('{',m.end());d=1;b=a+1
    while d:
        if s[b]=='{':d+=1
        elif s[b]=='}':d-=1
        b+=1
    return s[:a+1]+'\n'+body+'\n      '+s[b-1:]

s=(src/'Game.as').read_text('utf8')
s=s.replace('import flash.utils.getTimer;','import flash.utils.getTimer;\n   import flash.external.ExternalInterface;')
s=s.replace('public class Game extends MovieClip\n   {','public class Game extends MovieClip\n   {\n      public static var lan:Boolean = false;\n      public static var lanStepping:Boolean = false;\n      public static var lanSeed:uint = 1;\n      internal var lanBooted:Boolean = false;\n      internal var lanPreparedAt:int = -1;\n      internal var lanFrame:int = 0;\n      internal var lanNode:int = 0;\n      internal var lanStatus:String = "boot";')
s=s.replace('this.CreatePreloader();','''try { Game.lan = String(this.loaderInfo.parameters["lan"]) == "1"; } catch (e:Error) {}
         if (Game.lan && ExternalInterface.available) {
            ExternalInterface.addCallback("lanInit", this.lanInit);
            ExternalInterface.addCallback("lanStart", this.lanStart);
            ExternalInterface.addCallback("lanStep", this.lanStep);
            ExternalInterface.addCallback("lanState", this.lanState);
            ExternalInterface.addCallback("lanBatch", this.lanBatch);
            this.addEventListener(Event.ENTER_FRAME,this.lanOnFrame);
         } else { this.CreatePreloader(); }''',1)
s=s.replace('this.CreateIntroMenu();','if (!Game.lan) this.CreateIntroMenu();')
s=s.replace('this.MusicChannel.stop();','if (this.MusicChannel != null) this.MusicChannel.stop();')
for method in ['GameOver','Recuent']:
    s=s.replace('function '+method+'() : *\n      {','function '+method+'() : *\n      {\n         if (Game.lan) return;')
# Use a deterministic clock only in LAN mode; ordinary loading retains original behavior.
s=s.replace('this.m_timer.currentTime = getTimer() - this.m_timer.startTime;','this.m_timer.currentTime = Game.lan ? this.lanFrame * 40 : getTimer() - this.m_timer.startTime;')
s=s.replace('this.m_timer.addEventListener(Event.ENTER_FRAME,this.TimerUpdate,false,0,true);','if (!Game.lan) this.m_timer.addEventListener(Event.ENTER_FRAME,this.TimerUpdate,false,0,true);')
s=s.replace('this.m_pauseMenu.name = "m_pauseMenu";','this.m_pauseMenu.name = "m_pauseMenu";\n         if (Game.lan) this.m_pauseMenu.removeEventListener(Event.ENTER_FRAME,this.m_pauseMenu.Update);')
s=s.replace('private function keyReleased(param1:KeyboardEvent) : void\n      {','private function keyReleased(param1:KeyboardEvent) : void\n      {\n         if (Game.lan) return;')
s=s.replace('private function keyPressed(param1:KeyboardEvent) : void\n      {','private function keyPressed(param1:KeyboardEvent) : void\n      {\n         if (Game.lan) return;')
bridge=r'''
      internal var lanPending:Array;
      public function lanOnFrame(event:Event) : void {
         if (!this.lanBooted) {
            if(this.framesLoaded<3) return;
            if(this.currentFrame!=3) {this.gotoAndStop(3);return;}
            if (this.lanPreparedAt < 0) {this.lanPreparedAt=getTimer();return;}
            if(getTimer()-this.lanPreparedAt<120) return;
            this.GameReady();this.lanBooted=true;this.lanStatus="menu";
         }
         if (this.lanPending != null) {
            var probe:Platform1=new Platform1();
            if(probe.bar==null) return;
            var p:Array=this.lanPending;this.lanPending=null;
            try { this.lanStartNow(int(p[0]),uint(p[1])); }
            catch(e:Error) { this.lanStatus="error:"+String(e)+":"+e.getStackTrace(); throw e; }
         }
      }
      public static function lanRandom() : Number {
         if (!Game.lan) return Math.random();
         Game.lanSeed = uint((Number(Game.lanSeed) * 1664525 + 1013904223) % 4294967296);
         return Number(Game.lanSeed) / 4294967296;
      }
      public function lanInit() : Object {
         if (!Game.lan) return {ok:false, error:"LAN parameter required"};
         if (!this.lanBooted) return {ok:false,phase:"symbols"};
         return {ok:true,abi:"rgm141-lan/1",nodes:this.Translation,stepMs:40,physicsDt:0.038461538461538464};
      }
      public function lanStart(node:int, seed:uint) : Object {
         this.lanPending=[node,seed];this.lanStatus="starting";
         return {ok:true,phase:"starting"};
      }
      public function lanStartNow(node:int, seed:uint) : Object {
         this.lanInit();
         if(node<1 || node>32) return {ok:false,error:"invalid node"};
         if (this.m_level != null) {
            this.m_level.removeEventListener(Event.ENTER_FRAME,this.m_level.Update);
            this.m_timer.removeEventListener(Event.ENTER_FRAME,this.TimerUpdate);
            this.m_pauseMenu.removeEventListener(Event.ENTER_FRAME,this.m_pauseMenu.Update);
            stage.removeEventListener(KeyboardEvent.KEY_DOWN,this.keyPressed);
            stage.removeEventListener(KeyboardEvent.KEY_UP,this.keyReleased);
         }
         SoundMixer.stopAll();
         while(this.numChildren>0) this.removeChildAt(this.numChildren-1);
         Game.lanSeed=seed==0?1:seed;
         this.lanNode=node;this.lanFrame=0;this.lanStatus="play";
         var parts:Array=String(this.Translation[node-1]).split("_");
         this.cur_node=node;this.cur_type=Number(parts[0]);this.cur_lev=Number(parts[1]);
         this.PlayingMusic=false;
         this.StartGame(this.cur_type,this.cur_lev);
         this.m_timer.currentTime=0;
         this.m_level.removeEventListener(Event.ENTER_FRAME,this.m_level.Update);
         this.m_pauseMenu.removeEventListener(Event.ENTER_FRAME,this.m_pauseMenu.Update);
         this.mouseEnabled=false;this.mouseChildren=false;
         return this.lanState(true);
      }
      public function lanStep(frame:int, fire:int, water:int) : Object {
         if(!this.lanBooted || this.m_level==null) return {ok:false,error:"not started"};
         if(frame!=this.lanFrame+1) return {ok:false,error:"frame gap",frame:this.lanFrame};
         this.lanFrame=frame;
         this.m_level.l_pressed=(fire&1)!=0;this.m_level.r_pressed=(fire&2)!=0;this.m_level.u_pressed=(fire&4)!=0;
         this.m_level.l_pressed2=(water&1)!=0;this.m_level.r_pressed2=(water&2)!=0;this.m_level.u_pressed2=(water&4)!=0;
         Game.lanStepping=true;
         try { this.m_level.Update(new Event(Event.ENTER_FRAME)); this.TimerUpdate(new Event(Event.ENTER_FRAME)); }
         finally { Game.lanStepping=false; }
         if(this.m_level.ended) this.lanStatus=(this.m_level.myContactListener.deadFire || this.m_level.myContactListener.deadWater)?"dead":"complete";
         return this.lanState(false);
      }
      public function lanBatch(commands:Array) : Object {
         var result:Object=this.lanState(false);
         for(var i:int=0;i<commands.length;i++) { result=this.lanStep(int(commands[i][0]),int(commands[i][1]),int(commands[i][2])); if(!result.ok) return result; }
         return result;
      }
      private function lanMix(h:uint, value:Number) : uint {
         return uint((Number(h ^ uint(Math.round(value * 1000000))) * 1664525 + 1013904223) % 4294967296);
      }
      public function lanState(detail:Boolean=false) : Object {
         if(this.m_level==null) return {ok:true,frame:this.lanFrame,phase:this.lanStatus,node:this.lanNode};
         var l:level=this.m_level;
         var hash:uint=2166136261;
         var bodies:Array=[];
         var b:b2Body=l.m_world.m_bodyList;
         var id:int=0;
         while(b!=null) {
            var p:b2Vec2=b.GetPosition();var v:b2Vec2=b.GetLinearVelocity();
            hash=this.lanMix(hash,p.x);hash=this.lanMix(hash,p.y);hash=this.lanMix(hash,b.GetAngle());
            hash=this.lanMix(hash,v.x);hash=this.lanMix(hash,v.y);hash=this.lanMix(hash,b.GetAngularVelocity());
            hash=this.lanMix(hash,b.m_shapeCount);
            if(detail) bodies.push([id,p.x,p.y,b.GetAngle(),v.x,v.y,b.GetAngularVelocity(),b.m_shapeCount]);
            id++;b=b.m_next;
         }
         hash=this.lanMix(hash,l.timecount);hash=this.lanMix(hash,l.myContactListener.RedCount);hash=this.lanMix(hash,l.myContactListener.BlueCount);hash=this.lanMix(hash,l.myContactListener.GreenCount);
         hash=this.lanMix(hash,l.myContactListener.finish1);hash=this.lanMix(hash,l.myContactListener.finish2);
         hash=this.lanMix(hash,l.myContactListener.deadFire?1:0);hash=this.lanMix(hash,l.myContactListener.deadWater?1:0);
         hash=this.lanMix(hash,(l.SlidersAndRots.getChildByName("finish1") as MovieClip).currentFrame);
         hash=this.lanMix(hash,(l.SlidersAndRots.getChildByName("finish2") as MovieClip).currentFrame);
         var firePos:b2Vec2=l.pers1.GetPosition();var waterPos:b2Vec2=l.pers2.GetPosition();
         return {ok:true,frame:this.lanFrame,node:this.lanNode,phase:this.lanStatus,hash:String(hash),bodyCount:id,red:l.myContactListener.RedCount,blue:l.myContactListener.BlueCount,green:l.myContactListener.GreenCount,fire:[firePos.x,firePos.y],water:[waterPos.x,waterPos.y],timeMs:this.m_timer.currentTime,rng:String(Game.lanSeed),bodies:detail?bodies:null};
      }
'''
pos=s.rfind('\n   }');s=s[:pos]+bridge+s[pos:];(dst/'Game.as').write_text(s,'utf8')
s=(src/'level.as').read_text('utf8').replace('Math.random()','Game.lanRandom()')
s=s.replace('_loc11_.userData = new Platform1();','_loc11_.userData = new Platform1();\n         if (Game.lan) _loc11_.userData.gotoAndStop(1);')
s=s.replace('addEventListener(Event.ENTER_FRAME,this.Update,false,0,true);','if (!Game.lan) addEventListener(Event.ENTER_FRAME,this.Update,false,0,true);')
s=s.replace('public function Update(param1:Event) : void\n      {','public function Update(param1:Event) : void\n      {\n         if (Game.lan && !Game.lanStepping) return;')
(dst/'level.as').write_text(s,'utf8')
for cls in ['deadFire','WaterGirlStairs']:
    s=(src/(cls+'.as')).read_text('utf8')
    s=s.replace('(this.parent.parent as Game).','if (!Game.lan && this.parent != null && this.parent.parent is Game) (this.parent.parent as Game).')
    (dst/(cls+'.as')).write_text(s,'utf8')
original=(root/'local'/'game.swf').read_bytes()
(dst/'profile.json').write_text(json.dumps({'id':'rgm141','abi':'rgm141-lan/1','originalSha256':hashlib.sha256(original).hexdigest(),'patchedClasses':['Game','level','deadFire','WaterGirlStairs'],'frameMs':40,'physicsStep':1/26,'rng':'LCG32 1664525,1013904223','requires':['ExternalInterface','same patched SWF and Ruffle runtime'],'totalNodes':32},indent=2),'utf8')
print(dst)
