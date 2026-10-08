package com.edward.lumi;
import org.json.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;

/** Contract fixtures, not live network calls. See official Decisions response schema. */
public final class DirectAiTest {
    static int checks;
    static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks++;}
    static class Store implements DirectAi.Store {
        final Map<String,String> data=new ConcurrentHashMap<>();
        public String get(String k){return data.getOrDefault(k,"");}
        public void put(String k,String v){data.put(k,v);}
        public void remove(String k){data.remove(k);}
    }
    static final String FIRST="{\"width\":0.28,\"current\":\"calm\",\"cargo\":false,\"theme\":\"meadow\",\"focus\":\"bridge\",\"reason\":\"first\"}";
    static String image()throws Exception{return "data:image/png;base64,"+Files.readString(Path.of("tests/fixtures/bridge.b64"));}
    static JSONObject attempt()throws Exception{return new JSONObject().put("attempt_id",UUID.randomUUID().toString()).put("image",image());}
    static class Wire implements DirectAi.Transport {
        AtomicInteger decisions=new AtomicInteger(),deletes=new AtomicInteger(),vaults=new AtomicInteger(),sessions=new AtomicInteger();
        volatile JSONObject decisionBody,sessionBody;
        String choice="bridge",problem="",mission=FIRST;double confidence=.95;volatile boolean waiting;
        public JSONObject call(String path,String method,JSONObject body)throws Exception{
            if(path.equals("/decisions")){
                decisions.incrementAndGet();decisionBody=body;
                if(problem.equals("403"))throw new DirectAi.ApiError(403,"Sem permissão (HTTP 403)");
                if(problem.equals("timeout"))throw new java.net.SocketTimeoutException("private diagnostic");
                JSONObject r=new JSONObject(Files.readString(Path.of("tests/fixtures/decision-choice.json")));
                JSONObject a=r.getJSONArray("answers").getJSONObject(0);a.put("choice",choice).put("confidence",confidence);
                if(problem.equals("refusal"))a.put("type","refusal");if(problem.equals("missing"))r.remove("answers");return r;
            }
            if(path.equals("/vaults")){vaults.incrementAndGet();return new JSONObject().put("id","vault_fixture");}
            if(path.equals("/vaults/vault_fixture"))return new JSONObject().put("id","vault_fixture");
            if(path.equals("/agents/sessions")&&method.equals("POST")){sessionBody=body;int n=sessions.incrementAndGet();return new JSONObject().put("id","sess_"+n).put("environment",new JSONObject().put("id","env_fixture"));}
            if(method.equals("DELETE")){deletes.incrementAndGet();return new JSONObject();}
            if(path.startsWith("/agents/environments/"))return new JSONObject().put("status","connected");
            if(path.contains("/turns?"))return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("id","turn_fixture").put("status",waiting?"in_progress":"completed").put("subagent_id",JSONObject.NULL)));
            if(path.contains("/items?"))return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("type","message").put("role","assistant").put("phase","final_answer").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",mission))))).put("has_more",false);
            if(path.startsWith("/agents/sessions/"))return new JSONObject().put("status","idle").put("environment",new JSONObject().put("id","env_fixture"));
            throw new AssertionError("Unexpected endpoint "+path);
        }
    }
    static void done(DirectAi ai)throws Exception{for(int i=0;i<450&&ai.health().getBoolean("busy");i++)Thread.sleep(10);check(!ai.health().getBoolean("busy"),"planner completed");}
    static JSONObject classify(DirectAi ai)throws Exception{return ai.handle("/v1/classify","POST",attempt());}
    static JSONObject result(String id,String choice,String source,String c)throws Exception{return new JSONObject().put("attempt_id",id).put("choice",choice).put("source",source).put("challenge",new JSONObject(c));}
    public static void main(String[] args)throws Exception{try{run();System.out.println("PASS "+checks+" assertions; drawing/agent fixtures; zero live requests.");}catch(Throwable e){e.printStackTrace();System.exit(1);}}
    static void run()throws Exception{
        Store store=new Store();store.put("mission","old space mission");store.put("vault_id","vault_fixture");store.put("status_decisions","Chamada real OK");
        Wire wire=new Wire();DirectAi ai=new DirectAi(wire,store);
        check(store.get("mission").isEmpty(),"space mission migration");check(store.get("vault_id").equals("vault_fixture"),"existing vault retained");
        check(!ai.health().getBoolean("ready"),"never ready from old integration");ai.activate();done(ai);
        check(ai.health().getBoolean("ready"),"no-ID Decisions response plus validated challenge accepted");
        check(ai.health().getString("request_id").isEmpty(),"no fabricated request ID");check(wire.vaults.get()==0,"reuse configured vault");
        check(wire.decisionBody.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(1).getString("type").equals("input_image"),"real visual Decisions request");
        check(wire.decisionBody.getJSONArray("questions").getJSONObject(0).getJSONArray("choices").length()==4,"four solution categories");
        check(wire.sessionBody.getJSONObject("environment").getJSONObject("network").getString("access").equals("disabled"),"sandbox network off");check(wire.sessionBody.getJSONArray("vault_ids").getString(0).equals("vault_fixture"),"vault attached");check(wire.deletes.get()==1,"planning resources deleted");
        JSONObject body=attempt(),answer=ai.handle("/v1/classify","POST",body);int calls=wire.decisions.get();ai.handle("/v1/classify","POST",body);check(calls==wire.decisions.get(),"same attempt cached");
        check(answer.getString("choice").equals("bridge"),"bridge recognized");
        for(String choice:new String[]{"boat","jump","unclear"}){wire.choice=choice;check(classify(ai).getString("choice").equals(choice),"classification "+choice);}
        wire.choice="boat";wire.confidence=.30;check(classify(ai).getString("choice").equals("unclear"),"uncertain drawing does not trigger wrong animation");wire.confidence=.95;
        String hard=FIRST.replace("calm","fast");JSONObject boat=classify(ai);
        JSONObject outcome=result(boat.getString("attempt_id"),"boat","openai_decisions",hard);
        JSONObject recorded=ai.handle("/v1/outcomes","POST",outcome);done(ai);check(!recorded.getBoolean("success"),"boat fails fast current locally");
        String input=wire.sessionBody.getString("input");check(input.contains("\"choice\":\"boat\"")&&input.contains("\"success\":false"),"Agents receives actual previous failed solution");check(!input.contains("data:image"),"Agents does not receive drawing images");
        int seq=ai.health().getInt("sequence");ai.handle("/v1/outcomes","POST",outcome);check(ai.health().getInt("sequence")==seq,"duplicate result not recorded twice");
        check(ai.handle("/v1/challenge","GET",new JSONObject()).getJSONObject("challenge").getInt("planned_after")==seq,"challenge tied to history version");
        for(String error:new String[]{"403","timeout","refusal","missing"}){wire.problem=error;try{classify(ai);throw new AssertionError("error accepted");}catch(DirectAi.ApiError e){check(!e.getMessage().contains("private"),"sanitized "+error);}check(!ai.health().getBoolean("ready"),"failure resets readiness "+error);}
        wire.problem="";wire.choice="teleport";try{classify(ai);throw new AssertionError("unknown accepted");}catch(DirectAi.ApiError expected){checks++;}
        int before=wire.decisions.get();try{ai.handle("/v1/classify","POST",attempt().put("name","child"));throw new AssertionError("extra data accepted");}catch(DirectAi.ApiError expected){checks++;}check(before==wire.decisions.get(),"extra data never leaves phone");
        try{DirectAi.validateImage("https://example.invalid/image.png");throw new AssertionError("remote URL accepted");}catch(DirectAi.ApiError expected){checks++;}
        for(String bad:new String[]{FIRST.replace("0.28","0.9"),FIRST.replace("false","\"false\""),FIRST.replace("meadow","unknown"),FIRST.replace("bridge","jump").replace("0.28","0.45"),FIRST.replace("bridge","boat").replace("calm","fast"),FIRST.replace("}",",\"code\":\"run()\"}")}){try{DirectAi.validateMission(new JSONObject(bad));throw new AssertionError("unsafe challenge accepted");}catch(Exception expected){checks++;}}
        check(DirectAi.success(new JSONObject(FIRST),"jump"),"jump crosses narrow river");check(!DirectAi.success(new JSONObject(FIRST.replace("false","true")),"jump"),"cargo prevents jump");check(DirectAi.success(new JSONObject(hard),"bridge"),"bridge stays viable");
        check(store.data.values().stream().noneMatch(v->v.contains("data:image/png")),"drawing not persisted");ai.stop();
        Wire invalid=new Wire();invalid.mission="{}";DirectAi badAi=new DirectAi(invalid,new Store());badAi.activate();done(badAi);check(!badAi.health().getBoolean("ready"),"invalid agent mission rejected");check(invalid.deletes.get()==1,"invalid mission still cleans session");badAi.stop();
        Wire idle=new Wire();idle.waiting=true;DirectAi idleAi=new DirectAi(idle,new Store());check(idleAi.fetchMission("sess_1")==null,"idle session is not a completed answer");idleAi.stop();
        Wire lag=new Wire();lag.waiting=true;DirectAi late=new DirectAi(lag,new Store());late.activate();
        late.handle("/v1/outcomes","POST",result(UUID.randomUUID().toString(),"boat","manual",hard));
        lag.waiting=false;done(late);
        JSONObject fresh=late.handle("/v1/challenge","GET",new JSONObject()).getJSONObject("challenge");
        check(fresh.getInt("planned_after")==1,"stale plan never overwrites newer outcome");
        check(lag.sessions.get()==2,"new history triggers a fresh plan after cleanup");late.stop();
        Store cap=new Store();DirectAi limited=new DirectAi(new Wire(),cap);cap.put("budget_day",Long.toString(System.currentTimeMillis()/86400000L));cap.put("count_decisions","24");try{classify(limited);throw new AssertionError("quota ignored");}catch(DirectAi.ApiError expected){check(expected.status==429,"local quota");}limited.stop();
    }
}
