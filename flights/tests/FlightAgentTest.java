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
        int creates,submissions,ranks,deletes,cancels,approvalReplies,watches;
        boolean failCreate,failTurns,failSend,acceptFailedSend,failRank,failProbe;
        String sessionId="ses_fixture",turnId="",turnStatus="running",finalText="",searchId="",choice="f1";
        double confidence=.94;
        JSONArray actions=new JSONArray();
        JSONArray foreignItems=new JSONArray();
        JSONArray[] itemPages;
        String environmentStatus="connected";
        boolean noItems;
        Exception itemFailure;
        Exception sendFailure;
        java.util.concurrent.CountDownLatch pollEntered,pollRelease;
        final List<String> keys=new ArrayList<>(),inputs=new ArrayList<>();
        JSONObject lastCreate,lastReply;
        @Override public void watch(String id){check(id.equals(sessionId),"Subscribe to the saved session");watches++;}
        @Override public JSONObject call(String path,String method,JSONObject body,String key)throws Exception {
            apiPath(path);
            if(path.equals("/vaults")&&method.equals("POST"))return obj("id","vault_fixture");
            if(path.startsWith("/vaults/"))return obj("id","vault_fixture");
            if(path.equals("/agents/sessions")&&method.equals("POST")){
                creates++;lastCreate=body;searchId=body.getJSONObject("metadata").getString("client_search_id");
                if(failCreate){failCreate=false;throw new SocketTimeoutException();}
                return obj("id",sessionId,"environment",obj("id","env_fixture"));
            }
            if(path.startsWith("/agents/sessions?"))return obj("data",new JSONArray().put(obj("id",sessionId,"metadata",obj("client_search_id",searchId),"environment",obj("id","env_fixture"))));
            if(path.startsWith("/agents/environments/"))return obj("id","env_fixture","status",environmentStatus);
            if(path.endsWith("/events")){
                JSONObject event=body.getJSONArray("events").getJSONObject(0);String type=event.getString("type");
                if(type.equals("agent.session.input.message")){
                    check(watches==submissions+1,"Subscribe before submitting each task");
                    submissions++;keys.add(key);inputs.add(body.toString());
                    if(sendFailure!=null){Exception e=sendFailure;sendFailure=null;throw e;}
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
                check(path.startsWith("/agents/sessions/"+sessionId+"/turns/"+turnId+"/items?"),"Use documented per-turn items endpoint");
                if(itemFailure!=null){Exception e=itemFailure;itemFailure=null;throw e;}
                if(itemPages!=null){int page=path.contains("after=item_page_0")?1:0;return obj("data",itemPages[page],"has_more",page==0,"last_id","item_page_"+page);}
                if(noItems)return obj("data",new JSONArray(),"has_more",false);
                JSONArray data=new JSONArray().put(obj("id","item_screen","type","computer_use_call","turn_id",turnId,"title","Conferindo as datas","status","completed","output",obj("type","computer_screenshot","image_url","data:image/jpeg;base64,fixture")));
                if(!finalText.isEmpty())data.put(obj("id","item_final","type","message","turn_id",turnId,"role","assistant","phase","final_answer","content",new JSONArray().put(obj("type","output_text","text",finalText))));
                for(int i=0;i<foreignItems.length();i++)data.put(foreignItems.get(i));
                return obj("data",data,"has_more",false);
            }
            if(path.startsWith("/agents/sessions/")){
                if(method.equals("DELETE")){deletes++;return new JSONObject();}
                if(pollEntered!=null){pollEntered.countDown();if(!pollRelease.await(3,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("Blocked fixture not released");}
                return obj("id",sessionId,"status",turnStatus.equals("running")?"in_progress":"idle","environment",obj("id","env_fixture"),"required_actions",actions);
            }
            if(path.equals("/decisions")){
                JSONArray questions=body.getJSONArray("questions");
                for(int q=0;q<questions.length();q++){
                    JSONArray choices=questions.getJSONObject(q).getJSONArray("choices");
                    check(choices.length()>=2&&choices.length()<=255,"Decisions choice count");
                    Set<String> unique=new HashSet<>();
                    for(int i=0;i<choices.length();i++){
                        JSONObject option=choices.getJSONObject(i);
                        check(option.length()==2&&!option.has("label")&&option.get("description") instanceof String&&option.get("value") instanceof String,"Choice uses value + description, never label");
                        check(unique.add(option.getString("value")),"Choice values are unique");
                    }
                }
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
        Fake rejected=new Fake();rejected.sendFailure=new FlightAgent.ApiError(400,"A OpenAI rejeitou o envio (400).");FlightAgent invalid=new FlightAgent(rejected,new Memory());
        rejects(()->invalid.begin(itinerary()),"Definitive input rejection is propagated");
        check(!invalid.snapshot().getBoolean("running")&&invalid.snapshot().getString("phase").equals("error")&&invalid.snapshot().getString("error").contains("400"),"Rejected input cannot leave a fake running search");
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
    static void errorDiagnostics()throws Exception {
        String key="sk-proj-fixture-secret-123456789",raw=obj("error",obj("type","invalid_request_error","code","unknown_parameter","param","questions[0].choices[0].label","message","Unknown label "+key+" Bearer another-private-token")).toString();
        FlightAgent.ApiError e=OpenAiErrors.fromResponse(400,"/decisions",raw,"req_safe",key);
        check(e.getMessage().contains("Decisions")&&e.getMessage().contains("questions[0].choices[0].label"),"Rejected service and exact parameter visible");
        check(e.diagnostic.getString("request_id").equals("req_safe")&&e.diagnostic.getString("code").equals("unknown_parameter"),"Request ID and error code preserved");
        check(!e.diagnostic.toString().contains(key)&&!e.diagnostic.toString().contains("another-private-token"),"Diagnostics remove credentials");
        check(OpenAiErrors.fromResponse(400,"/live/sessions","<html>secret</html>",null,key).diagnostic.getString("detail").isEmpty(),"Non-JSON response not stored");
        check(OpenAiErrors.fromResponse(401,"/vaults",obj("error",obj("param",JSONObject.NULL,"code",JSONObject.NULL,"message",key)).toString(),"Bearer hidden",key).diagnostic.getString("param").isEmpty(),"Null and unsafe identifiers excluded");
        check(OpenAiErrors.shortStatus(400).contains("inválida")&&!OpenAiErrors.shortStatus(400).contains("saldo"),"400 is not an account balance error");
        java.io.ByteArrayInputStream input=new java.io.ByteArrayInputStream(new byte[20000]);
        check(OpenAiErrors.readBody(input,16384).length()==16384&&OpenAiErrors.readBody(null,16384).isEmpty(),"Bounded error body and empty stream");
        Memory store=new Memory();FlightAgent.Transport bad=(p,m,b,i)->{if(p.startsWith("/vaults"))return obj("id","vault_fixture");throw e;};
        FlightAgent agent=new FlightAgent(bad,store);rejects(agent::test,"Real structured API error propagated");
        check(agent.snapshot().getJSONObject("api_error").getString("param").endsWith("label")&&!agent.snapshot().getString("decisions_status").contains("saldo"),"Failed probe persists useful diagnostics");
        FlightAgent recovered=new FlightAgent(new Fake(),store);recovered.test();check(!recovered.snapshot().has("api_error"),"Successful probe clears prior diagnostics");
    }
    static JSONObject finalItem(String turn,String subagent,String result)throws Exception {
        return obj("id","item_other","type","message","turn_id",turn,"subagent_id",subagent==null?JSONObject.NULL:subagent,"role","assistant","phase","final_answer","content",new JSONArray().put(obj("type","output_text","text",result)));
    }
    static void progressAndFailures()throws Exception {
        apiPath("/agents/sessions/ses_fixture/turns/turn_1/items?order=desc&limit=20");
        rejects(()->apiPath("/agents/sessions/ses_fixture/items?order=desc&turn_id=turn_1"),"Reject unsupported turn_id query before sending HTTP");
        apiPath("/agents/sessions/ses_fixture/events?stream=true");
        Fake f=new Fake();Memory store=new Memory();FlightAgent a=started(f,store);f.noItems=true;f.environmentStatus="pending";
        JSONObject preparing=a.poll();check(preparing.getString("phase").equals("starting")&&preparing.getString("progress").contains("preparando"),"Pending hosted environment is visible as preparation");
        f.environmentStatus="connected";JSONObject connected=a.poll();
        check(connected.getString("phase").equals("browsing")&&connected.getString("progress").contains("conectado")&&connected.getString("screenshot").isEmpty(),"Connected browser may legitimately have no screenshot yet");
        f.turnId="";check(a.poll().getString("progress").contains("Aguardando"),"No root turn reports waiting rather than fabricated activity");f.turnId="turn_1";
        f.itemFailure=OpenAiErrors.fromResponse(400,"/agents/sessions/ses_fixture/turns/turn_1/items",obj("error",obj("param","fixture_field","message","Rejected query")).toString(),"req_poll","");
        rejects(a::poll,"Simulated permanent polling error");JSONObject rejected=a.snapshot();
        check(rejected.getBoolean("running")&&rejected.getBoolean("poll_blocked")&&rejected.getString("phase").equals("recovering"),"Stop automatic retries without falsely cancelling remote task");
        check(rejected.getString("poll_warning").contains("400")&&rejected.getJSONObject("api_error").getString("request_id").equals("req_poll"),"Persist polling error and request ID");
        check(new FlightAgent(f,store).snapshot().getBoolean("poll_blocked"),"Blocked tracking survives process restart");
        rejects(()->a.begin(itinerary()),"Unknown task outcome prevents duplicate search");
        JSONObject recovered=a.poll();check(!recovered.optBoolean("poll_blocked")&&!recovered.has("poll_warning")&&f.creates==1&&f.submissions==1,"Manual consultation recovers same session without replaying task");
        f.itemFailure=new SocketTimeoutException();rejects(a::poll,"Simulated transient item timeout");check(!a.snapshot().getBoolean("poll_blocked")&&a.snapshot().has("poll_warning"),"Transient failures remain recoverable and visible");
        check(a.snapshot().getLong("poll_retry_at")>System.currentTimeMillis(),"Transient failure backs off automatic consultation");a.poll();check(!a.snapshot().has("poll_retry_at"),"Successful consultation clears retry delay");
        a.streamFailure("Sem acompanhamento ao vivo.",null);check(a.snapshot().has("stream_warning")&&a.snapshot().getBoolean("running"),"Stream transport failure does not imply task failure");
        a.streamConnected();check(!a.snapshot().has("stream_warning"),"New stream clears transport warning");
        a.onEvent(obj("type","agent.session.turn.failed","turn",obj("id","turn_child","subagent_id","child","status","failed")));
        check(a.snapshot().getBoolean("running"),"Subagent failure does not end root search");
        a.onEvent(obj("type","error"));check(a.snapshot().getString("phase").equals("recovering")&&a.snapshot().has("poll_warning"),"Provider error event triggers visible session recovery");a.poll();
        f.environmentStatus="failed";Memory expiredCheck=new Memory();JSONObject cached=a.snapshot();cached.put("environment_checked_at",0);expiredCheck.put("state",cached.toString());
        JSONObject failed=new FlightAgent(f,expiredCheck).poll();check(!failed.getBoolean("running")&&failed.getString("phase").equals("error")&&failed.getString("error").contains("navegador"),"Environment failure cannot keep spinning");
        for(String type:new String[]{"agent.session.failed","agent.session.environment.failed","agent.session.turn.failed","agent.session.turn.cancelled"}){
            Fake remote=new Fake();FlightAgent agent=started(remote,new Memory());agent.onEvent(obj("type",type,"turn",obj("id","turn_1","subagent_id",JSONObject.NULL,"status","failed")));
            check(!agent.snapshot().getBoolean("running")&&!agent.snapshot().getString("error").isEmpty(),"Surface terminal lifecycle event "+type);
        }
        Fake expired=new Fake();FlightAgent exp=started(expired,new Memory());expired.environmentStatus="expired";check(exp.poll().getString("error").contains("expirou"),"Expired environment is terminal");
    }
    static void turnHistoryAndNonblockingSnapshot()throws Exception {
        Fake f=new Fake();FlightAgent a=started(f,new Memory());f.turnStatus="completed";f.finalText=output().toString();
        String unrelated=obj("status","blocked","summary","Outra tarefa","route","Rota antiga","offers",new JSONArray()).toString();
        f.foreignItems.put(finalItem("turn_old",null,unrelated)).put(finalItem("turn_1","child",unrelated));
        check(a.poll().getJSONArray("offers").length()==1,"Only current root final answer can become fares");
        Fake pages=new Fake();FlightAgent paged=started(pages,new Memory());pages.turnStatus="completed";
        pages.itemPages=new JSONArray[]{new JSONArray().put(finalItem("turn_old",null,unrelated)),new JSONArray().put(finalItem("turn_1",null,output().toString()))};
        check(paged.poll().getJSONArray("offers").length()==1,"Read paginated turn items before concluding no offers");
        Fake slow=new Fake();FlightAgent visible=started(slow,new Memory());slow.pollEntered=new java.util.concurrent.CountDownLatch(1);slow.pollRelease=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<JSONObject> poll=worker.submit(visible::poll);
        try{
            check(slow.pollEntered.await(1,java.util.concurrent.TimeUnit.SECONDS),"Polling fixture entered blocked network call");
            JSONObject snapshot=worker.submit(visible::snapshot).get(1,java.util.concurrent.TimeUnit.SECONDS);
            check(snapshot.getBoolean("running")&&visible.streamSession().equals("ses_fixture"),"UI and event subscription do not block behind network polling");
        }finally{slow.pollRelease.countDown();worker.shutdown();}
        poll.get(3,java.util.concurrent.TimeUnit.SECONDS);
    }
    public static void main(String[]args)throws Exception {
        contracts();completionAndRefinement();incompleteAndUnavailable();recovery();permissionsAndDeadline();errorDiagnostics();progressAndFailures();turnHistoryAndNonblockingSnapshot();
        System.out.println("PASS: "+checks+" contract and lifecycle checks (offline fixtures).");
    }
}
