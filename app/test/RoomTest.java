package com.party.classic;
import org.json.*;
public class RoomTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static JSONObject action(RoomWorld w,String token,String op)throws Exception{return w.action(new JSONObject().put("token",token).put("op",op).put("requestId",RoomWorld.token()));}
 static JSONObject packet(String type,RoomWorld w)throws Exception{return new JSONObject().put("type",type).put("epoch",w.epoch).put("resync",w.resync);}
 public static void main(String[]args)throws Exception{
  RoomWorld w=new RoomWorld();String h=w.hostToken;action(w,h,"create");
  String g=w.action(new JSONObject().put("op","join").put("name","Guest").put("room",w.room).put("invite",w.invite).put("requestId","guest")).getString("token");
  w.connect(h,"h");w.connect(g,"g");action(w,g,"ready");action(w,h,"start");long now=System.currentTimeMillis();w.tick(now);check(w.frame==0,"loading barrier");
  w.input(h,"h",packet("engine",w));w.input(g,"g",packet("engine",w));
  w.input(h,"h",packet("input",w).put("mask",2).put("seq",1));w.input(g,"g",packet("input",w).put("mask",4).put("seq",1));w.tick(now);check(w.frame==1,"fixed tick");
  JSONArray row=w.replay(h,0,w.epoch).getJSONArray("commands").getJSONArray(0);check(row.getInt(1)==2&&row.getInt(2)==4,"role inputs");
  w.input(h,"h",packet("input",w).put("mask",1).put("seq",0));check(w.seats[0].mask==2,"stale sequence rejected");
  for(int i=0;i<300;i++)w.tick(now+1);check(w.replay(g,0,w.epoch).getJSONArray("commands").length()==250,"bounded replay");
  w.tick(now+400);check(w.seats[0].mask==0&&w.seats[1].mask==0,"input expiry");
  w.disconnect(g,"g");int f=w.frame;w.tick(now+401);check(w.frame==f,"disconnect pause");w.connect(g,"g2");w.tick(now+402);check(w.frame==f,"reconnect loading barrier");w.input(g,"g2",packet("engine",w));
  w.input(h,"h",packet("digest",w).put("frame",10).put("hash","123"));w.input(g,"g2",packet("digest",w).put("frame",10).put("hash","124"));check(w.resync==1,"digest desync");
  w.input(h,"h",packet("engine",w).put("resync",0));check(w.seats[0].loaded==-1,"stale generation ignored");
  int oldEpoch=w.epoch;action(w,h,"reload");check(w.epoch==oldEpoch+1&&w.frame==0&&w.journal.isEmpty(),"new level clears journal");
  boolean rejected=false;try{w.replay(g,0,oldEpoch);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"stale replay rejected");
  System.out.println("PASS: input roles, sequence, leases, load barrier, replay, disconnect, desync generation, reload");
 }
}
