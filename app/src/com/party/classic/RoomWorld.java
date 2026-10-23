package com.party.classic;
import java.util.*;
import java.security.SecureRandom;
import org.json.*;

/** Orders 25 Hz input frames. Each client owns its local original Box2D simulation. */
public final class RoomWorld {
 static final SecureRandom RNG=new SecureRandom();public final String hostToken=token();
 int frame,node=1,seed=1,resync;String fault="";ArrayList<JSONArray> journal=new ArrayList<>();Map<Integer,String[]> hashes=new HashMap<>();
 static class Seat{int loaded=-1;String name,token,link="";int role,mask;boolean ready,connected,active=true;long seen,inputAt,seq=-1;Seat(String n,String t,int r){name=n;token=t;role=r;}}
 Seat[] seats=new Seat[2];String phase="home",room="",invite="",engineStatus="未启动";boolean solo,manualPause;int epoch;long revision;Map<String,String> joins=new HashMap<>();Map<String,LinkedHashSet<String>> requests=new HashMap<>();
 static String token(){byte[] b=new byte[16];RNG.nextBytes(b);StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
 public static void require(boolean b,String s){if(!b)throw new IllegalArgumentException(s);}
 public boolean host(String t){return hostToken.equals(t);}
 public synchronized int seat(String t){if(t!=null)for(int i=0;i<2;i++)if(seats[i]!=null&&t.equals(seats[i].token))return i;return -1;}
 String clean(String s,String fallback){s=s.replaceAll("[\\p{Cntrl}<>]","").trim();return s.isEmpty()?fallback:s.substring(0,Math.min(12,s.length()));}
 public synchronized JSONObject action(JSONObject a)throws JSONException{
  String t=a.optString("token"),op=a.optString("op"),id=a.optString("requestId");require(!id.isEmpty()&&id.length()<100,"请求无效");
  if(op.equals("join")){require(!phase.equals("home")&&room.equals(a.optString("room"))&&invite.equals(a.optString("invite")),"房间已结束，请重新扫码");int old=seat(t);if(old<0)old=seat(joins.get(id));if(old>=0)return state(seats[old].token).put("token",seats[old].token);require(phase.equals("lobby")&&!solo,"本局已经开始，或这是单机房间");require(seats[1]==null,"房间已满，原搭档可以用原链接回来");Seat s=new Seat(clean(a.optString("name"),"搭档"),token(),1-seats[0].role);seats[1]=s;joins.put(id,s.token);revision++;return state(s.token).put("token",s.token);}
  require(host(t)||seat(t)>=0,"座位已失效，请重新扫码");LinkedHashSet<String> used=requests.computeIfAbsent(t,k->new LinkedHashSet<>());if(used.contains(id))return state(t);int me=seat(t);
  if(op.equals("start")||op.equals("reload")){int requested=a.optInt("node",op.equals("start")?1:node);require(requested>=1&&requested<=32,"关卡无效");}
  switch(op){
   case "create":require(host(t),"请由房主创建");require(phase.equals("home")||phase.equals("lobby"),"请先结束当前房间");int role=a.optInt("role",0);require(role==0||role==1,"角色无效");seats=new Seat[2];seats[0]=new Seat(clean(a.optString("name"),"房主"),hostToken,role);seats[0].ready=true;solo=a.optBoolean("solo");room=String.format("%06d",RNG.nextInt(1000000));invite=token();phase="lobby";manualPause=false;engineStatus="未启动";epoch++;requests.clear();joins.clear();break;
   case "ready":require(me==1&&phase.equals("lobby"),"当前无法准备");seats[me].ready=!seats[me].ready;break;
   case "start":require(host(t)&&phase.equals("lobby"),"请由房主开局");require(seats[0].connected&&(solo||seats[1]!=null&&seats[1].connected&&seats[1].ready),"请等搭档连接并准备");phase="play";manualPause=false;engineStatus="加载本地游戏";epoch++;resetSimulation(a.optInt("node",1));clear();break;
   case "pause":require(me>=0&&phase.equals("play"),"当前不在游戏中");manualPause=!manualPause;clear();break;
   case "reload":require(host(t)&&phase.equals("play"),"请由房主重载");epoch++;resetSimulation(a.optInt("node",node));engineStatus="加载本地游戏";clear();break;
   case "switch":require(host(t)&&solo,"仅单机可切换角色");seats[0].role=1-seats[0].role;clear();break;
   case "leave":require(me==1&&phase.equals("lobby"),"开局后座位保留，可用原链接回来");seats[1]=null;revision++;return new JSONObject().put("left",true);
   case "close":require(host(t),"仅房主可结束");phase="home";room="";invite="";clear();seats=new Seat[2];break;
   default:throw new IllegalArgumentException("未知操作");
  }
  revision++;used.add(id);while(used.size()>64)used.remove(used.iterator().next());requests.put(t,used);return state(t);
 }
 void clear(){for(Seat s:seats)if(s!=null){s.mask=0;s.inputAt=0;}}
 public synchronized void connect(String t,String link){int me=seat(t);require(me>=0||host(t),"座位失效");if(me>=0){Seat s=seats[me];s.link=link;s.connected=true;s.active=true;s.seen=System.currentTimeMillis();s.seq=-1;s.mask=0;s.loaded=-1;}revision++;}
 public synchronized void disconnect(String t,String link){int me=seat(t);if(me>=0&&seats[me].link.equals(link)){seats[me].connected=false;clear();revision++;}}
 public synchronized void input(String t,String link,JSONObject a){int me=seat(t);if(me<0)return;Seat s=seats[me];if(!s.link.equals(link))return;s.seen=System.currentTimeMillis();s.connected=true;
  switch(a.optString("type")){
   case "input":long seq=a.optLong("seq",-1);if(seq<=s.seq)return;int mask=a.optInt("mask",0);require(mask>=0&&mask<8,"仅可控制自己的方向和跳跃");s.seq=seq;s.mask=s.active?mask:0;s.inputAt=s.seen;break;
   case "active":s.active=a.optBoolean("active");clear();revision++;break;
   case "engine":if(a.optInt("epoch",-1)==epoch&&a.optInt("resync",-1)==resync){s.loaded=epoch;engineStatus="双端本地运行";}break;
   case "digest":if(a.optInt("epoch",-1)==epoch&&a.optInt("resync",-1)==resync){int f=a.optInt("frame",-1);String digest=a.optString("hash");if(f>=0&&f<=frame&&f%10==0&&digest.matches("[0-9]{1,10}")){String[] pair=hashes.computeIfAbsent(f,k->new String[2]);pair[me]=digest;if(!solo&&pair[0]!=null&&pair[1]!=null){if(!pair[0].equals(pair[1])){resync++;hashes.clear();for(Seat x:seats)if(x!=null)x.loaded=-1;clear();if(resync>3)fault="同步校验持续不一致，请房主重开关卡";}else hashes.remove(f);}if(hashes.size()>128)hashes.clear();}}break;
   case "ping":break;
  }
 }
 public synchronized String recipient(String t,String link){int me=seat(t);require(me>=0&&seats[me].link.equals(link)&&phase.equals("play")&&!solo,"连接已失效");Seat other=seats[1-me];return other!=null&&other.connected?other.token:null;}
 void resetSimulation(int n){require(n>=1&&n<=32,"关卡无效");node=n;seed=RNG.nextInt(2147483646)+1;frame=0;resync=0;fault="";journal.clear();hashes.clear();for(Seat s:seats)if(s!=null)s.loaded=-1;}
 public synchronized JSONObject replay(String t,int after,int expected)throws JSONException{require(host(t)||seat(t)>=0,"座位失效");require(expected==epoch,"关卡已更新");require(after>=0&&after<=frame,"帧无效");JSONArray rows=new JSONArray();for(int i=after;i<Math.min(frame,after+250);i++)rows.put(journal.get(i));return new JSONObject().put("epoch",epoch).put("commands",rows).put("frame",frame);}
 public synchronized void tick(long now){for(Seat s:seats)if(s!=null){if(s.connected&&now-s.seen>5000){s.connected=false;clear();revision++;}if(now-s.inputAt>350)s.mask=0;}
  if(phase.equals("play")&&!paused()) {if(frame>=90000){fault="本局已到一小时，请重开关卡";clear();return;}int[] inputs={0,0};for(Seat s:seats)if(s!=null)inputs[s.role]=s.mask;journal.add(new JSONArray().put(++frame).put(inputs[0]).put(inputs[1]));}
 }
 boolean paused(){if(manualPause||!fault.isEmpty())return true;for(int i=0;i<(solo?1:2);i++)if(seats[i]==null||!seats[i].connected||!seats[i].active||seats[i].loaded!=epoch)return true;return false;}
 public synchronized JSONObject state(String t)throws JSONException{int me=seat(t);require(host(t)||me>=0,"座位已失效，请重新扫码");JSONArray people=new JSONArray();int[] inputs={0,0};boolean pause=phase.equals("play")&&paused();String reason=!fault.isEmpty()?fault:manualPause?"游戏已暂停":"等待搭档回到游戏";
  for(int i=0;i<2;i++){Seat s=seats[i];people.put(s==null?JSONObject.NULL:new JSONObject().put("name",s.name).put("role",s.role).put("ready",s.ready).put("connected",s.connected).put("active",s.active));if(s!=null&&!pause)inputs[s.role]=s.mask;if(i==0&&s!=null&&(!s.active||!s.connected))reason="等待房主回到游戏";}
  JSONArray commands=new JSONArray();for(int i=Math.max(0,frame-10);i<frame;i++)commands.put(journal.get(i));
  JSONObject out=new JSONObject().put("frame",frame).put("node",node).put("seed",seed).put("resync",resync).put("commands",commands).put("type","state").put("phase",phase).put("paused",pause).put("pauseReason",reason).put("manualPause",manualPause).put("room",room).put("host",host(t)).put("me",me).put("role",me>=0?seats[me].role:0).put("solo",solo).put("epoch",epoch).put("players",people).put("inputs",new JSONArray(inputs)).put("engineStatus",engineStatus).put("revision",revision).put("time",System.currentTimeMillis());if(host(t))out.put("invite",invite);return out;
 }
}
