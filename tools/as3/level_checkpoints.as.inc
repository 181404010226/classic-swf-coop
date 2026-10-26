      public var lanHistory:Array;
      public var lanSafeFrames:Array;
      public var lanCheckpointReady:Array;
      public var lanRespawns:Array;
      public var lanWaitRelease:Array;
      public var lanFlash:Array;
      public function lanInitCheckpoints() : void {
         this.lanHistory=[[[this.StartPosFire.x,this.StartPosFire.y,0]],[[this.StartPosWater.x,this.StartPosWater.y,0]]];
         this.lanSafeFrames=[0,0];this.lanCheckpointReady=[false,false];this.lanRespawns=[0,0];this.lanWaitRelease=[false,false];this.lanFlash=[0,0];
      }
      private function lanDanger(kind:*, role:int) : Boolean {
         return kind=="black" || (role==0 && kind=="water") || (role==1 && kind=="fire");
      }
      public function lanSafeAt(p:b2Vec2, role:int) : Boolean {
         if(p.x<0.8 || p.y<0 || p.x>this.LevelArray[0].length-1.8 || p.y>this.LevelArray.length-1.5) return false;
         var area:b2AABB=new b2AABB();area.lowerBound.Set(p.x-0.95,p.y-1);area.upperBound.Set(p.x+0.95,p.y+1.35);
         var found:Array=[];var count:int=this.m_world.Query(area,found,2048);
         var supported:Array=[false,false,false];
         for(var i:int=0;i<count;i++) {
            var shape:b2Shape=found[i];var body:b2Body=shape.GetBody();
            if(body==this.pers1 || body==this.pers2) continue;
            if(this.lanDanger(shape.GetUserData(),role)) return false;
            if(shape.IsSensor()) continue;
            for(var x:int=-1;x<=1;x++) {
               if(body.IsStatic() && body.GetUserData()!=null && body.GetUserData().name=="ground") {
                  if(shape.TestPoint(body.GetXForm(),new b2Vec2(p.x+x*0.43,p.y+0.87)) || shape.TestPoint(body.GetXForm(),new b2Vec2(p.x+x*0.43,p.y+1.06))) supported[x+1]=true;
               }
               for(var y:int=0;y<3;y++) if(shape.TestPoint(body.GetXForm(),new b2Vec2(p.x+x*0.34,p.y-0.72+y*0.56))) return false;
            }
         }
         return supported[0] && supported[1] && supported[2];
      }
      public function lanFilterInput(role:int, mask:int) : int {
         if(mask==0) this.lanWaitRelease[role]=false;
         return this.lanWaitRelease[role]?0:mask;
      }
      private function lanRespawn(role:int) : void {
         var old:b2Body=role==0?this.pers1:this.pers2;
         var history:Array=this.lanHistory[role];var point:Array=history[0];
         for(var i:int=history.length-1;i>=0;i--) if(this.lanSafeAt(new b2Vec2(history[i][0],history[i][1]),role)) {point=history[i];break;}
         var visual:* =old.GetUserData();this.m_world.DestroyBody(old);
         var def:b2BodyDef=new b2BodyDef();def.fixedRotation=true;def.userData=visual;def.position.Set(point[0],point[1]-0.06);
         var b:b2Body=this.m_world.CreateBody(def);
         var circle:b2CircleDef=new b2CircleDef();circle.radius=0.5;circle.density=0.6;circle.friction=0;circle.restitution=0.1;circle.localPosition.Set(0,-0.3);circle.filter.groupIndex=-4;b.CreateShape(circle);
         circle=new b2CircleDef();circle.radius=0.3;circle.density=0.01;circle.friction=0.2;circle.restitution=0.1;circle.localPosition.Set(0,0.5);circle.filter.groupIndex=-4;circle.userData=role==0?"firelegs":"waterlegs";b.CreateShape(circle);
         var polygon:b2PolygonDef=new b2PolygonDef();polygon.vertexCount=4;polygon.vertices[0].Set(-0.5,-0.3);polygon.vertices[1].Set(0.5,-0.3);polygon.vertices[2].Set(0.3,0.5);polygon.vertices[3].Set(-0.3,0.5);polygon.friction=0;polygon.density=0.5;polygon.filter.groupIndex=-4;b.CreateShape(polygon);b.SetMassFromShapes();
         if(role==0) {
            this.pers1=b;this.myContactListener.deadFire=false;this.myContactListener.contactStack1=[];this.myContactListener.finish1=0;
            this.l_pressed=false;this.r_pressed=false;this.u_pressed=false;this.prohibjump1=false;this.allowjump1=false;
         } else {
            this.pers2=b;this.myContactListener.deadWater=false;this.myContactListener.contactStack2=[];this.myContactListener.finish2=0;
            this.l_pressed2=false;this.r_pressed2=false;this.u_pressed2=false;this.prohibjump2=false;this.allowjump2=false;
         }
         b.WakeUp();visual.visible=true;visual.x=def.position.x*20;visual.y=def.position.y*20;
         this.lanRespawns[role]++;this.lanWaitRelease[role]=true;this.lanSafeFrames[role]=0;this.lanFlash[role]=10;
      }
      public function lanCheckpointAfterPhysics() : void {
         if(!Game.lan || this.ended) return;
         for(var role:int=0;role<2;role++) {
            if(role==0?this.myContactListener.deadFire:this.myContactListener.deadWater) this.lanRespawn(role);
            var body:b2Body=role==0?this.pers1:this.pers2;var p:b2Vec2=body.GetPosition();
            if(this.lanFlash[role]>0) this.lanFlash[role]--;
            body.GetUserData().alpha=this.lanFlash[role]>0 && this.lanFlash[role]%2==0?0.45:1;
            if(this.lanWaitRelease[role] || Math.abs(body.GetLinearVelocity().y)>0.2 || !this.lanSafeAt(p,role)) {this.lanSafeFrames[role]=0;continue;}
            this.lanSafeFrames[role]=Math.min(6,this.lanSafeFrames[role]+1);
            if(this.lanSafeFrames[role]>=6) {
               var history:Array=this.lanHistory[role];var last:Array=history[history.length-1];
               if(!this.lanCheckpointReady[role] || Math.abs(last[0]-p.x)+Math.abs(last[1]-p.y)>0.35) {
                  history.push([p.x,p.y,this.timecount]);if(history.length>24) history.splice(1,1);
                  this.lanCheckpointReady[role]=true;
               }
            }
         }
      }
      public function lanCheckpointState() : Array {
         var result:Array=[];
         for(var role:int=0;role<2;role++) {var h:Array=this.lanHistory[role];var p:Array=h[h.length-1];result.push([p[0],p[1],p[2],this.lanCheckpointReady[role]]);}
         return result;
      }
