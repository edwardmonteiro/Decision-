package com.edward.flights;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private WebView web;
    private SharedPreferences prefs;
    private final ExecutorService control=Executors.newSingleThreadExecutor();
    private final ExecutorService streamExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService voiceControl=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private volatile FlightAgent ai;
    private volatile VoiceAgent voice;
    private volatile String apiKey="";
    private volatile HttpsURLConnection liveConnection;
    private volatile boolean destroyed,configuring,foreground;
    private volatile long streamRetryAt;
    private volatile String subscribedSession="";
    private PermissionRequest microphonePermission;
    private static final int MICROPHONE_REQUEST=41;
    private final AtomicBoolean streamRunning=new AtomicBoolean();
    private final AtomicBoolean deadlineQueued=new AtomicBoolean();
    private static final String ORIGIN="https://decision.local";
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("decision_flights",MODE_PRIVATE);
        if(prefs.contains("api_key"))try{apiKey=decrypt(prefs.getString("api_key",""));ai=createAgent(apiKey);voice=createVoice(apiKey);}catch(Exception e){prefs.edit().remove("api_key").apply();}
        getWindow().setStatusBarColor(Color.WHITE);getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return WindowInsets.CONSUMED;}
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();
        });
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setSupportZoom(false);WebView.setWebContentsDebuggingEnabled(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{
                String[] resources=request.getResources();
                if(destroyed||!foreground||ai==null||!ORIGIN.equals(request.getOrigin().toString().replaceAll("/$",""))||resources.length!=1||!PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[0])){request.deny();return;}
                if(checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});return;}
                if(microphonePermission!=null){request.deny();return;}
                microphonePermission=request;requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},MICROPHONE_REQUEST);
            });}
            @Override public void onPermissionRequestCanceled(PermissionRequest request){runOnUiThread(()->{if(microphonePermission==request)microphonePermission=null;});}
        });
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String p=u.getPath();
                if(!"https".equals(u.getScheme())||!"decision.local".equals(u.getHost())||!("/index.html".equals(p)||"/app.css".equals(p)||"/app.js".equals(p)||"/voice.js".equals(p)))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
                try{return new WebResourceResponse(p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":"text/html","UTF-8",getAssets().open(p.substring(1)));}catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(WebView v,String url){push();}
        });
        web.addJavascriptInterface(new Bridge(),"Android");root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();web.loadUrl(ORIGIN+"/index.html");
        watchdog.scheduleWithFixedDelay(()->{
            VoiceAgent v=voice;if(!destroyed&&v!=null&&v.expired()){js("DecisionVoiceEnd","");closeVoice();}
            if(destroyed||!deadlineQueued.compareAndSet(false,true))return;
            try{control.execute(()->{try{FlightAgent a=ai;if(a!=null&&a.enforceDeadline()){stopStream();push();}}catch(Exception ignored){/* Retry the guard on the next tick. */}finally{deadlineQueued.set(false);}});}
            catch(RejectedExecutionException ignored){deadlineQueued.set(false);}
        },15,15,TimeUnit.SECONDS);
    }
    private SecretKey encryptionKey() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(!ks.containsAlias("decision_flights")){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder("decision_flights",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());g.generateKey();}
        return ((KeyStore.SecretKeyEntry)ks.getEntry("decision_flights",null)).getSecretKey();
    }
    private String encrypt(String text)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,encryptionKey());return Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(text.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);}
    private String decrypt(String text)throws Exception{String[] p=text.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,encryptionKey(),new GCMParameterSpec(128,Base64.decode(p[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(p[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}
    private FlightAgent.Store accountStore(String key) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format("%02x",b));String prefix="oa_"+h.substring(0,16)+"_";
        return new FlightAgent.Store(){public String get(String k){return prefs.getString(prefix+k,"");}public void put(String k,String v){prefs.edit().putString(prefix+k,v).commit();}};
    }
    private FlightAgent createAgent(String key)throws Exception{return new FlightAgent(new FlightAgent.Transport(){
        public JSONObject call(String p,String m,JSONObject b,String i)throws Exception{return MainActivity.this.call(key,p,m,b,i);}
        public void watch(String id){streamRetryAt=0;startStream(id,true);}
        public void updated(){push();}
    },accountStore(key));}
    private VoiceAgent createVoice(String key)throws Exception{return new VoiceAgent((p,m,b,i)->call(key,p,m,b,i),accountStore(key));}
    private HttpsURLConnection connection(String key,String path) throws Exception {
        FlightContracts.apiPath(path);
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.openai.com/v1"+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(45000);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("OpenAI-Beta","agents=v1");return c;
    }
    private JSONObject call(String key,String path,String method,JSONObject body,String idempotencyKey)throws Exception {
        HttpsURLConnection c=connection(key,path);
        try{
            c.setRequestMethod(method);if(method.equals("GET"))c.setReadTimeout(15000);c.setRequestProperty("Content-Type","application/json");
            if(idempotencyKey!=null)c.setRequestProperty("Idempotency-Key",idempotencyKey);
            if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();if(status<200||status>=300){
                String raw="";try{raw=OpenAiErrors.readBody(c.getErrorStream(),16384);}catch(Exception ignored){}
                throw OpenAiErrors.fromResponse(status,path,raw,c.getHeaderField("x-request-id"),key);
            }
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>12000000)throw new IOException("Provider response too large");out.write(buffer,0,n);}
                JSONObject response=out.size()==0?new JSONObject():new JSONObject(out.toString("UTF-8"));response.remove("_request_id");
                String id=c.getHeaderField("x-request-id");if(id!=null&&id.matches("[A-Za-z0-9_-]{1,160}"))response.put("_request_id",id);return response;
            }
        }finally{c.disconnect();}
    }
    private static String safeError(Exception e){
        if(e instanceof FlightAgent.ApiError)return e.getMessage();
        if(e instanceof java.net.SocketTimeoutException)return "A conexão demorou. Consulte a sessão para recuperar a busca.";
        if(e instanceof java.net.UnknownHostException)return "Sem conexão com a OpenAI. Confira a internet.";
        if(e instanceof javax.net.ssl.SSLException)return "Falha na conexão segura com a OpenAI.";
        if(e instanceof java.time.DateTimeException)return "Confira as datas da viagem.";
        return "Não foi possível validar a resposta. Consulte a sessão antes de repetir a busca.";
    }
    private void js(String function,String argument){runOnUiThread(()->{if(web!=null&&!destroyed&&!isFinishing())web.evaluateJavascript("window."+function+"&&window."+function+"("+JSONObject.quote(argument)+")",null);});}
    private JSONObject withVoice(JSONObject value)throws Exception{VoiceAgent v=voice;if(v!=null)value.put("voice",v.snapshot());return value;}
    private void push(){FlightAgent a=ai;if(a!=null)try{js("DecisionState",withVoice(a.snapshot()).toString());}catch(Exception ignored){}}
    private void result(String id,JSONObject response){runOnUiThread(()->{if(web!=null&&!destroyed)web.evaluateJavascript("window.DecisionNativeResult("+JSONObject.quote(id)+","+JSONObject.quote(response.toString())+")",null);});}
    private void startStream(){FlightAgent a=ai;if(a!=null)startStream(a.streamSession(),false);}
    private void startStream(String id,boolean beforeInput){
        FlightAgent a=ai;if(a==null||id.isEmpty()||destroyed||System.currentTimeMillis()<streamRetryAt)return;
        if(streamRunning.get()&&!id.equals(subscribedSession))stopStream();
        if(!streamRunning.compareAndSet(false,true))return;
        subscribedSession=id;
        CountDownLatch ready=new CountDownLatch(1);
        streamExecutor.execute(()->{
            HttpsURLConnection c=null;
            try{
                c=connection(apiKey,"/agents/sessions/"+FlightContracts.resource(id)+"/events?stream=true");liveConnection=c;c.setReadTimeout(65000);c.setRequestProperty("Accept","text/event-stream");
                int status=c.getResponseCode();
                if(status!=200){String raw="";try{raw=OpenAiErrors.readBody(c.getErrorStream(),16384);}catch(Exception ignored){}
                    throw OpenAiErrors.fromResponse(status,"/agents/sessions/"+id+"/events",raw,c.getHeaderField("x-request-id"),apiKey);}
                if(!id.equals(a.streamSession()))return;
                a.streamStatus(true);ready.countDown();a.streamConnected();push();
                boolean endedOnRoot=false;
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                    String line;StringBuilder data=new StringBuilder();long lastPush=0;
                    while(!destroyed&&ai==a&&id.equals(a.streamSession())&&(line=reader.readLine())!=null){
                        if(ai!=a||!id.equals(a.streamSession()))break;
                        if(line.isEmpty()){
                            if(data.length()>0){String value=data.toString();data.setLength(0);if(value.equals("[DONE]"))break;
                                JSONObject event=new JSONObject(value);a.onEvent(event);String type=event.optString("type");long now=System.currentTimeMillis();
                                if(now-lastPush>350||type.endsWith("completed")||type.endsWith("requires_action")||type.endsWith("failed")||type.equals("error")){push();lastPush=now;}
                                JSONObject turn=event.optJSONObject("turn");if(turn!=null&&turn.isNull("subagent_id")&&turn.optString("id").equals(a.snapshot().optString("last_turn"))&&(type.endsWith(".completed")||type.endsWith(".failed")||type.endsWith(".cancelled"))){endedOnRoot=true;push();break;}
                            }
                        }else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());if(data.length()>6500000)throw new IOException("Event too large");}
                    }
                }
                if(!endedOnRoot&&id.equals(a.streamSession())){streamRetryAt=System.currentTimeMillis()+15000;a.streamFailure("O acompanhamento ao vivo foi interrompido. O andamento será consultado na mesma sessão.",null);}
            }catch(Exception e){
                ready.countDown();streamRetryAt=System.currentTimeMillis()+15000;
                try{if(ai==a&&id.equals(a.streamSession()))a.streamFailure("Sem acompanhamento ao vivo. "+safeError(e),e instanceof FlightAgent.ApiError?(FlightAgent.ApiError)e:null);}catch(Exception ignored){}
            }
            finally{ready.countDown();a.streamStatus(false);if(c!=null)c.disconnect();liveConnection=null;subscribedSession="";streamRunning.set(false);push();}
        });
        if(beforeInput)try{ready.await(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    private void stopStream(){HttpsURLConnection c=liveConnection;if(c!=null)c.disconnect();}
    private void connect(){
        js("DecisionVoiceEnd","");closeVoice();
        if(configuring)return;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);int pad=(int)(22*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad,pad,pad);
        TextView info=new TextView(this);info.setText("Conecte sua chave OpenAI. Ela fica criptografada neste celular. O navegador e as sessões executam na sua conta OpenAI, com cobrança de API. O teste verifica Decisions e conecta um Vault.");info.setTextSize(15);panel.addView(info);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Chave de API OpenAI");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);panel.addView(input);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Conectar OpenAI").setView(panel).setNegativeButton("Cancelar",(v,w)->input.setText("")).setPositiveButton("Conectar e testar",null).create();
        d.setOnShowListener(v->{d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String key=input.getText().toString().trim();if(!key.startsWith("sk-")||key.length()<24||key.length()>1024||key.matches(".*\\s.*")){input.setError("Confira sua chave OpenAI.");return;}
            input.setText("");d.dismiss();configuring=true;js("DecisionConnection","Conectando à OpenAI…");
            control.execute(()->{
                try{VoiceAgent previous=voice;if(previous!=null)previous.close();if(ai!=null)ai.close();stopStream();prefs.edit().putString("api_key",encrypt(key)).commit();apiKey=key;ai=createAgent(key);voice=createVoice(key);ai.test();js("DecisionConnection","Conexão testada. Você pode iniciar a busca.");}
                catch(Exception e){js("DecisionConnection",safeError(e));}finally{configuring=false;push();}
            });
        });});d.setOnDismissListener(v->input.setText(""));d.show();
    }
    public class Bridge {
        @JavascriptInterface public boolean isConfigured(){return ai!=null&&!apiKey.isEmpty();}
        @JavascriptInterface public void openOpenAISetup(){runOnUiThread(()->connect());}
        @JavascriptInterface public void openUrl(String url){
            boolean allowed=FlightContracts.googleUrl(url)||url.equals("https://platform.openai.com/agents");if(!allowed)return;
            runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception e){js("DecisionConnection","Não há navegador disponível para abrir o link.");}});
        }
        @JavascriptInterface public void request(String id,String operation,String raw){
            if(!id.matches("[0-9]{1,10}")||!operation.matches("state|test|search|poll|approve|cancel|close|retry|disconnect|live_start|live_close|live_finish|live_action")||raw.length()>(operation.equals("live_start")?65536:16000))return;
            if(operation.equals("live_start")||operation.equals("live_close")||operation.equals("live_finish")){
                voiceControl.execute(()->{
                    try{
                        VoiceAgent v=voice;FlightAgent a=ai;if(v==null||a==null||configuring)throw new FlightAgent.ApiError(401,"Conecte a OpenAI para conversar por voz.");
                        JSONObject body=new JSONObject(raw),answer;
                        if(operation.equals("live_start")){if(!foreground||destroyed)throw new FlightAgent.ApiError(409,"Abra o aplicativo para iniciar a voz.");answer=v.start(body,a.snapshot());}
                        else if(operation.equals("live_finish"))answer=v.finished(body);else answer=v.closeExpected(body);
                        result(id,new JSONObject().put("live",answer));push();
                    }catch(Exception e){try{JSONObject out=new JSONObject().put("error",safeError(e));if(ai!=null)out.put("state",withVoice(ai.snapshot()));result(id,out);}catch(Exception ignored){}}
                });return;
            }
            control.execute(()->{
                try{
                    FlightAgent a=ai;if(a==null)throw new FlightAgent.ApiError(401,configuring?"Conexão em preparo…":"Conecte a OpenAI nas configurações.");
                    if(operation.equals("disconnect")){if(voice!=null)voice.close();a.close();stopStream();ai=null;voice=null;apiKey="";prefs.edit().remove("api_key").commit();result(id,new JSONObject().put("configured",false));return;}
                    if(operation.equals("live_action")){
                        VoiceAgent v=voice;if(v==null)throw new FlightAgent.ApiError(409,"Conversa por voz encerrada.");
                        JSONObject answer=v.action(a,new JSONObject(raw));result(id,new JSONObject().put("live",answer.getJSONObject("result")).put("state",withVoice(answer.getJSONObject("state"))));startStream();return;
                    }
                    JSONObject answer=a.handle(operation,new JSONObject(raw));result(id,withVoice(answer));
                    if(operation.equals("cancel")||operation.equals("close"))stopStream();else startStream();
                }catch(Exception e){try{JSONObject out=new JSONObject().put("error",safeError(e));if(ai!=null)out.put("state",withVoice(ai.snapshot()));result(id,out);}catch(Exception ignored){}}
            });
        }
    }
    private void closeVoice(){VoiceAgent v=voice;if(v!=null&&!voiceControl.isShutdown())voiceControl.execute(()->{try{v.close();push();}catch(Exception ignored){/* Persisted ID permits closure recovery on resume. */}});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==MICROPHONE_REQUEST){PermissionRequest p=microphonePermission;microphonePermission=null;if(p!=null){if(!destroyed&&foreground&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)p.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});else p.deny();}}}
    @Override protected void onPause(){foreground=false;super.onPause();if(web!=null){web.evaluateJavascript("window.DecisionPause&&window.DecisionPause()",null);web.onPause();}closeVoice();}
    @Override protected void onResume(){foreground=true;super.onResume();closeVoice();if(web!=null){web.onResume();web.evaluateJavascript("window.DecisionResume&&window.DecisionResume()",null);}}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.DecisionBack&&window.DecisionBack()",null);}
    @Override protected void onDestroy(){destroyed=true;if(microphonePermission!=null){microphonePermission.deny();microphonePermission=null;}closeVoice();voiceControl.shutdown();watchdog.shutdownNow();stopStream();control.shutdown();streamExecutor.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
