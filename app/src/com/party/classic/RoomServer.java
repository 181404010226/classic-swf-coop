package com.party.classic;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPOutputStream;
import org.json.*;

/** HTTP assets + RFC6455 WebSocket input/snapshots, with a single fixed-step room owner. */
public final class RoomServer implements Closeable {
    public interface Assets {byte[] read(String name)throws IOException;}
    public final RoomWorld world;private final Assets assets;private final ServerSocket listener;
    final ThreadPoolExecutor workers=new ThreadPoolExecutor(4,8,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(48));
    final ScheduledExecutorService clock=Executors.newSingleThreadScheduledExecutor();
    final ConcurrentHashMap<String,Peer> peers=new ConcurrentHashMap<>();final Map<String,byte[]> cache=new ConcurrentHashMap<>();
    volatile boolean running=true;int frame;long broadcasts;
    public RoomServer(Assets a)throws IOException{assets=a;world=new RoomWorld();ServerSocket s;try{s=new ServerSocket(17684,32,InetAddress.getByName("0.0.0.0"));}catch(IOException e){s=new ServerSocket(0,32,InetAddress.getByName("0.0.0.0"));}listener=s;
        Thread accept=new Thread(()->{while(running)try{Socket client=listener.accept();try{workers.execute(()->serve(client));}catch(RejectedExecutionException e){client.close();}}catch(IOException e){if(!running)break;}},"forest-http");accept.setDaemon(true);accept.start();
        clock.scheduleAtFixedRate(()->{try{world.tick(System.currentTimeMillis());broadcast();}catch(Exception e){System.err.println("World tick: "+e.getMessage());}},0,40000000,TimeUnit.NANOSECONDS);
    }
    public int port(){return listener.getLocalPort();}
    public List<String> urls(){List<String> urls=new ArrayList<>();try{Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){NetworkInterface ni=all.nextElement();String n=ni.getName();if(!ni.isUp()||ni.isLoopback()||n.startsWith("tun")||n.startsWith("rmnet")||n.startsWith("ccmni"))continue;Enumeration<InetAddress> ips=ni.getInetAddresses();while(ips.hasMoreElements()){InetAddress ip=ips.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())urls.add("http://"+ip.getHostAddress()+":"+port());}}}catch(Exception ignored){}urls.sort((a,b)->Integer.compare(a.contains("192.168.")?0:1,b.contains("192.168.")?0:1));return urls;}
    static String line(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();int c;while((c=in.read())!=-1){if(c=='\n')break;if(out.size()>8192)throw new IOException("Header too long");if(c!='\r')out.write(c);}return c<0&&out.size()==0?null:out.toString("UTF-8");}
    void serve(Socket socket){boolean websocket=false;try{socket.setSoTimeout(5000);socket.setTcpNoDelay(true);InputStream in=new BufferedInputStream(socket.getInputStream());String first=line(in);if(first==null)return;String[] p=first.split(" ");if(p.length!=3)return;URI uri=new URI(p[1]);String path=uri.getPath(),method=p[0];Map<String,String> h=new HashMap<>();String s;int total=0;while((s=line(in))!=null&&!s.isEmpty()){total+=s.length();if(total>16384)throw new IOException("Too many headers");int c=s.indexOf(':');if(c>0)h.put(s.substring(0,c).toLowerCase(Locale.ROOT),s.substring(c+1).trim());}
        if(h.containsKey("origin")&&!h.get("origin").equals("http://"+h.get("host"))){json(socket,403,new JSONObject().put("error","请从房间页面操作"));return;}
        if(path.equals("/ws")&&method.equals("GET")){
            Map<String,String> q=query(uri.getRawQuery());String t=q.getOrDefault("t","");world.state(t);RoomWorld.require("websocket".equalsIgnoreCase(h.get("upgrade"))&&"13".equals(h.get("sec-websocket-version")),"无效连接");
            String key=h.getOrDefault("sec-websocket-key","");RoomWorld.require(key.length()==24,"无效连接");String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            socket.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            Peer peer=new Peer(t,socket,in);Peer old=peers.put(t,peer);if(old!=null)old.close();world.connect(t,peer.id);peer.start();websocket=true;return;
        }
        String t=h.getOrDefault("x-seat-token","");
        if(path.startsWith("/api/")){try{JSONObject result;if(path.equals("/api/action")&&method.equals("POST")){
            RoomWorld.require(h.getOrDefault("content-type","").startsWith("application/json"),"请求格式无效");int length=Integer.parseInt(h.getOrDefault("content-length","0"));RoomWorld.require(length>=2&&length<=8192,"请求大小无效");byte[] body=readExactly(in,length);JSONObject a=new JSONObject(new String(body,StandardCharsets.UTF_8));a.put("token",t);result=world.action(a);
        }else if(path.equals("/api/state")&&method.equals("GET"))result=world.state(t);
        else if(path.equals("/api/replay")&&method.equals("GET")){Map<String,String> q=query(uri.getRawQuery());result=world.replay(t,Integer.parseInt(q.getOrDefault("after","0")),Integer.parseInt(q.getOrDefault("epoch","-1")));}
        else if(path.equals("/api/config")&&method.equals("GET")){RoomWorld.require(world.host(t),"仅供房主查看");result=new JSONObject().put("urls",new JSONArray(urls())).put("port",port());}
        else{json(socket,404,new JSONObject().put("error","接口不存在"));return;}json(socket,200,result);}catch(IllegalArgumentException|JSONException e){json(socket,400,new JSONObject().put("error",e.getMessage()));}return;}
        if(!method.equals("GET")){respond(socket,405,"text/plain",new byte[0],false);return;}
        String file=path.equals("/")?"index.html":path.substring(1);if(!file.matches("[A-Za-z0-9_./-]+")||file.contains("..")||!(Arrays.asList("index.html","app.js","style.css","qr.js","engine.html","engine.js","game.swf","cover.jpg").contains(file)||file.startsWith("ruffle/"))){respond(socket,404,"text/plain",new byte[0],false);return;}
        String ext=file.substring(file.lastIndexOf('.')+1);String mime=ext.equals("js")?"application/javascript":ext.equals("css")?"text/css":ext.equals("png")?"image/png":ext.equals("jpg")?"image/jpeg":ext.equals("svg")?"image/svg+xml":ext.equals("wasm")?"application/wasm":ext.equals("swf")?"application/x-shockwave-flash":"text/html";
        boolean gzip=h.getOrDefault("accept-encoding","").contains("gzip")&&(ext.equals("js")||ext.equals("css"));String cacheKey=file+(gzip?":gz":"");boolean immutable=file.startsWith("ruffle/")||file.equals("game.swf")||file.equals("cover.jpg");byte[] bytes=immutable?cache.get(cacheKey):null;if(bytes==null){try{bytes=assets.read(file);if(gzip){ByteArrayOutputStream out=new ByteArrayOutputStream();try(GZIPOutputStream z=new GZIPOutputStream(out)){z.write(bytes);}bytes=out.toByteArray();}if(immutable)cache.put(cacheKey,bytes);}catch(IOException e){respond(socket,404,"text/plain",new byte[0],false);return;}}respond(socket,200,mime,bytes,gzip);
    }catch(Exception e){/* Bad/disconnected clients do not stop the room. */}finally{if(!websocket)try{socket.close();}catch(Exception ignored){}}}
    static Map<String,String> query(String q)throws Exception{Map<String,String> result=new HashMap<>();if(q!=null)for(String pair:q.split("&")){String[] p=pair.split("=",2);result.put(URLDecoder.decode(p[0],"UTF-8"),p.length>1?URLDecoder.decode(p[1],"UTF-8"):"");}return result;}
    static byte[] readExactly(InputStream in,int n)throws IOException{byte[] b=new byte[n];int pos=0;while(pos<n){int c=in.read(b,pos,n-pos);if(c<0)throw new EOFException();pos+=c;}return b;}
    void json(Socket s,int status,JSONObject obj)throws IOException{respond(s,status,"application/json; charset=utf-8",obj.toString().getBytes(StandardCharsets.UTF_8),false);}
    void respond(Socket s,int status,String type,byte[] bytes,boolean gzip)throws IOException{
        String head="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; media-src 'self' blob: data:; connect-src 'self' ws:; frame-src 'self'; worker-src 'self' blob:; frame-ancestors 'self'\r\n"+(gzip?"Content-Encoding: gzip\r\n":"")+"\r\n";OutputStream out=s.getOutputStream();out.write(head.getBytes(StandardCharsets.US_ASCII));out.write(bytes);out.flush();
    }
    void broadcast(){broadcasts++;for(Peer p:peers.values())try{p.offer(world.state(p.token).toString());}catch(Exception e){p.close();}}
    final class Peer {
        final String token,id=UUID.randomUUID().toString();final Socket socket;final InputStream in;final Object signal=new Object();volatile boolean alive=true;String latest;final ArrayDeque<String> reliable=new ArrayDeque<>();
        Peer(String t,Socket s,InputStream i){token=t;socket=s;in=i;}
        void start(){Thread reader=new Thread(this::read,"forest-ws-read"),writer=new Thread(this::write,"forest-ws-write");reader.setDaemon(true);writer.setDaemon(true);reader.start();writer.start();}
        void offer(String text){synchronized(signal){latest=text;signal.notifyAll();}}
        void write(){try{OutputStream out=socket.getOutputStream();while(alive){String text;synchronized(signal){while(alive&&latest==null&&reliable.isEmpty())signal.wait(1000);if(!alive)break;if(!reliable.isEmpty())text=reliable.removeFirst();else{text=latest;latest=null;}}if(text==null)continue;byte[] b=text.getBytes(StandardCharsets.UTF_8);out.write(0x81);if(b.length<126)out.write(b.length);else{out.write(126);out.write(b.length>>8);out.write(b.length&255);}out.write(b);out.flush();}}catch(Exception e){}finally{close();}}
        void read(){try{socket.setSoTimeout(10000);while(alive){int first=in.read(),second=in.read();if(first<0||second<0)break;int opcode=first&15;if((first&0x80)==0||(first&0x70)!=0||(second&0x80)==0)break;long length=second&127;if(length==126){byte[] n=readExactly(in,2);length=((n[0]&255)<<8)|(n[1]&255);}else if(length==127)break;if(length>24000)break;byte[] mask=readExactly(in,4),body=readExactly(in,(int)length);for(int n=0;n<body.length;n++)body[n]^=mask[n%4];if(opcode==8)break;if(opcode==1){try{JSONObject message=new JSONObject(new String(body,StandardCharsets.UTF_8));world.input(token,id,message);}catch(IllegalArgumentException|JSONException ignored){}}else if(opcode!=9&&opcode!=10)break;}}catch(Exception e){}finally{close();}}
        void close(){if(!alive)return;alive=false;peers.remove(token,this);world.disconnect(token,id);synchronized(signal){signal.notifyAll();}try{socket.close();}catch(IOException ignored){}}
    }
    public void close(){running=false;try{listener.close();}catch(Exception ignored){}for(Peer p:peers.values())p.close();clock.shutdownNow();workers.shutdownNow();}
}
