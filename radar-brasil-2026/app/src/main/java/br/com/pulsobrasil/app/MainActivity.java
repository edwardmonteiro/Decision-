package br.com.pulsobrasil.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.graphics.Color;
import android.view.WindowManager;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,17,29));
        getWindow().setNavigationBarColor(Color.rgb(11,17,29));
        getWindow().getDecorView().setSystemUiVisibility(0);
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(11,17,29));
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request){
                Uri uri=request.getUrl();
                if(!"https".equals(uri.getScheme())) return true;
                startActivity(new Intent(Intent.ACTION_VIEW,uri)); return true;
            }
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onBackPressed(){
        if(web.canGoBack()) web.goBack(); else super.onBackPressed();
    }
    public class Bridge {
        @JavascriptInterface public void load(String tag,String url){
            if(!url.startsWith("https://resultados.tse.jus.br/") &&
               !url.startsWith("https://resultados-sim.tse.jus.br/")) {
                reply(tag,false,"Fonte não permitida");return;
            }
            io.submit(()->{
                HttpURLConnection conn=null;
                try{
                    conn=(HttpURLConnection)new URL(url).openConnection();
                    conn.setConnectTimeout(8500);
                    conn.setReadTimeout(9500);
                    conn.setRequestProperty("Accept","application/json");
                    conn.setRequestProperty("User-Agent","Mozilla/5.0 PulsoBrasil/1.0");
                    if(conn.getResponseCode()!=200) throw new Exception("HTTP "+conn.getResponseCode());
                    int max=2500000;ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=conn.getInputStream()){
                        byte[] b=new byte[8192];int n;
                        while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>max)throw new Exception("Arquivo excede limite");}
                    }
                    String data=out.toString("UTF-8");
                    new JSONObject(data);
                    reply(tag,true,data);
                }catch(Exception ex){reply(tag,false,ex.getMessage()==null?"Falha de conexão":ex.getMessage());}
                finally{if(conn!=null)conn.disconnect();}
            });
        }
        @JavascriptInterface public void open(String url){
            if(url.startsWith("https://")){
                runOnUiThread(()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))));
            }
        }
        private void reply(String tag,boolean ok,String data) {
            String js="window.onNativeResult("+JSONObject.quote(tag)+","+ok+","+JSONObject.quote(data)+")";
            runOnUiThread(()->web.evaluateJavascript(js,null));
        }
    }
}