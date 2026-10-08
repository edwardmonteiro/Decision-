package com.edward.flights;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.*;
import static com.edward.flights.FlightContracts.*;

/** Offline protocol fixtures. These tests never contact OpenAI or claim live fares. */
public final class FlightAgentTest {
    static int checks;
    static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    interface Action {void run() throws Exception;}
    static void rejects(Action a,String why)throws Exception{boolean failed=false;try{a.run();}catch(Exception e){failed=true;}check(failed,why);}
    static class Memory implements FlightAgent.Store {
        final Map<String,String> values=new HashMap<>();
        public String get(String k){return values.getOrDefault(k,"");}
        public void put(String k,String v){values.put(k,v);}
    }
    static class Fake implements FlightAgent.Transport {
        int creates,submissions,ranks,deletes,cancels,approvalReplies;
        boolean failCreate,failTurns,failSend,acceptFailedSend,failRank,failProbe;
        String sessionId="ses_fixture",turnId="",turnStatus="running",finalText="",searchId="",choice="f1";
        double confidence=.94;
        JSONArray actions=new JSONArray();
        final List<String> keys=new ArrayList<>(),inputs=new ArrayList<>();
        JSONObject lastCreate,lastReply;
        @Override public JSONObject call(String path,String method,JSONObject body,String key)throws Exception {
            if(path.equals("/vaults")&&method.equals("POST"))return obj("id","vault_fixture");
            if(path.startsWith("/vaults/"))return obj("id","vault_fixture");
            if(path.equals("/agents/sessions")&&method.equals("POST")){
                creates++;lastCreate=body;searchId=body.getJSONObject("metadata").getString("client_search_id");
                if(failCreate){failCreate=false;throw new SocketTimeoutException();}
                return obj("id",sessionId,"environment",obj("id","env_fixture"));
            }
            if(path.startsWith("/agents/sessions?"))return obj("data",new JSONArray().put(obj("id",sessionId,"metadata",obj("client_search_id",searchId),"environment",obj("id","env_fixture"))));
            if(path.startsWith("/agents/environments/"))return obj("id","env_fixture","status","connected");
            if(path.endsWith("/events")){
                JSONObject event=body.getJSONArray("events").getJSONObject(0);String type=event.getString("type");
                if(type.equals("agent.session.input.message")){
                    submissions++;keys.add(key);inputs.add(body.toString());
                    if(!failSend||acceptFailedSend){turnId="turn_"+submissions;turnStatus="running";}
                    if(failSend){failSend=false;throw new SocketTimeoutException();}
                }else if(type.equals("agent.session.input.cancel"))cancels++;
                else {approvalReplies++;lastReply=event;actions=new JSONArray();}
                return new JSONObject();
            }
            if(path.contains("/turns?")){
                if(failTurns){failTurns=false;throw new SocketTimeoutException();}
                return obj("data",turnId.isEmpty()?new JSONArray():new JSONArray().put(obj("id",turnId,"subagent_id",JSONObject.NULL,"status",turnStatus)));
            }
            if(path.contains("/items?")){
                JSONArray data=new JSONArray().put(obj("id","item_screen","type","computer_use_call","turn_id",turnId,"title","Conferindo as datas","status","completed","output",obj("type","computer_screenshot","image_url","data:image/jpeg;base64,fixture")));
                if(!finalText.isEmpty())data.put(obj("id","item_final","type","message","turn_id",turnId,"role","assistant","phase","final_answer","content",new JSONArray().put(obj("type","output_text","text",finalText))));
                return obj("data",data,"has_more",false);
            }
            if(path.startsWith("/agents/sessions/")){
                if(method.equals("DELETE")){deletes++;return new JSONObject();}
                return obj("id",sessionId,"status",turnStatus.equals("running")?"in_progress":"idle","environment",obj("id","env_fixture"),"required_actions",actions);
            }
            if(path.equals("/decisions")){
                String name=body.getJSONArray("questions").getJSONObject(0).getString("name");
                if(name.equals("intent")&&failProbe)throw new FlightAgent.ApiError(403,"fixture access");
                if(name.equals("best_fit")){ranks++;if(failRank)throw new FlightAgent.ApiError(429,"fixture limit");}
                return obj("answers",new JSONArray().put(obj("type","choice","name",name,"choice",name.equals("intent")?"flights":choice,"confidence",confidence)),"_request_id","req_fixture");
            }
            throw new AssertionError("Unexpected request: "+method+" "+path);
        }
    }
    static JSONObject itinerary()throws Exception{return obj("from","São Paulo","to","Lisboa","departure",LocalDate.now().plusDays(33).toString(),"returning",LocalDate.now().plusDays(43).toString(),"passengers",1,"cabin","economy","preferences","Sem escalas");}
    static JSONObject offer(String id)throws Exception{return obj("id",id,"airline","Companhia de teste","price",4321,"currency","BRL","price_scope","trip_per_person","duration_minutes",580,"stops",0,"departure","GRU 22:00","arrival","LIS 11:40 +1","baggage","Não informado","source_url","https://www.google.com/travel/flights/search?tfs=fixture","details","Dados fictícios usados apenas no teste de contrato.");}
    static JSONObject output()throws Exception{return obj("status","ok","summary","Resultado fictício de teste.","route","São Paulo → Lisboa","offers",new JSONArray().put(offer("f1")));}
    static FlightAgent started(Fake f,Memory m)throws Exception{FlightAgent a=new FlightAgent(f,m);a.begin(itinerary());return a;}
    static void contracts()throws Exception {
        JSONObject body=FlightAgent.createBody("vault_fixture","search_fixture");
        check(body.getJSONObject("environment").getString("type").equals("openai_hosted"),"Cloud environment");
        check(body.getJSONObject("environment").getJSONObject("desktop").getBoolean("enabled"),"Hosted browser enabled");
        check(body.getJSONArray("vault_ids").getString(0).equals("vault_fixture"),"Vault attached");
        check(body.getJSONObject("agent").getJSONArray("tools").getJSONObject(0).getString("type").equals("computer_use"),"Computer-use tool");
        check(body.getJSONObject("agent").getJSONObject("text").getJSONObject("format").length()==2,"Agents schema format, not Responses format");
        check(validateOutput(output()).getJSONArray("offers").length()==1,"Valid observed offer shape");
        JSONObject bad=output();bad.getJSONArray("offers").getJSONObject(0).put("source_url","https://www.google.com.evil.example/travel/flights");rejects(()->validateOutput(bad),"Reject impersonated source");
        JSONObject overflow=output();overflow.getJSONArray("offers").getJSONObject(0).put("duration_minutes",4294967876L);rejects(()->validateOutput(overflow),"Reject numeric overflow");
        JSONObject duplicate=output();duplicate.getJSONArray("offers").put(offer("f1"));rejects(()->validateOutput(duplicate),"Reject duplicate offer ID");
        JSONObject blocked=output();blocked.put("status","blocked");rejects(()->validateOutput(blocked),"Blocked result cannot carry offers");
        JSONObject negative=output();negative.getJSONArray("offers").getJSONObject(0).put("price",-1);rejects(()->validateOutput(negative),"Reject negative price");
        check(!googleUrl("https://attacker@www.google.com/travel/flights"),"No URL credentials");
        check(!publicOrigin("https://accounts.google.com"),"No sign-in origin");
        check(!publicOrigin("https://www.google.com/path"),"Approval must contain an origin");
        JSONObject past=itinerary();past.put("departure",LocalDate.now().minusDays(1).toString());rejects(()->trip(past),"Reject past date");
    }
    static void completionAndRefinement()throws Exception {
        Fake f=new Fake();Memory m=new Memory();FlightAgent a=started(f,m);
        f.finalText=output().toString();JSONObject active=a.poll();
        check(active.getBoolean("running")&&active.getJSONArray("offers").length()==0&&f.ranks==0,"Provisional text cannot become fares");
        check(active.getString("screenshot").startsWith("data:image/jpeg;base64,"),"Latest cloud screenshot observed");
        f.turnStatus="completed";JSONObject done=a.poll();
        check(!done.getBoolean("running")&&done.getString("phase").equals("complete"),"Root completion required");
        check(done.getJSONArray("offers").length()==1&&f.ranks==1,"Compare verified offers once");
        check(done.getJSONObject("ranking").getString("offer_id").equals("f1"),"High confidence selection");
        a.poll();check(f.ranks==1,"Do not charge Decisions twice on completed polling");
        a.begin(obj("followup",true,"text","Tente Campinas"));check(f.creates==1&&f.submissions==2,"Refine in same session");
        check(a.snapshot().getString("baseline_turn").equals("turn_1"),"Old completed turn cannot finish a refinement");
        a.cancel();check(f.cancels==1&&!a.snapshot().getBoolean("running"),"Cancel remote task");
        a.close();check(f.deletes==1&&!a.snapshot().has("session_id"),"Delete session and reset local state");
    }
    static void incompleteAndUnavailable()throws Exception {
        Fake probe=new Fake();FlightAgent tested=new FlightAgent(probe,new Memory());tested.test();probe.failProbe=true;
        rejects(()->tested.test(),"Simulated failed retest");check(tested.snapshot().getString("decisions_status").contains("falhou"),"Failed retest clears stale availability");
        Fake malformed=new Fake();FlightAgent a=started(malformed,new Memory());malformed.turnStatus="completed";malformed.finalText="not JSON";
        JSONObject bad=a.poll();check(!bad.getBoolean("running")&&bad.getString("phase").equals("error")&&malformed.ranks==0,"Malformed answer is terminal and unranked");
        Fake f=new Fake();FlightAgent b=started(f,new Memory());f.turnStatus="completed";f.finalText=obj("status","blocked","summary","CAPTCHA observado.","route","São Paulo → Lisboa","offers",new JSONArray()).toString();
        check(b.poll().getJSONArray("offers").length()==0&&f.ranks==0,"Blocked pages produce no fabricated fares");
        Fake limited=new Fake();FlightAgent c=started(limited,new Memory());limited.turnStatus="completed";limited.finalText=output().toString();limited.failRank=true;
        JSONObject preserved=c.poll();check(preserved.getJSONArray("offers").length()==1&&preserved.getJSONObject("ranking").getString("status").equals("unavailable"),"Rate limit preserves observed fares");
        Fake uncertain=new Fake();FlightAgent d=started(uncertain,new Memory());uncertain.turnStatus="completed";uncertain.finalText=output().toString();uncertain.confidence=.40;
        check(!d.poll().getJSONObject("ranking").has("offer_id"),"Do not label a low-confidence decision as best");
    }
    static void recovery()throws Exception {
        Fake dropped=new Fake();dropped.failSend=true;Memory m=new Memory();FlightAgent a=new FlightAgent(dropped,m);
        rejects(()->a.begin(itinerary()),"Simulated lost message acknowledgment");
        check(a.snapshot().has("pending_input"),"Retain exact pending input");a.retryMessage();
        check(dropped.submissions==2&&dropped.keys.get(0).equals(dropped.keys.get(1))&&dropped.inputs.get(0).equals(dropped.inputs.get(1)),"Retry exact task with same idempotency key");
        Fake accepted=new Fake();accepted.failSend=true;accepted.acceptFailedSend=true;FlightAgent b=new FlightAgent(accepted,new Memory());rejects(()->b.begin(itinerary()),"Simulated accepted request timeout");b.retryMessage();
        check(accepted.submissions==1&&!b.snapshot().has("pending_input"),"Query the session before retry; no duplicate accepted task");
        Fake creation=new Fake();creation.failCreate=true;Memory createStore=new Memory();FlightAgent c=new FlightAgent(creation,createStore);rejects(()->c.begin(itinerary()),"Simulated ambiguous creation");
        c.begin(itinerary());check(creation.creates==1&&c.snapshot().has("session_id"),"Recover created session by metadata instead of creating again");
        Fake baseline=new Fake();baseline.failTurns=true;Memory baselineStore=new Memory();FlightAgent d=new FlightAgent(baseline,baselineStore);rejects(()->d.begin(itinerary()),"Simulated failure after creation");
        check(new FlightAgent(baseline,baselineStore).snapshot().has("session_id"),"Persist created ID before dependent reads");
    }
    static void permissionsAndDeadline()throws Exception {
        Fake f=new Fake();Memory m=new Memory();FlightAgent a=started(f,m);
        f.actions=new JSONArray().put(obj("type","computer_use_approval_request","request_id","req_origin","request",obj("type","browser_origin_access","origin","https://www.google.com","reason","Pesquisar")));
        check(a.poll().getJSONArray("approvals").getJSONObject(0).getBoolean("allowed"),"Show public origin approval");
        a.approve(obj("request_id","req_origin","decision","approve"));check(f.approvalReplies==1&&f.lastReply.getJSONObject("response").getString("decision").equals("approve"),"Explicit typed approval response");
        rejects(()->a.approve(obj("request_id","req_origin","decision","approve")),"Reject stale approval");
        f.actions=new JSONArray().put(obj("type","computer_use_approval_request","request_id","req_other","request",obj("type","browser_origin_access","origin","https://evil.example")));
        rejects(()->a.approve(obj("request_id","req_other","decision","approve")),"Reject off-scope origin");check(f.approvalReplies==1,"Do not send forbidden approval");
        f.actions=new JSONArray().put(obj("type","computer_use_approval_request","request_id","req_auth","request",obj("type","browser_authentication")));
        a.poll();check(f.lastReply.getJSONObject("response").getString("action").equals("cancel"),"Cancel authentication requests");
        JSONObject old=new JSONObject(m.get("state"));old.put("started_at",System.currentTimeMillis()-250000);m.put("state",old.toString());FlightAgent restored=new FlightAgent(f,m);
        check(restored.enforceDeadline()&&f.cancels==1&&!restored.snapshot().getBoolean("running"),"Cancel expired task even with no fresh turn");
    }
    public static void main(String[]args)throws Exception {
        contracts();completionAndRefinement();incompleteAndUnavailable();recovery();permissionsAndDeadline();
        System.out.println("PASS: "+checks+" contract and lifecycle checks (offline fixtures).");
    }
}
