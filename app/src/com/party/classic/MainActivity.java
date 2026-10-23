package com.party.classic;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class MainActivity extends Activity {
    WebView web;final Handler handler=new Handler(Looper.getMainLooper());boolean destroyed;String joinedOrigin="";
    @Override public void onCreate(Bundle state){super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(13,35,31));getWindow().setNavigationBarColor(Color.rgb(13,35,31));
        WebView.setWebContentsDebuggingEnabled(true);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(13,35,31));
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(Color.rgb(13,35,31));frame.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
        frame.setOnApplyWindowInsetsListener((v,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });frame.requestApplyInsets();
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Phone(),"Phone");
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){String url=req.getUrl().toString();if(url.startsWith("http://127.0.0.1:")||!joinedOrigin.isEmpty()&&url.startsWith(joinedOrigin+"/"))return false;try{startActivity(new Intent(Intent.ACTION_VIEW,req.getUrl()));}catch(Exception ignored){}return true;}});
        try{startForegroundService(new Intent(this,HostService.class));waitForServer(0);}catch(Exception e){showError(e.getMessage());}
    }
    void waitForServer(int tries){if(destroyed)return;if(HostService.server!=null){RoomServer s=HostService.server;web.loadUrl("http://127.0.0.1:"+s.port()+"/#host="+s.world.hostToken);}else if(HostService.failure!=null||tries>60)showError("本地房间服务启动失败，请重新打开应用。"+(HostService.failure==null?"":HostService.failure));else handler.postDelayed(()->waitForServer(tries+1),100);}
    void showError(String text){TextView error=new TextView(this);error.setText(text);error.setTextColor(Color.WHITE);error.setPadding(32,60,32,32);setContentView(error);}
    public class Phone {
        @JavascriptInterface public void joinRoom(String text){runOnUiThread(()->{
            try{java.net.URI uri=new java.net.URI(text.trim());String host=uri.getHost();
                if(!"http".equals(uri.getScheme())||host==null||!host.matches("[0-9.]+")||!java.net.InetAddress.getByName(host).isSiteLocalAddress()||uri.getPort()<1||uri.getPort()>65535||uri.getUserInfo()!=null||!"/".equals(uri.getPath())||uri.getFragment()!=null)throw new IllegalArgumentException();
                java.util.Map<String,String> q=RoomServer.query(uri.getRawQuery());if(!q.getOrDefault("r","").matches("[0-9]{6}")||!q.getOrDefault("i","").matches("[0-9a-f]{32}"))throw new IllegalArgumentException();
                joinedOrigin="http://"+host+":"+uri.getPort();web.loadUrl(text.trim());
            }catch(Exception e){Toast.makeText(MainActivity.this,"请粘贴搭档二维码中的局域网房间链接",Toast.LENGTH_LONG).show();}
        });}
        @JavascriptInterface public void homeRoom(){runOnUiThread(()->{joinedOrigin="";waitForServer(0);});}
        @JavascriptInterface public void playMode(boolean play){runOnUiThread(()->{
            setRequestedOrientation(play?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){if(play){controller.hide(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}else controller.show(WindowInsets.Type.systemBars());}}
        });}
  
        @JavascriptInterface public void wifi(){runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}catch(Exception ignored){}});}
        @JavascriptInterface public void hotspot(){runOnUiThread(()->{try{Intent i=new Intent();i.setClassName("com.android.settings","com.android.settings.TetherSettings");startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));}});}
        @JavascriptInterface public void copy(String text){runOnUiThread(()->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("房间链接",text));Toast.makeText(MainActivity.this,"房间链接已复制",Toast.LENGTH_SHORT).show();});}
        @JavascriptInterface public void share(String text){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"邀请朋友入座"));});}
        @JavascriptInterface public void quit(){runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("结束本地房间服务？").setMessage("其他玩家将断开连接，本次闯关不会保存。").setNegativeButton("继续玩",null).setPositiveButton("结束并退出",(d,w)->{stopService(new Intent(MainActivity.this,HostService.class));finish();}).show());}
    }
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.onNativeBack && window.onNativeBack()",null);}
    @Override public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(web!=null){web.removeJavascriptInterface("Phone");web.destroy();}super.onDestroy();}
}
