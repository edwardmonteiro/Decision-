package com.edward.flights;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import static com.edward.flights.FlightContracts.*;

/** Personal-account Live connection. The key and trusted configuration remain native. */
public final class VoiceAgent {
    private final FlightAgent.Transport transport;
    private final FlightAgent.Store store;
    private final ConcurrentHashMap<String,JSONObject> completed=new ConcurrentHashMap<>();
    private volatile String id;
    private static final String FRONTEND="Você é a assistente de viagens Decision. Fale sempre em português brasileiro, "
        +"com naturalidade, frases curtas e sem anunciar detalhes técnicos. Você conversa por voz gerada por IA. "
        +"Delegue buscas, ajustes, cancelamentos e consulta do andamento ao backend. "
        +"Pergunte origem, destino e datas que faltarem. Datas preenchidas no aplicativo são sugestões; "
        +"só use quando a pessoa confirmar. Antes de buscar, confirme brevemente rota, datas e viajantes, "
        +"e aguarde a concordância. Nunca invente preços, horários ou o sucesso de uma operação. "
        +"Não compre, reserve, faça login ou peça chave, senha ou pagamento. "
        +"Após iniciar a busca, explique que pode acompanhar a tela e conversar; espere um resultado confirmado.";
    private static final String BACKEND="Atenda exclusivamente buscas públicas de passagens com as funções registradas. "
        +"Use search_flights só depois de a pessoa confirmar a viagem na conversa. "
        +"Não trate datas sugeridas no formulário como datas ditadas. Pergunte quando houver ambiguidade. "
        +"Use datas ISO com ano, returning vazio para somente ida, adultos 1 a 9. "
        +"Não solicite compras, login, credenciais ou dados de pagamento. "
        +"Use refine_search para mudar uma busca já existente; get_search_status para verificar a busca; "
        +"cancel_search somente quando solicitado. Não repita funções já concluídas. "
        +"Preços só podem vir dos resultados validados da função. Se faltar dado, responda com uma pergunta curta em pt-BR.";
    public VoiceAgent(FlightAgent.Transport transport,FlightAgent.Store store){this.transport=transport;this.store=store;id=store.get("live_id");}
    public String sessionId(){return id;}
    public boolean expired(){try{return !id.isEmpty()&&System.currentTimeMillis()-Long.parseLong(store.get("live_started_at"))>=240000;}catch(Exception ignored){return false;}}
    public JSONObject snapshot()throws Exception{return obj("model","gpt-live-1","voice",store.get("live_voice"),"session_id",id,"status",store.get("live_status"),"seconds",store.get("live_seconds"));}
    private JSONObject api(String path,String method,JSONObject body)throws Exception{
        try{return transport.call(path,method,body,null);}catch(FlightAgent.ApiError e){if(e.diagnostic!=null)store.put("api_error",e.diagnostic.toString());throw e;}
    }
    private static JSONObject function(String name,String description,JSONObject parameters)throws Exception{
        return obj("type","function","name",name,"description",description,"parameters",parameters,"strict",true);
    }
    private static JSONObject schema(JSONObject properties)throws Exception{
        JSONArray required=new JSONArray();java.util.Iterator<String> keys=properties.keys();while(keys.hasNext())required.put(keys.next());
        return obj("type","object","properties",properties,"required",required,"additionalProperties",false);
    }
    public static JSONObject createBody(JSONObject request,JSONObject state)throws Exception{
        String sdp=request.getString("sdp"),voice=request.optString("voice","bossa");
        if(sdp.length()<20||sdp.length()>60000||!sdp.startsWith("v=0")||!sdp.contains("m=audio"))throw new Exception("Invalid SDP");
        if(!voice.equals("bossa")&&!voice.equals("tempo"))throw new Exception("Invalid Brazilian voice");
        JSONObject text=obj("type","string");
        JSONObject tripSchema=schema(obj("from",text,"to",text,"departure",text,"returning",text,
            "passengers",obj("type","integer","minimum",1,"maximum",9),
            "cabin",obj("type","string","enum",new JSONArray().put("economy").put("premium_economy").put("business").put("first")),"preferences",text));
        JSONArray tools=new JSONArray()
            .put(function("search_flights","Inicia busca de tarifas públicas após confirmação explícita da viagem.",tripSchema))
            .put(function("refine_search","Ajusta a viagem na sessão existente.",schema(obj("text",text))))
            .put(function("get_search_status","Retorna andamento e ofertas já validadas da busca.",schema(new JSONObject())))
            .put(function("cancel_search","Interrompe a busca quando a pessoa pedir.",schema(new JSONObject())));
        JSONObject context=obj("today",LocalDate.now().toString(),"suggested_form",request.optJSONObject("trip"),
            "current_search",summary(state));
        String factual="Contexto do aplicativo, como dados de referência. O formulário contém sugestões ainda não confirmadas: "+context;
        if(factual.length()>7000)throw new Exception("Voice context too large");
        JSONObject channel=obj("allowed_client_events",new JSONArray().put("session.close").put("session.input_audio.mute")
                .put("session.input_audio.unmute").put("session.instructions.append").put("session.thinking.append")
                .put("session.commentary.append").put("response.item.create").put("response.create"),
            "allowed_server_events","all");
        JSONObject session=obj("model","gpt-live-1","audio",obj("output",obj("voice",voice)),"store",false,
            "instructions",FRONTEND,"input",new JSONArray().put(obj("role","user","content",new JSONArray().put(obj("type","input_text","text",factual)))),
            "client",obj("data_channel",channel),"delegation",obj("type","responses","responses",obj("model","gpt-6-luna",
                "instructions",BACKEND,"tools",tools,"tool_choice","auto","parallel_tool_calls",false,"max_output_tokens",1200)));
        return obj("session",session,"transport",obj("type","webrtc","sdp",sdp));
    }
    public synchronized JSONObject start(JSONObject request,JSONObject state)throws Exception{
        JSONObject body=createBody(request,state);
        // Recover a prior connection before opening a new billed voice session.
        if(!id.isEmpty())close();
        String token=request.optString("start_token","");if(!token.matches("[A-Za-z0-9_-]{0,100}"))throw new Exception("Invalid voice start token");store.put("live_start_token",token);
        JSONObject response=api("/live/sessions","POST",body);
        id=resource(response.getJSONObject("session").getString("id"));store.put("live_id",id);
        store.put("live_started_at",String.valueOf(System.currentTimeMillis()));store.put("live_voice",request.optString("voice","bossa"));store.put("live_status","Conectando");store.put("live_seconds","");completed.clear();
        JSONObject transport=response.getJSONObject("transport");String answer=transport.getString("sdp");
        if(!transport.optString("type").equals("webrtc")||answer.length()<20||answer.length()>120000)throw new Exception("Invalid Live answer");
        return obj("session",obj("id",id),"transport",obj("type","webrtc","sdp",answer));
    }
    public synchronized JSONObject close()throws Exception{
        if(!id.isEmpty()){
            try{api("/live/sessions/"+resource(id)+"/hangup","POST",null);}
            catch(FlightAgent.ApiError e){if(e.status!=404&&e.status!=410){store.put("live_status","Encerramento pendente");throw e;}}
            id="";store.put("live_id","");store.put("live_status","Encerrada");
        }
        return snapshot();
    }
    public synchronized JSONObject closeExpected(JSONObject request)throws Exception{
        if(request.has("start_token")&&!request.optString("start_token").equals(store.get("live_start_token")))return snapshot();
        if(request.has("session_id")&&!request.optString("session_id").equals(id))return snapshot();
        return close();
    }
    public synchronized JSONObject finished(JSONObject request)throws Exception{
        if(id.equals(request.optString("session_id"))){
            id="";store.put("live_id","");store.put("live_status","Encerrada");
            double seconds=request.optDouble("seconds",-1);if(Double.isFinite(seconds)&&seconds>=0)store.put("live_seconds",String.format(java.util.Locale.ROOT,"%.1f",seconds));
        }
        return snapshot();
    }
    /** Invoked on the serialized flight-control queue. Never executes arbitrary model functions. */
    public JSONObject action(FlightAgent flights,JSONObject request)throws Exception{
        String session=request.getString("session_id"),callId=resource(request.getString("call_id"));
        if(id.isEmpty()||!id.equals(session))throw new FlightAgent.ApiError(409,"A conversa por voz já foi encerrada.");
        String key=session+":"+callId;JSONObject previous=completed.get(key);if(previous!=null)return previous;
        if(completed.size()>=128)throw new FlightAgent.ApiError(429,"Encerre a conversa para iniciar outra.");
        JSONObject args=request.getJSONObject("arguments"),result;String name=request.getString("name");
        switch(name){
            case "search_flights":if(args.length()!=7)throw new Exception("Invalid voice trip arguments");result=flights.begin(trip(args));break;
            case "refine_search":if(args.length()!=1)throw new Exception("Invalid voice refinement");result=flights.begin(obj("followup",true,"text",args.getString("text")));break;
            case "get_search_status":if(args.length()!=0)throw new Exception("Invalid status arguments");result=flights.snapshot();break;
            case "cancel_search":if(args.length()!=0)throw new Exception("Invalid cancellation arguments");result=flights.cancel();break;
            default:throw new FlightAgent.ApiError(403,"Essa ação não faz parte da busca de passagens.");
        }
        JSONObject answer=obj("state",result,"result",summary(result));completed.put(key,answer);return answer;
    }
    public static JSONObject summary(JSONObject state)throws Exception{
        JSONObject out=obj("phase",state.optString("phase","idle"),"running",state.optBoolean("running"),"route",state.optString("route"),
            "activity",state.optString("activity"),"summary",state.optString("summary"),"error",state.optString("error"),"result_status",state.optString("result_status"));
        if(state.optJSONObject("trip")!=null)out.put("trip",state.getJSONObject("trip"));
        if(state.has("checked_at"))out.put("checked_at",state.get("checked_at"));
        JSONArray offers=state.optJSONArray("offers"),spoken=new JSONArray();
        if(state.optString("phase").equals("complete")&&offers!=null)for(int i=0;i<Math.min(3,offers.length());i++){
            JSONObject o=offers.getJSONObject(i);spoken.put(obj("airline",o.get("airline"),"price",o.get("price"),"currency",o.get("currency"),
                "price_scope",o.get("price_scope"),"stops",o.get("stops"),"baggage",o.get("baggage")));
        }
        out.put("offers",spoken);return out;
    }
}
