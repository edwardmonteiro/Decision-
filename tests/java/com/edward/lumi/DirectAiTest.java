package com.edward.lumi;
import org.json.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/** JVM tests use recorded-shape fixtures, never live credentials or network calls. */
public final class DirectAiTest {
    static int checks;
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);checks++;}
    static class Store implements DirectAi.Store {
        Map<String,String> data=new ConcurrentHashMap<>();
        public String get(String k){return data.getOrDefault(k,"");}
        public void put(String k,String v){data.put(k,v);}
        public void remove(String k){data.remove(k);}
    }
    static final String VALID="{\"mission_id\":\"garden\",\"star_lanes\":[0.2,0.4,0.6,0.8,0.5,0.3,0.4,0.7],\"asteroid_speed\":0.9}";
    static class Wire implements DirectAi.Transport {
        AtomicInteger decisions=new AtomicInteger(),deletes=new AtomicInteger(),vaults=new AtomicInteger();
        JSONObject sessionBody,decisionBody; boolean blocked,invalid,waiting; String requestId="",decisionProblem=""; int pages;
        public JSONObject call(String path,String method,JSONObject body)throws Exception{
            if(path.equals("/decisions")){
                decisions.incrementAndGet();decisionBody=body;
                if(blocked)throw new DirectAi.ApiError(403,"Sem permissão (HTTP 403)");
                if(decisionProblem.equals("timeout"))throw new java.net.SocketTimeoutException("private diagnostic must not leak");
                JSONObject result=new JSONObject(java.nio.file.Files.readString(java.nio.file.Path.of("tests/fixtures/decision-choice.json")));
                if(!requestId.isEmpty())result.put("_request_id",requestId);
                JSONObject answer=result.getJSONArray("answers").getJSONObject(0);
                if(decisionProblem.equals("refusal"))answer.put("type","refusal");
                if(decisionProblem.equals("unknown"))answer.put("choice","unbounded_speed");
                if(decisionProblem.equals("missing"))result.remove("answers");
                return result;
            }
            if(path.equals("/vaults")){vaults.incrementAndGet();return new JSONObject("{\"id\":\"vault_fixture\"}");}
            if(path.equals("/vaults/vault_fixture"))return new JSONObject("{\"id\":\"vault_fixture\"}");
            if(path.equals("/agents/sessions")&&method.equals("POST")){sessionBody=body;return new JSONObject("{\"id\":\"sess_fixture\",\"environment\":{\"id\":\"env_fixture\"}}");}
            if(method.equals("DELETE")){deletes.incrementAndGet();return new JSONObject("{\"deleted\":true}");}
            if(path.equals("/agents/sessions/sess_fixture"))return new JSONObject("{\"status\":\"idle\",\"environment\":{\"id\":\"env_fixture\"}}");
            if(path.equals("/agents/environments/env_fixture"))return new JSONObject("{\"status\":\"connected\"}");
            if(path.contains("/turns?"))return new JSONObject("{\"data\":[{\"id\":\"turn_fixture\",\"status\":\""+(waiting?"in_progress":"completed")+"\",\"subagent_id\":null}]}");
            if(path.contains("/items?")){pages++;return new JSONObject().put("data",new JSONArray().put(new JSONObject().put("type","message").put("role","assistant").put("phase","final_answer").put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",invalid?"{\"mission_id\":\"bad\"}":VALID))))).put("has_more",false);}
            throw new AssertionError("Unexpected endpoint "+path);
        }
    }
    static void waitDone(DirectAi ai)throws Exception{for(int i=0;i<200&&ai.health().getBoolean("busy");i++)Thread.sleep(10);check(!ai.health().getBoolean("busy"),"planner completed");}
    static JSONObject summary(int stars)throws Exception{return new JSONObject().put("wave",1).put("stars",stars).put("hits",0).put("shots",30).put("cleared",4).put("elapsed",14);}
    public static void main(String[] args)throws Exception{
        Wire wire=new Wire();Store store=new Store();DirectAi ai=new DirectAi(wire,store);
        check(!ai.health().getBoolean("ready"),"No false ready before live evidence");
        ai.activate();waitDone(ai);
        JSONObject health=ai.health();check(health.getBoolean("ready"),"All services verified with transport responses");
        check(health.getString("request_id").isEmpty(),"no synthetic provider ID when header absent");
        check(wire.vaults.get()==1,"vault provisioned once");check(wire.deletes.get()==1,"sandbox session cleaned");
        check(wire.sessionBody.getJSONObject("environment").getJSONObject("network").getString("access").equals("disabled"),"network disabled");
        check(wire.sessionBody.getJSONArray("vault_ids").getString(0).equals("vault_fixture"),"vault attached");
        check(wire.decisionBody.getString("model").equals("gpt-6-luna"),"Decisions model");
        check(wire.decisionBody.getJSONArray("questions").getJSONObject(0).getString("type").equals("choice"),"typed choice");
        check(health.getString("decisions").endsWith("steady"),"difficulty clamped for beginner");
        wire.requestId="req_header_fixture";
        ai.activate();waitDone(ai);
        check(ai.health().getBoolean("ready"),"HTTP request ID is optional diagnostic evidence");
        check(ai.health().getString("request_id").equals("req_header_fixture"),"HTTP request ID preserved");
        for(String problem:new String[]{"refusal","unknown","missing","timeout"}){
            wire.decisionProblem=problem; ai.activate();waitDone(ai);
            check(!ai.health().getBoolean("ready"),"failed retest clears previous success: "+problem);
            check(ai.health().getString("request_id").isEmpty(),"no stale request ID: "+problem);
            if(problem.equals("timeout"))check(ai.health().getString("decisions").equals("Tempo de resposta excedido · tente novamente"),"timeout distinguished without leaking exception");
        }
        wire.decisionProblem=""; ai.activate();waitDone(ai);
        check(ai.health().getBoolean("ready"),"retest recovers without replacing key or vault");
        check(wire.vaults.get()==1,"retest reuses vault");
        JSONObject run=ai.handle("/v1/runs","POST",new JSONObject());String id=run.getString("id");
        check(run.getJSONObject("mission").getJSONArray("star_lanes").length()==8,"generated layout consumed");
        String route="/v1/runs/"+id+"/decision";JSONObject answer=ai.handle(route,"POST",summary(5));
        check(answer.getString("choice").equals("bright"),"successful pilot choice");int calls=wire.decisions.get();
        ai.handle(route,"POST",summary(5));check(wire.decisions.get()==calls,"decision idempotent");
        try{ai.handle(route,"POST",summary(5).put("name","child"));throw new AssertionError("extra field accepted");}catch(Exception expected){checks++;}
        ai.handle("/v1/runs/"+id,"DELETE",new JSONObject());
        try{ai.handle("/v1/runs/"+id,"GET",new JSONObject());throw new AssertionError("closed run accepted");}catch(Exception expected){checks++;}
        ai.stop();check(store.get("pending_cleanup").isEmpty(),"no pending owned sessions");
        for(String bad:new String[]{VALID.replace("0.9}","2.0}"),VALID.replace("0.2,0.4","0.05,0.4"),VALID.replace("garden","unknown"),VALID.replace("0.9}","\"0.9\"}"),VALID.replace("0.4,0.6","0.85,0.12"),VALID.replace("0.9}","0.9,\"code\":\"run()\"}")}){
            try{DirectAi.validateMission(new JSONObject(bad));throw new AssertionError("unsafe mission accepted");}catch(Exception expected){checks++;}
        }
        Wire fail=new Wire();fail.blocked=true;DirectAi partial=new DirectAi(fail,new Store());partial.activate();waitDone(partial);
        check(!partial.health().getBoolean("ready"),"403 cannot become ready");check(partial.health().getString("decisions").contains("403"),"permission error visible");partial.stop();
        Wire invalid=new Wire();invalid.invalid=true;DirectAi malformed=new DirectAi(invalid,new Store());malformed.activate();waitDone(malformed);check(!malformed.health().getBoolean("ready"),"invalid final output rejected");check(invalid.deletes.get()==1,"failure cleans sandbox");malformed.stop();
        Wire idle=new Wire();idle.waiting=true;DirectAi waiting=new DirectAi(idle,new Store());check(waiting.fetchMission("sess_fixture")==null,"idle is not completed");waiting.stop();
        Store budget=new Store();budget.put("budget_day",Long.toString(System.currentTimeMillis()/86400000L));budget.put("count_decisions","24");budget.put("count_sessions","12");Wire capped=new Wire();DirectAi limits=new DirectAi(capped,budget);limits.activate();check(capped.decisions.get()==0,"quota prevents actual request");check(capped.sessionBody==null,"session quota enforced");limits.stop();
        System.out.println("PASS "+checks+" assertions; fixture transport only; zero live OpenAI requests.");
    }
}
