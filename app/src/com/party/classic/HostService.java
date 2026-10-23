package com.party.classic;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;

public class HostService extends Service {
    static volatile RoomServer server;static volatile String failure;
    private PowerManager.WakeLock lock;
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("room","双人闯关房间",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(this,"room").setContentTitle("森林冰火人 · 经典联机").setContentText("本地房间服务运行中 · 点此回到关卡").setSmallIcon(com.party.classic.R.drawable.ic_notification).setContentIntent(open).setOngoing(true).build();
        startForeground(7,notice);
        try{server=new RoomServer(name->{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}});
            lock=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"classicduo:room");lock.acquire();
        }catch(Exception e){failure=e.getMessage();stopSelf();}
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_NOT_STICKY;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){if(server!=null){server.close();server=null;}if(lock!=null&&lock.isHeld())lock.release();super.onDestroy();}
}
