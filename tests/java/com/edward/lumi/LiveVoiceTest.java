package com.edward.lumi;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public final class LiveVoiceTest {
    static int checks;
    static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    static class Store implements DirectAi.Store {
        final Map<String,String> values=new ConcurrentHashMap<>();
        public String get(String k){return values.getOrDefault(k,"");}
        public void put(String k,String v){values.put(k,v);}
        public void remove(String k){values.remove(k);}
    }
    static class Wire implements DirectAi.Transport {
        int creates,hangups;boolean broken,denied,cleanupFailed;JSONObject request;
        CountDownLatch entered,release;
        public JSONObject call(String path,String method,JSONObject body)throws Exception{
            if(path.endsWith("/hangup")){hangups++;check(method.equals("POST")&&body==null,"documented hangup");if(cleanupFailed)throw new DirectAi.ApiError(503,"fixture");return new JSONObject();}
            check(path.equals("/live/sessions")&&method.equals("POST"),"only live creation");creates++;request=body;
            if(entered!=null){entered.countDown();release.await(3,TimeUnit.SECONDS);}
            if(denied)throw new DirectAi.ApiError(403,"fixture denied");
            return new JSONObject().put("session",new JSONObject().put("id","live_fixture_"+creates)).put("transport",new JSONObject().put("type","webrtc").put("sdp",broken?"bad":"v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"));
        }
    }
    static JSONObject body()throws Exception{return new JSONObject("{\"sdp\":\"v=0\\r\\nm=audio 9 UDP/TLS/RTP/SAVPF 111\\r\\nm=application 9 UDP/DTLS/SCTP webrtc-datachannel\\r\\n\",\"challenge\":{\"width\":0.28,\"current\":\"calm\",\"cargo\":false,\"theme\":\"meadow\",\"focus\":\"bridge\",\"reason\":\"first\"}}");}
    interface Attempt{void run()throws Exception;}
    static void fails(Attempt f,String label)throws Exception{try{f.run();throw new AssertionError(label);}catch(DirectAi.ApiError expected){checks++;}}
    public static void main(String[] args)throws Exception{
        String config=Files.readString(Path.of("app/src/main/assets/live-session.json"));Wire w=new Wire();Store st=new Store();LiveVoice v=new LiveVoice(w,st,config);
        fails(()->v.start(body().put("instructions","untrusted")),"extra instructions rejected");check(w.creates==0,"no invalid outbound");
        fails(()->v.start(body().put("sdp","https://untrusted.example")),"remote URL rejected");
        JSONObject bad=body();bad.getJSONObject("challenge").put("width",9);fails(()->v.start(bad),"bounds rejected");
        JSONObject r=v.start(body());check(r.getString("session_id").equals("live_fixture_1"),"opaque session id preserved");check(r.length()==3,"only SDP/id/model exposed");
        JSONObject c=w.request.getJSONObject("session");check(c.getString("model").equals("gpt-live-1"),"latest live model");check(!c.getBoolean("store"),"storage disabled");check(c.getJSONObject("audio").getJSONObject("output").getString("voice").equals("bossa"),"Brazilian voice");check(!c.getJSONObject("audio").has("format"),"WebRTC negotiates media");
        check(c.getJSONObject("delegation").getString("type").equals("responses"),"managed backend");check(c.getJSONObject("delegation").getJSONObject("responses").getJSONArray("tools").length()==3,"exactly three bounded tools");
        check(w.request.getJSONObject("transport").getString("type").equals("webrtc"),"WebRTC contract");check(!w.request.toString().contains("image"),"no drawing uploaded by voice");
        fails(()->v.start(body()),"duplicate session blocked");
        v.confirmed("wrong_id");check(!st.get("voice_pending").isEmpty(),"unrelated close ignored");v.confirmed(r.getString("session_id"));check(st.get("voice_pending").isEmpty(),"confirmed close releases known id");
        w.broken=true;fails(()->v.start(body()),"malformed SDP rejected");check(w.hangups==1&&st.get("voice_pending").isEmpty(),"malformed response resource closed");w.broken=false;
        w.denied=true;fails(()->v.start(body()),"403 preserved");check(st.get("voice_count").equals("3"),"failed starts counted");w.denied=false;
        w.entered=new CountDownLatch(1);w.release=new CountDownLatch(1);ExecutorService pool=Executors.newSingleThreadExecutor();
        Future<?> pending=pool.submit(()->{try{v.start(body());throw new AssertionError("late start accepted");}catch(DirectAi.ApiError expected){}catch(Exception e){throw new RuntimeException(e);}});
        check(w.entered.await(2,TimeUnit.SECONDS),"creation in flight");v.cancel();w.release.countDown();pending.get(3,TimeUnit.SECONDS);check(w.hangups==2&&st.get("voice_pending").isEmpty(),"late cancelled creation closed");pool.shutdown();w.entered=null;
        v.start(body());w.cleanupFailed=true;v.stop();check(!st.get("voice_pending").isEmpty(),"failed cleanup retained");fails(()->v.start(body()),"new session blocked until cleanup");
        w.cleanupFailed=false;v.recover();check(st.get("voice_pending").isEmpty(),"cleanup retry succeeds");v.start(body());v.stop();fails(()->v.start(body()),"daily quota enforced");check(w.creates==6,"only six attempts");
        check(st.values.keySet().stream().noneMatch(k->k.contains("audio")||k.contains("transcript")||k.contains("sdp")),"no media or captions persisted");
        System.out.println("PASS "+checks+" voice fixture assertions; no live API calls.");
    }
}
