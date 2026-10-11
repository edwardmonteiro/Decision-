package com.edward.tracado;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Color;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.view.WindowInsets;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech speech;
    private boolean speechReady;
    private long lastBuzz;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,18,30));
        getWindow().setNavigationBarColor(Color.rgb(7,18,30));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7,18,30));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return android.os.Build.VERSION.SDK_INT>=30?WindowInsets.CONSUMED:insets.consumeSystemWindowInsets();
        });
        web=new WebView(this);web.setBackgroundColor(Color.rgb(7,18,30));
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);s.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                Uri u=req.getUrl();String path=u.getPath();
                if(!"https".equals(u.getScheme())||!"tracado.local".equals(u.getHost())||u.getPort()!=-1||!"GET".equals(req.getMethod())||!("/index.html".equals(path)||"/style.css".equals(path)||"/race.js".equals(path)||"/tracks.js".equals(path)||"/game.js".equals(path)))return blocked();
                try{return new WebResourceResponse(path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html","UTF-8",getAssets().open(path.substring(1)));}catch(Exception e){return blocked();}
            }
        });
        web.addJavascriptInterface(new Bridge(),"Android");
        root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();
        speech=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS&&speech!=null){speechReady=speech.setLanguage(new Locale("pt","BR"))>=0;speech.setSpeechRate(.86f);}});
        web.loadUrl("https://tracado.local/index.html");
    }
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));}
    private class Bridge {
        @JavascriptInterface public void speak(String text){if(text==null||text.length()>600)return;runOnUiThread(()->{if(speechReady)speech.speak(text,TextToSpeech.QUEUE_FLUSH,null,"hint");});}
        @JavascriptInterface public void haptic(){runOnUiThread(()->{long now=android.os.SystemClock.elapsedRealtime();if(now-lastBuzz<120)return;lastBuzz=now;Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(16,70));});}
        @JavascriptInterface public void exit(){runOnUiThread(()->finish());}
    }
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.TracadoBack&&window.TracadoBack()",null);else super.onBackPressed();}
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("window.TracadoPause&&window.TracadoPause()",null);web.onPause();}if(speech!=null)speech.stop();super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){if(speech!=null){speech.stop();speech.shutdown();}if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
