package com.edward.lumi;

import android.app.Activity;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.FrameLayout;
import android.view.WindowInsets;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;
import java.security.KeyStore;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService network = Executors.newFixedThreadPool(2);
    private static final String ORIGIN="https://lumi.local";
    private SharedPreferences prefs;
    private long lastHaptic;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("lumi_connection",MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(7,18,30));
        getWindow().setNavigationBarColor(Color.rgb(7,18,30));
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(7,18,30));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);}else{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());}
            return android.os.Build.VERSION.SDK_INT>=30?WindowInsets.CONSUMED:insets.consumeSystemWindowInsets();
        });
        web=new WebView(this);web.setBackgroundColor(Color.rgb(7,18,30));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportZoom(false);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();String path=u.getPath();
                if(!"https".equals(u.getScheme())||!"lumi.local".equals(u.getHost())||!("/index.html".equals(path)||"/style.css".equals(path)||"/game.js".equals(path)))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
                try{String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html";return new WebResourceResponse(mime,"UTF-8",getAssets().open(path.substring(1)));}catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            }
        });
        web.addJavascriptInterface(new Bridge(),"Android");root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();web.loadUrl(ORIGIN+"/index.html");
    }
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("lumi_connection")){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("lumi_connection",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
        return ((KeyStore.SecretKeyEntry)ks.getEntry("lumi_connection",null)).getSecretKey();
    }
    private String encrypt(String text)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private String decrypt(String text)throws Exception{String[] parts=text.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
    private void result(String id,JSONObject value){runOnUiThread(()->{if(!isFinishing()&&web!=null)web.evaluateJavascript("window.LumiNativeResult("+JSONObject.quote(id)+","+JSONObject.quote(value.toString())+")",null);});}
    private JSONObject error(String text){JSONObject o=new JSONObject();try{o.put("error",text);}catch(Exception ignored){}return o;}
    public class Bridge {
        @JavascriptInterface public boolean isConfigured(){return prefs.contains("token")&&prefs.contains("endpoint");}
        @JavascriptInterface public String getEndpoint(){return prefs.getString("endpoint","");}
        @JavascriptInterface public String configure(String endpoint,String token){
            try{Uri u=Uri.parse(endpoint);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null)throw new Exception();String clean=endpoint.replaceAll("/+$","");if(token.startsWith("sk-")||token.length()<24||token.length()>512)return error("Utilize o código do servidor, não uma chave OpenAI.").toString();prefs.edit().putString("endpoint",clean).putString("token",encrypt(token)).apply();return "{\"saved\":true}";}catch(Exception e){return error("Informe um endereço HTTPS e um código válido.").toString();}
        }
        @JavascriptInterface public void clearConfig(){prefs.edit().clear().apply();}
        @JavascriptInterface public void haptic(){long now=android.os.SystemClock.elapsedRealtime();if(now-lastHaptic<200)return;lastHaptic=now;Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(18,45));}
        @JavascriptInterface public void request(String id,String path,String method,String body){
            if(!id.matches("[0-9]{1,10}")||!path.matches("/(health|v1/runs(/[a-f0-9-]{36}(/decision)?)?)")||!(method.equals("GET")||method.equals("POST")||method.equals("DELETE"))||body.length()>8192){result(id,error("Requisição inválida"));return;}
            network.execute(()->{HttpsURLConnection c=null;try{if(!isConfigured())throw new Exception();String endpoint=prefs.getString("endpoint","");String token=decrypt(prefs.getString("token",""));c=(HttpsURLConnection)new URL(endpoint+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(8000);c.setReadTimeout(18000);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Content-Type","application/json");if(method.equals("POST")){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.getBytes(StandardCharsets.UTF_8));}}
                int status=c.getResponseCode();if(status<200||status>=300){result(id,error(status==401?"Código de conexão inválido.":status==429?"Limite de IA atingido. O jogo continua offline.":"IA indisponível. O jogo continua offline."));return;}try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>65536)throw new IOException();out.write(buffer,0,n);}result(id,new JSONObject(out.toString("UTF-8")));}
            }catch(Exception e){result(id,error("Não foi possível conectar. O jogo continua offline."));}finally{if(c!=null)c.disconnect();}});
        }
    }
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("window.LumiPause&&window.LumiPause()",null);web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();web.evaluateJavascript("window.LumiResume&&window.LumiResume()",null);}}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.LumiBack&&window.LumiBack()",null);}
    @Override protected void onDestroy(){network.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
