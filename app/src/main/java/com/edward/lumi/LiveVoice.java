package com.edward.lumi;

import org.json.JSONObject;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Owns native authentication, fixed configuration, quotas and late-start cleanup. */
final class LiveVoice {
    private final DirectAi.Transport transport;
    private final DirectAi.Store store;
    private final JSONObject config;
    private int generation;
    private boolean starting;
    private String active="";
    LiveVoice(DirectAi.Transport transport, DirectAi.Store store, String config)throws Exception{
        this.transport=transport;this.store=store;this.config=new JSONObject(config);
    }
    JSONObject start(JSONObject body)throws Exception{
        if(body.length()!=2||!body.has("sdp")||!body.has("challenge"))throw new DirectAi.ApiError(400,"Pedido de voz inválido");
        String sdp=body.getString("sdp");
        if(sdp.length()>65000||!sdp.startsWith("v=0")||!sdp.contains("m=audio")||!sdp.contains("m=application"))throw new DirectAi.ApiError(400,"Conexão de áudio inválida");
        JSONObject river=DirectAi.validateMission(body.getJSONObject("challenge"));river.remove("source");
        final int token;
        synchronized(this){
            if(starting||!active.isEmpty())throw new DirectAi.ApiError(409,"Já existe uma conversa de voz");
            starting=true;token=generation;
        }
        try{
            recover();
            synchronized(this){
                if(token!=generation)throw new DirectAi.ApiError(409,"Conversa cancelada");
                if(!store.get("voice_pending").isEmpty())throw new DirectAi.ApiError(503,"Ainda encerrando a conversa anterior. Tente novamente.");
                String day=LocalDate.now(ZoneOffset.UTC).toString();
                if(!day.equals(store.get("voice_day"))){store.put("voice_day",day);store.put("voice_count","0");}
                int n;try{n=Integer.parseInt(store.get("voice_count"));}catch(Exception e){n=0;}
                if(n>=6)throw new DirectAi.ApiError(429,"As 6 conversas de voz de hoje já foram usadas. Amanhã tem mais.");
                store.put("voice_count",Integer.toString(n+1));
            }
            JSONObject session=new JSONObject(config.toString());
            session.put("instructions",session.getString("instructions")+" Rio inicial: "+river.toString());
            JSONObject request=new JSONObject().put("session",session).put("transport",new JSONObject().put("type","webrtc").put("sdp",sdp));
            JSONObject r=transport.call("/live/sessions","POST",request);
            String id=r.getJSONObject("session").getString("id");
            if(!id.matches("[A-Za-z0-9_-]{1,200}"))throw new DirectAi.ApiError(502,"Identificador da sessão de voz inválido");
            synchronized(this){active=id;store.put("voice_pending",id);}
            try{
                String answer=r.getJSONObject("transport").getString("sdp");
                if(!answer.startsWith("v=0")||answer.length()>100000)throw new DirectAi.ApiError(502,"Resposta de áudio inválida");
                synchronized(this){
                    if(token!=generation)throw new DirectAi.ApiError(409,"Conversa cancelada");
                    return new JSONObject().put("session_id",id).put("sdp",answer).put("model","gpt-live-1");
                }
            }catch(Exception e){stop();throw e;}
        }finally{synchronized(this){starting=false;}}
    }
    void cancel(){synchronized(this){generation++;}}
    void stop(){cancel();recover();}
    synchronized void confirmed(String id){
        if(id.equals(active)){active="";store.remove("voice_pending");}
    }
    void recover(){
        String id; synchronized(this){id=store.get("voice_pending");}
        if(id.isEmpty()||!id.matches("[A-Za-z0-9_-]{1,200}"))return;
        try{
            transport.call("/live/sessions/"+id+"/hangup","POST",null);
            synchronized(this){if(id.equals(store.get("voice_pending")))store.remove("voice_pending");if(id.equals(active))active="";}
        }catch(DirectAi.ApiError e){if(e.status==404||e.status==410)synchronized(this){if(id.equals(store.get("voice_pending")))store.remove("voice_pending");if(id.equals(active))active="";}}
        catch(Exception ignored){}
    }
}
