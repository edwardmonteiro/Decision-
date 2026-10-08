package com.edward.flights;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    private volatile FlightAgent ai;
    private volatile String apiKey="";
    private volatile HttpsURLConnection liveConnection;
    private volatile boolean destroyed,configuring;
    private final AtomicBoolean streamRunning=new AtomicBoolean();
    private final AtomicBoolean deadlineQueued=new AtomicBoolean();
    private static final String ORIGIN="https://decision.local";
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);prefs=getSharedPreferences("decision_flights",MODE_PRIVATE);
        if(prefs.contains("api_key"))try{apiKey=decrypt(prefs.getString("api_key",""));ai=createAgent(apiKey);}catch(Exception e){prefs.edit().remove("api_key").apply();}
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
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();String p=u.getPath();
                if(!"https".equals(u.getScheme())||!"decision.local".equals(u.getHost())||!("/index.html".equals(p)||"/app.css".equals(p)||"/app.js".equals(p)))return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",null,new ByteArrayInputStream(new byte[0]));
                try{return new WebResourceResponse(p.endsWith(".js")?"application/javascript":p.endsWith(".css")?"text/css":"text/html","UTF-8",getAssets().open(p.substring(1)));}catch(IOException e){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(WebView v,String url){push();}
        });
        web.addJavascriptInterface(new Bridge(),"Android");root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);root.requestApplyInsets();web.loadUrl(ORIGIN+"/index.html");
        watchdog.scheduleWithFixedDelay(()->{
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
    private FlightAgent createAgent(String key) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte b:digest)h.append(String.format("%02x",b));String prefix="oa_"+h.substring(0,16)+"_";
        FlightAgent.Store store=new FlightAgent.Store(){public String get(String k){return prefs.getString(prefix+k,"");}public void put(String k,String v){prefs.edit().putString(prefix+k,v).commit();}};
        return new FlightAgent((p,m,b,i)->call(key,p,m,b,i),store);
    }
    private HttpsURLConnection connection(String key,String path) throws Exception {
        if(!path.matches("/(decisions|vaults(/[A-Za-z0-9_-]+)?|agents/(sessions|environments)(/[A-Za-z0-9_-]+)?(/(events|turns|items))?)(\\?[A-Za-z0-9_=&-]+)?"))throw new IOException("Invalid API path");
        HttpsURLConnection c=(HttpsURLConnection)new URL("https://api.openai.com/v1"+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(45000);
        c.setRequestProperty("Authorization","Bearer "+key);c.setRequestProperty("OpenAI-Beta","agents=v1");return c;
    }
    private String errorMessage(int status){
        return status==401?"Chave OpenAI inválida (401).":status==403?"Sua chave não tem acesso ao serviço (403).":status==404?"Recurso ou modelo indisponível (404).":status==429?"Limite de uso ou saldo OpenAI (429).":status==400?"A OpenAI rejeitou a configuração (400).":"Falha da OpenAI (HTTP "+status+").";
    }
    private JSONObject call(String key,String path,String method,JSONObject body,String idempotencyKey)throws Exception {
        HttpsURLConnection c=connection(key,path);
        try{
            c.setRequestMethod(method);c.setRequestProperty("Content-Type","application/json");
            if(idempotencyKey!=null)c.setRequestProperty("Idempotency-Key",idempotencyKey);
            if(body!=null){c.setDoOutput(true);try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int status=c.getResponseCode();if(status<200||status>=300)throw new FlightAgent.ApiError(status,errorMessage(status));
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
    private void push(){FlightAgent a=ai;if(a!=null)try{js("DecisionState",a.snapshot().toString());}catch(Exception ignored){}}
    private void result(String id,JSONObject response){runOnUiThread(()->{if(web!=null&&!destroyed)web.evaluateJavascript("window.DecisionNativeResult("+JSONObject.quote(id)+","+JSONObject.quote(response.toString())+")",null);});}
    private void startStream(){
        FlightAgent a=ai;if(a==null)return;String id=a.streamSession();if(id.isEmpty()||!streamRunning.compareAndSet(false,true))return;
        streamExecutor.execute(()->{
            HttpsURLConnection c=null;
            try{
                c=connection(apiKey,"/agents/sessions/"+FlightContracts.resource(id)+"/events?stream=true");liveConnection=c;c.setReadTimeout(65000);c.setRequestProperty("Accept","text/event-stream");
                if(c.getResponseCode()!=200)return;a.streamStatus(true);push();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
                    String line;StringBuilder data=new StringBuilder();long lastPush=0;
                    while(!destroyed&&ai==a&&!a.streamSession().isEmpty()&&(line=reader.readLine())!=null){
                        if(line.isEmpty()){
                            if(data.length()>0){String value=data.toString();data.setLength(0);if(value.equals("[DONE]"))break;
                                JSONObject event=new JSONObject(value);a.onEvent(event);String type=event.optString("type");long now=System.currentTimeMillis();
                                if(now-lastPush>350||type.endsWith("completed")||type.endsWith("requires_action")){push();lastPush=now;}
                                JSONObject turn=event.optJSONObject("turn");if(turn!=null&&turn.isNull("subagent_id")&&(type.endsWith(".completed")||type.endsWith(".failed")||type.endsWith(".cancelled"))){push();break;}
                            }
                        }else if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());if(data.length()>6500000)throw new IOException("Event too large");}
                    }
                }
            }catch(Exception ignored){/* Saved-session polling recovers missed events; never resend input here. */}
            finally{a.streamStatus(false);if(c!=null)c.disconnect();liveConnection=null;streamRunning.set(false);push();}
        });
    }
    private void stopStream(){HttpsURLConnection c=liveConnection;if(c!=null)c.disconnect();}
    private void connect(){
        if(configuring)return;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);int pad=(int)(22*getResources().getDisplayMetrics().density);panel.setPadding(pad,pad,pad,pad);
        TextView info=new TextView(this);info.setText("Conecte sua chave OpenAI. Ela fica criptografada neste celular. O navegador e as sessões executam na sua conta OpenAI, com cobrança de API. O teste verifica Decisions e conecta um Vault.");info.setTextSize(15);panel.addView(info);
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Chave de API OpenAI");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);panel.addView(input);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Conectar OpenAI").setView(panel).setNegativeButton("Cancelar",(v,w)->input.setText("")).setPositiveButton("Conectar e testar",null).create();
        d.setOnShowListener(v->{d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            String key=input.getText().toString().trim();if(!key.startsWith("sk-")||key.length()<24||key.length()>1024||key.matches(".*\\s.*")){input.setError("Confira sua chave OpenAI.");return;}
            input.setText("");d.dismiss();configuring=true;js("DecisionConnection","Conectando à OpenAI…");
            control.execute(()->{
                try{if(ai!=null)ai.close();stopStream();prefs.edit().putString("api_key",encrypt(key)).commit();apiKey=key;ai=createAgent(key);ai.test();js("DecisionConnection","Conexão testada. Você pode iniciar a busca.");}
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
            if(!id.matches("[0-9]{1,10}")||!operation.matches("state|test|search|poll|approve|cancel|close|retry|disconnect")||raw.length()>16000)return;
            control.execute(()->{
                try{
                    FlightAgent a=ai;if(a==null)throw new FlightAgent.ApiError(401,configuring?"Conexão em preparo…":"Conecte a OpenAI nas configurações.");
                    if(operation.equals("disconnect")){a.close();stopStream();ai=null;apiKey="";prefs.edit().remove("api_key").commit();result(id,new JSONObject().put("configured",false));return;}
                    JSONObject answer=a.handle(operation,new JSONObject(raw));result(id,answer);
                    if(operation.equals("cancel")||operation.equals("close"))stopStream();else startStream();
                }catch(Exception e){try{JSONObject out=new JSONObject().put("error",safeError(e));if(ai!=null)out.put("state",ai.snapshot());result(id,out);}catch(Exception ignored){}}
            });
        }
    }
    @Override protected void onPause(){super.onPause();if(web!=null){web.evaluateJavascript("window.DecisionPause&&window.DecisionPause()",null);web.onPause();}}
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();web.evaluateJavascript("window.DecisionResume&&window.DecisionResume()",null);}}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("window.DecisionBack&&window.DecisionBack()",null);}
    @Override protected void onDestroy(){destroyed=true;watchdog.shutdownNow();stopStream();control.shutdown();streamExecutor.shutdownNow();if(web!=null){web.removeJavascriptInterface("Android");web.destroy();web=null;}super.onDestroy();}
}
