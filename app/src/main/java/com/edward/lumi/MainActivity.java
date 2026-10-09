package com.edward.lumi;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.InputType;
import android.view.WindowManager;
import java.security.MessageDigest;
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
    private android.speech.tts.TextToSpeech voice;
    private volatile boolean voiceReady;
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private volatile DirectAi ai;
    private volatile LiveVoice live;
    private volatile boolean microphoneAllowed, foreground;
    private String microphoneRequest;
    private final android.os.Handler voiceTimer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable voiceDeadline=()->stopVoice("Tempo da conversa encerrado. Toque no microfone para conversar de novo.");
    private volatile boolean configuring;
    private static final String ORIGIN="https://lumi.local";
    private SharedPreferences prefs;
    private long lastHaptic;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("lumi_connection",MODE_PRIVATE);
        voice=new android.speech.tts.TextToSpeech(this,status->{if(status==android.speech.tts.TextToSpeech.SUCCESS&&voice!=null){int lang=voice.setLanguage(new java.util.Locale("pt","BR"));voice.setSpeechRate(.86f);voiceReady=lang>=0;}});
        if(prefs.contains("openai_key")){try{DirectAi restored=createAi(decrypt(prefs.getString("openai_key","")));ai=restored;network.execute(restored::recover);}catch(Exception e){prefs.edit().remove("openai_key").apply();}}
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
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){
                runOnUiThread(()->{
                    String[] resources=request.getResources();
                    if("https".equals(request.getOrigin().getScheme())&&"lumi.local".equals(request.getOrigin().getHost())&&request.getOrigin().getPort()==-1&&resources.length==1&&PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[0])&&microphoneAllowed&&foreground&&checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});}
                    else request.deny();
                });
            }
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                Uri u=request.getUrl();String path=u.getPath();
                if(!"https".equals(u.getScheme())||!"lumi.local".equals(u.getHost())||!("/index.html".equals(path)||"/style.css".equals(path)||"/game.js".equals(path)||"/live-voice.js".equals(path)||"/levels.js".equals(path)))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
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
    private DirectAi createAi(String apiKey) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format("%02x",b));
        String prefix="oa_"+hex.substring(0,16)+"_";
        DirectAi.Store store=new DirectAi.Store(){
            public String get(String k){return prefs.getString(prefix+k,"");}
            public void put(String k,String v){prefs.edit().putString(prefix+k,v).commit();}
            public void remove(String k){prefs.edit().remove(prefix+k).commit();}
        };
        String config;try(InputStream in=getAssets().open("live-session.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);config=out.toString("UTF-8");}
        LiveVoice next=new LiveVoice((path,method,body)->openai(apiKey,path,method,body),store,config);live=next;network.execute(next::recover);
        return new DirectAi((path,method,body)->openai(apiKey,path,method,body),store);
    }
    private JSONObject openai(String apiKey,String path,String method,JSONObject body) throws Exception {
        if(!path.matches("/(live/sessions(/[A-Za-z0-9_-]{1,200}/hangup)?|decisions|vaults(/[A-Za-z0-9_-]+)?|agents/(sessions|environments)(/[A-Za-z0-9_-]+)?(/(turns|items))?)(\\?[A-Za-z0-9_=&-]+)?"))throw new IOException("Invalid API path");
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.openai.com/v1"+path).openConnection();
        try {
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(path.equals("/decisions")?35000:18000);
            c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+apiKey);
            c.setRequestProperty("Content-Type","application/json");if(!path.startsWith("/live/"))c.setRequestProperty("OpenAI-Beta","agents=v1");
            if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();
            if(status<200||status>=300){
                String message=status==401?"Chave inválida (HTTP 401)":status==403?"Sem permissão para este serviço (HTTP 403)":status==404?"Recurso ou modelo indisponível (HTTP 404)":status==429?"Limite de uso ou saldo OpenAI (HTTP 429)":status==400?"OpenAI não aceitou a configuração (HTTP 400)":"OpenAI indisponível (HTTP "+status+")";
                throw new DirectAi.ApiError(status,message);
            }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>2000000)throw new IOException("Response too large");out.write(buffer,0,n);}
                JSONObject response=out.size()==0?new JSONObject():new JSONObject(out.toString("UTF-8"));
                String requestId=c.getHeaderField("x-request-id");
                response.remove("_request_id");
                if(requestId!=null&&requestId.matches("[A-Za-z0-9_-]{1,160}"))response.put("_request_id",requestId);
                return response;
            }
        } finally {c.disconnect();}
    }
    private void notifyConnection(String message){runOnUiThread(()->{if(web!=null&&!isFinishing())web.evaluateJavascript("window.LumiConnectionChanged&&window.LumiConnectionChanged("+JSONObject.quote(message)+")",null);});}
    private void setupOpenAI(){
        if(configuring)return;
        stopVoice("");
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);int pad=(int)(22*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);
        TextView info=new TextView(this);info.setText("Cole sua chave de API OpenAI. Ela fica criptografada neste celular. Ao experimentar, o desenho é enviado à OpenAI. Quando você liga a voz, o áudio e as condições do rio também são enviados. Evite dados pessoais. O teste lê um desenho e prepara um desafio, com cobrança de API. Nenhum servidor próprio é necessário.");info.setTextSize(15);layout.addView(info);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Chave de API OpenAI");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);layout.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Conectar OpenAI").setView(layout).setNegativeButton("Cancelar",(d,w)->input.setText("")).setPositiveButton("Conectar e testar",null).create();
        dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String apiKey=input.getText().toString().trim();
            if(!apiKey.startsWith("sk-")||apiKey.length()<24||apiKey.length()>1024||apiKey.matches(".*\\s.*")){input.setError("Informe uma chave de API válida.");return;}
            input.setText("");dialog.dismiss();configuring=true;notifyConnection("Conectando diretamente à OpenAI…");
            network.execute(()->{try{DirectAi previous=ai;ai=null;if(previous!=null)previous.stop();prefs.edit().putString("openai_key",encrypt(apiKey)).commit();ai=createAi(apiKey);ai.recover();ai.activate();notifyConnection("Conexão testada. Confira o resultado de cada serviço.");}catch(Exception e){notifyConnection("Não foi possível concluir a conexão. Confira sua chave e o acesso da conta.");}finally{configuring=false;}});
        });});
        dialog.setOnDismissListener(d->input.setText(""));dialog.show();
    }
    public class Bridge {
        @JavascriptInterface public boolean isConfigured(){return ai!=null&&prefs.contains("openai_key");}
        @JavascriptInterface public void openOpenAISetup(){runOnUiThread(()->setupOpenAI());}
        @JavascriptInterface public void clearConfig(){
            runOnUiThread(()->MainActivity.this.stopVoice("Chave removida. Voz desligada."));
            DirectAi previous=ai;ai=null;prefs.edit().remove("openai_key").apply();
            if(previous!=null)network.execute(previous::stop);
        }
        @JavascriptInterface public void speak(String text){if(text.length()<=240&&voiceReady)runOnUiThread(()->voice.speak(text,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"lumi-instruction"));}
        @JavascriptInterface public void prepareVoice(String id){if(id.matches("[0-9]{1,10}"))runOnUiThread(()->prepareMicrophone(id));}
        @JavascriptInterface public void stopVoice(){runOnUiThread(()->MainActivity.this.stopVoice(""));}
        @JavascriptInterface public void voiceClosed(String id){LiveVoice service=live;if(service!=null)service.confirmed(id);runOnUiThread(()->{microphoneAllowed=false;voiceTimer.removeCallbacks(voiceDeadline);});}
        @JavascriptInterface public void startVoice(String id,String body){
            if(!id.matches("[0-9]{1,10}")||body.length()>75000){result(id,error("Pedido de voz inválido"));return;}
            LiveVoice service=live;
            if(service==null||!microphoneAllowed||!foreground||configuring){result(id,error("Ligue o microfone pelo botão de voz."));return;}
            network.execute(()->{try{result(id,service.start(new JSONObject(body)));}catch(DirectAi.ApiError e){result(id,error(e.getMessage()));}catch(Exception e){result(id,error("Não foi possível conectar a voz à OpenAI. Confira a internet e o acesso a GPT-Live-1."));}});
        }
        @JavascriptInterface public void haptic(){long now=android.os.SystemClock.elapsedRealtime();if(now-lastHaptic<200)return;lastHaptic=now;Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null&&v.hasVibrator())v.vibrate(VibrationEffect.createOneShot(18,45));}
        @JavascriptInterface public void request(String id,String path,String method,String body){
            if(!id.matches("[0-9]{1,10}")||!path.matches("/(health|activate|v1/(challenge|classify|outcomes))")||!(method.equals("GET")||method.equals("POST")||method.equals("DELETE"))||body.length()>(path.equals("/v1/classify")?360000:8192)){result(id,error("Requisição inválida"));return;}
            network.execute(()->{try{DirectAi service=ai;if(service==null){result(id,error(configuring?"Conexão em preparo…":"Conecte a OpenAI no aplicativo."));return;}result(id,service.handle(path,method,new JSONObject(body)));}catch(DirectAi.ApiError e){result(id,error(e.getMessage()));}catch(Exception e){result(id,error("Falha de conexão ou resposta inválida. O jogo continua offline."));}});
        }
    }
    private void prepareMicrophone(String id){
        if(ai==null||configuring){result(id,error("Conecte a OpenAI na área dos responsáveis."));return;}
        if(microphoneRequest!=null){result(id,error("A permissão do microfone já está aberta."));return;}
        if(voice!=null)voice.stop();
        if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED){enableMicrophone(id);return;}
        microphoneRequest=id;requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},71);
    }
    private void enableMicrophone(String id){microphoneAllowed=true;voiceTimer.removeCallbacks(voiceDeadline);voiceTimer.postDelayed(voiceDeadline,120000);try{result(id,new JSONObject().put("allowed",true));}catch(Exception ignored){}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==71&&microphoneRequest!=null){String id=microphoneRequest;microphoneRequest=null;if(grants.length>0&&grants[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)enableMicrophone(id);else result(id,error("Microfone não autorizado. Você pode continuar desenhando."));}}
    private void stopVoice(String message){
        microphoneAllowed=false;voiceTimer.removeCallbacks(voiceDeadline);if(microphoneRequest!=null){result(microphoneRequest,error("Conversa cancelada"));microphoneRequest=null;}
        LiveVoice service=live;if(service!=null){service.cancel();if(!network.isShutdown())network.execute(service::recover);}
        if(web!=null&&!isFinishing())web.evaluateJavascript("window.LumiVoiceStop&&window.LumiVoiceStop("+JSONObject.quote(message)+")",null);
    }
    @Override protected void onPause(){foreground=false;if(microphoneRequest==null)stopVoice("Voz desligada ao sair do jogo.");if(voice!=null)voice.stop();if(web!=null){if(microphoneRequest==null)web.evaluateJavascript("window.LumiPause&&window.LumiPause()",null);web.onPause();}super.onPause();}
    @Override protected void onResume(){super.onResume();foreground=true;if(web!=null){web.onResume();web.evaluateJavascript("window.LumiResume&&window.LumiResume()",null);}}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.LumiBack&&window.LumiBack()",null);}
    @Override protected void onDestroy(){stopVoice("");if(voice!=null){voice.stop();voice.shutdown();voiceReady=false;}DirectAi service=ai;ai=null;if(service!=null)network.execute(service::stop);network.shutdown();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
