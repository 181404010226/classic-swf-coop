      internal var lanMenuPending:Array;
      internal var lanCanControl:Boolean = false;
      public function lanMenu(view:String, canControl:Boolean) : Object {
         if(view!="intro" && view!="levels" && view!="instructions") return {ok:false,error:"invalid menu"};
         this.lanMenuPending=[view,canControl];
         return {ok:true,phase:"menu-loading"};
      }
      private function lanRequestMenu(action:String, node:int=0) : void {
         if(!this.lanCanControl) return;
         ExternalInterface.call("classicMenu",action,node);
      }
      private function lanShowMenu(view:String, canControl:Boolean) : void {
         this.lanCanControl=canControl;
         this.lanPending=null;
         if(this.m_level!=null) {
            this.m_level.removeEventListener(Event.ENTER_FRAME,this.m_level.Update);
            stage.removeEventListener(KeyboardEvent.KEY_DOWN,this.keyPressed);
            stage.removeEventListener(KeyboardEvent.KEY_UP,this.keyReleased);
         }
         if(this.m_mainMenu!=null) this.m_mainMenu.flies.removeEventListener(Event.ENTER_FRAME,this.FliesMove);
         this.m_level=null;this.lanNode=0;this.lanFrame=0;
         SoundMixer.stopAll();this.PlayingMusic=false;
         while(this.numChildren>0) this.removeChildAt(this.numChildren-1);
         this.mouseEnabled=canControl;this.mouseChildren=canControl;
         if(view=="levels") this.CreateMainMenu();
         else {
            this.CreateIntroMenu();
            this.m_introMenu.Buttons.Morer.visible=false;
            this.m_introMenu.Buttons.Submiter.visible=false;
            this.m_introMenu.Buttons.Crediter.visible=false;
            this.m_introMenu.ArmorLogo.mouseEnabled=false;
            this.m_introMenu.ArmorLogo.mouseChildren=false;
            if(view=="instructions") {
               this.instr=new Instructions();addChild(this.instr);
               this.instr.OKO.addEventListener(MouseEvent.CLICK,this.UnLoadInstructions);
            }
         }
         this.lanStatus=view;
      }
