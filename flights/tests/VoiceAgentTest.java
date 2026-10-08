package com.edward.flights;

import org.json.JSONArray;
import org.json.JSONObject;
import static com.edward.flights.FlightContracts.*;
import static com.edward.flights.FlightAgentTest.*;

/** Documented Live WebRTC wire contract and action boundaries; no live account access. */
public final class VoiceAgentTest {
    static class Live implements FlightAgent.Transport {
        int creates,hangups;boolean failHangup,malformed;JSONObject last;
        public JSONObject call(String path,String method,JSONObject body,String key)throws Exception{
            check(method.equals("POST")&&key==null,"Live uses native POST without automatic retries");
            if(path.equals("/live/sessions")){creates++;last=body;return obj("session",obj("id","live_"+creates),"transport",obj("type","webrtc","sdp",malformed?"bad":"v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"));}
            check(path.matches("/live/sessions/live_[0-9]+/hangup"),"Only Live hangup endpoint is used");hangups++;
            if(failHangup)throw new FlightAgent.ApiError(500,"fixture unavailable");return new JSONObject();
        }
    }
    static JSONObject request()throws Exception{return obj("sdp","v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n","voice","bossa","trip",FlightAgentTest.itinerary());}
    static JSONObject action(String session,String call,String name,JSONObject args)throws Exception{return obj("session_id",session,"call_id",call,"name",name,"arguments",args);}
    public static void main(String[] args)throws Exception{
        int start=checks;Live transport=new Live();Memory memory=new Memory();VoiceAgent voice=new VoiceAgent(transport,memory);
        JSONObject response=voice.start(request(),new JSONObject()),session=transport.last.getJSONObject("session");
        check(session.getString("model").equals("gpt-live-1")&&session.getJSONObject("audio").getJSONObject("output").getString("voice").equals("bossa"),"Exact Live model and Brazilian voice");
        check(!session.getJSONObject("audio").has("format")&&!session.getBoolean("store"),"WebRTC negotiates media and storage is disabled");
        check(session.getJSONObject("delegation").getJSONObject("responses").getString("model").equals("gpt-6-luna"),"OpenAI owns Responses delegation");
        JSONArray tools=session.getJSONObject("delegation").getJSONObject("responses").getJSONArray("tools");
        check(tools.length()==4&&!session.getJSONObject("delegation").getJSONObject("responses").getBoolean("parallel_tool_calls"),"Only sequential flight tools registered");
        for(int i=0;i<tools.length();i++){JSONObject t=tools.getJSONObject(i),schema=t.getJSONObject("parameters");check(t.getBoolean("strict")&&!schema.getBoolean("additionalProperties")&&schema.getJSONArray("required").length()==schema.getJSONObject("properties").length(),"Strict function schemas");}
        check(response.getJSONObject("session").getString("id").equals(memory.get("live_id")),"Created session persisted before handoff");
        voice.closeExpected(obj("session_id","old_live"));check(voice.sessionId().equals("live_1")&&transport.hangups==0,"Stale handshake cleanup cannot end current conversation");
        voice.closeExpected(obj("start_token","old_start"));check(voice.sessionId().equals("live_1")&&transport.hangups==0,"Stale startup token cannot end newer connection");
        check(!voice.expired(),"Fresh session within budget");memory.put("live_started_at",String.valueOf(System.currentTimeMillis()-240001));check(voice.expired(),"Native deadline catches expired voice session");
        check(VoiceAgent.createBody(request().put("voice","tempo"),new JSONObject()).getJSONObject("session").getJSONObject("audio").getJSONObject("output").getString("voice").equals("tempo"),"Brazilian masculine voice supported");
        rejects(()->VoiceAgent.createBody(request().put("voice","unknown"),new JSONObject()),"Unknown voice rejected before billing");
        rejects(()->VoiceAgent.createBody(request().put("sdp","invalid"),new JSONObject()),"Malformed SDP rejected before billing");
        Fake flightsTransport=new Fake();FlightAgent flights=new FlightAgent(flightsTransport,new Memory());
        JSONObject search=action("live_1","call_1","search_flights",FlightAgentTest.itinerary());voice.action(flights,search);voice.action(flights,search);
        check(flightsTransport.creates==1&&flightsTransport.submissions==1,"Repeated Live call does not launch duplicate flight task");
        JSONObject status=voice.action(flights,action("live_1","call_2","get_search_status",new JSONObject()));
        check(status.getJSONObject("result").getBoolean("running")&&!status.getJSONObject("result").has("vault_id")&&status.getJSONObject("result").getJSONArray("offers").length()==0,"Spoken status exposes progress without private IDs or unverified fares");
        rejects(()->voice.action(flights,action("other_live","call_bad","cancel_search",new JSONObject())),"Stale session cannot act");
        rejects(()->voice.action(flights,action("live_1","call_bad","buy_ticket",new JSONObject())),"Unknown function cannot execute");
        rejects(()->voice.action(flights,action("live_1","call_bad","search_flights",FlightAgentTest.itinerary().put("url","https://evil.test"))),"Unexpected arguments rejected");
        voice.action(flights,action("live_1","call_3","cancel_search",new JSONObject()));check(flightsTransport.cancels==1,"Voice can cancel flight search");
        voice.finished(obj("session_id","unrelated","seconds",3));check(voice.sessionId().equals("live_1"),"Stale close event does not clear newer session");
        voice.finished(obj("session_id","live_1","seconds",12.5));check(voice.sessionId().isEmpty()&&voice.snapshot().getString("seconds").equals("12.5"),"Terminal usage recorded once");
        voice.start(request(),new JSONObject());transport.failHangup=true;rejects(voice::close,"Failed hangup reported");check(memory.get("live_id").equals("live_2"),"Failed hangup keeps recoverable ID");
        transport.failHangup=false;VoiceAgent recovered=new VoiceAgent(transport,memory);recovered.start(request(),new JSONObject());check(transport.hangups==2&&recovered.sessionId().equals("live_3"),"Recovery closes previous connection before new session");
        recovered.close();check(memory.get("live_id").isEmpty(),"Hangup clears ID");
        transport.malformed=true;rejects(()->recovered.start(request(),new JSONObject()),"Malformed SDP answer rejected");check(!memory.get("live_id").isEmpty(),"Malformed handoff keeps ID for cleanup");recovered.close();
        System.out.println("PASS: "+(checks-start)+" offline GPT Live protocol and action checks (no live audio).");
    }
}
