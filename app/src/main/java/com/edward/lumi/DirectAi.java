package com.edward.lumi;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Personal-device orchestration. All provider requests go directly to api.openai.com. */
public final class DirectAi {
    public interface Transport { JSONObject call(String path, String method, JSONObject body) throws Exception; }
    public interface Store { String get(String key); void put(String key, String value); void remove(String key); }
    public static final class ApiError extends Exception {
        public final int status;
        public ApiError(int status, String message) { super(message); this.status = status; }
    }
    private final Transport transport;
    private final Store store;
    private final ExecutorService planner = Executors.newSingleThreadExecutor();
    private final AtomicBoolean activating = new AtomicBoolean();
    private final Map<String, Run> runs = new HashMap<>();
    private Future<?> planning;
    private volatile boolean stopped;
    private volatile long generation;
    private static final String[] MISSION_IDS = {"garden", "comet", "rings"};
    private static final class Run {
        final long created = System.currentTimeMillis();
        final Map<Integer, JSONObject> decisions = new HashMap<>();
    }
    public DirectAi(Transport transport, Store store) { this.transport = transport; this.store = store; }
    private static JSONObject obj(Object... pairs) throws Exception {
        JSONObject value = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) value.put((String)pairs[i], pairs[i + 1]);
        return value;
    }
    private synchronized void status(String service, String value) { store.put("status_" + service, value); }
    public synchronized JSONObject health() throws Exception {
        JSONObject out = obj("mode", "direct_openai", "version", "0.2.1",
            "decisions", fallback(store.get("status_decisions"), "Não testado"),
            "vault", fallback(store.get("status_vault"), "Não criado"),
            "session", fallback(store.get("status_session"), "Não iniciada"),
            "environment", fallback(store.get("status_environment"), "Não iniciado"),
            "vault_id", store.get("vault_id"), "session_id", store.get("last_session_id"),
            "environment_id", store.get("environment_id"), "request_id", store.get("decision_id"),
            "updated_at", store.get("updated_at"), "mission_ready", !store.get("mission").isEmpty(),
            "busy", activating.get() || (planning != null && !planning.isDone()),
            "daily_decisions", count("decisions"), "daily_sessions", count("sessions"));
        out.put("ready", store.get("decision_validated").equals("true") && !store.get("vault_id").isEmpty()
                && store.get("environment_validated").equals("true") && store.get("mission_validated").equals("true")
                && store.get("status_decisions").startsWith("Chamada real OK") && store.get("status_vault").startsWith("Criado"));
        return out;
    }
    private static String fallback(String value, String fallback) { return value.isEmpty() ? fallback : value; }
    private String day() { return Long.toString(System.currentTimeMillis() / 86400000L); }
    private int count(String kind) {
        if (!day().equals(store.get("budget_day"))) return 0;
        try { return Integer.parseInt(store.get("count_" + kind)); } catch (Exception ignored) { return 0; }
    }
    private synchronized void reserve(String kind, int limit) throws Exception {
        if (!day().equals(store.get("budget_day"))) {
            store.put("budget_day", day()); store.put("count_decisions", "0"); store.put("count_sessions", "0");
        }
        int n = count(kind);
        if (n >= limit) throw new ApiError(429, "Limite diário local atingido. O jogo continua offline.");
        store.put("count_" + kind, Integer.toString(n + 1));
    }
    private static String safeError(Exception e) {
        if (e instanceof ApiError) return e.getMessage();
        if (e instanceof java.net.SocketTimeoutException) return "Tempo de resposta excedido · tente novamente";
        if (e instanceof java.net.UnknownHostException) return "Não foi possível localizar a OpenAI · confira a conexão";
        if (e instanceof javax.net.ssl.SSLException) return "Falha na conexão segura com a OpenAI";
        if (e instanceof org.json.JSONException) return "Formato de resposta inesperado da OpenAI";
        return "Falha de conexão ou resposta inválida";
    }
    public JSONObject activate() throws Exception {
        if (!activating.compareAndSet(false, true)) return health();
        try {
            store.remove("decision_id");
            store.remove("decision_validated");
            status("decisions", "Testando chamada real…");
            try {
                JSONObject probe = obj("wave", 1, "stars", 0, "hits", 0, "shots", 0, "cleared", 0, "elapsed", 0);
                decision(probe);
            } catch (Exception e) { status("decisions", safeError(e)); }
            try { if(planning==null||planning.isDone())recover(); ensureVault(); }
            catch (Exception e) { status("vault", safeError(e)); }
            if(store.get("status_vault").startsWith("Criado")) {
                try { ensurePlan(); } catch (Exception e) { status("session",safeError(e)); }
            }
            store.put("updated_at", Long.toString(System.currentTimeMillis()));
            return health();
        } finally { activating.set(false); }
    }
    private synchronized String ensureVault() throws Exception {
        String id = store.get("vault_id");
        status("vault", "Verificando Vault…");
        if (!id.isEmpty()) {
            try { transport.call("/vaults/" + resourceId(id), "GET", null); }
            catch (ApiError e) { if (e.status == 404) { store.remove("vault_id"); id = ""; } else throw e; }
        }
        if (id.isEmpty()) {
            // Empty vault is intentional: this self-contained game has no external service credentials.
            JSONObject result = transport.call("/vaults", "POST", obj("name", "LUMI Orbit · celular",
                "metadata", obj("app", "lumi-orbit", "purpose", "mobile-agent-integrations")));
            id = resourceId(result.getString("id")); store.put("vault_id", id);
        }
        status("vault", "Criado · sem credenciais externas");
        return id;
    }
    public synchronized void ensurePlan() throws Exception {
        if (stopped || !store.get("mission").isEmpty() || (planning != null && !planning.isDone())) return;
        if (store.get("vault_id").isEmpty()) { status("session", "Conecte e teste a OpenAI primeiro"); return; }
        if(!store.get("pending_cleanup").isEmpty())throw new ApiError(409,"Encerramento pendente. Toque em testar novamente.");
        reserve("sessions", 12);
        store.remove("environment_validated");store.remove("mission_validated");
        long token = generation;
        status("session", "Criando sessão…"); status("environment", "Preparando sandbox…");
        planning = planner.submit(() -> plan(token));
    }
    private boolean current(long token) { return !stopped && token == generation && !Thread.currentThread().isInterrupted(); }
    private void plan(long token) {
        String sessionId = "";
        try {
            if (!current(token)) return;
            String input = "Create a safe flight pattern for a young child's space game. Return exactly a JSON object with mission_id (garden, comet, or rings), star_lanes (8 numbers between 0.12 and 0.88), asteroid_speed (number 0.80 to 1.10). Use Python in the environment to generate and validate the JSON; print the JSON, then return that same JSON as the final answer. Ensure neighboring lanes differ by at most 0.40. No free text, executable content, or additional fields. Eight stars are always the goal.";
            JSONObject created = transport.call("/agents/sessions", "POST", obj(
                "agent", obj("model", "gpt-6-astra", "instructions", "You design small, playful numeric game configurations. Run Python to validate every requested bound. No delegation, network access, external services, or personal data. Use the sandbox and return only the final JSON."),
                "environment", obj("type", "openai_hosted", "container_size", "small", "network", obj("access", "disabled")),
                "vault_ids", new JSONArray().put(store.get("vault_id")),
                "metadata", obj("app", "lumi-orbit", "client", "android-direct", "contract", "2"),
                "input", input, "stream", false));
            sessionId = resourceId(created.getString("id"));
            store.put("pending_cleanup", sessionId); store.put("last_session_id", sessionId);
            JSONObject environment = created.optJSONObject("environment");
            if (environment != null) store.put("environment_id", environment.optString("id", ""));
            status("session", "Sessão criada · preparando missão");
            long deadline = System.currentTimeMillis() + 110000;
            while (current(token) && System.currentTimeMillis() < deadline) {
                JSONObject mission = fetchMission(sessionId);
                if (mission != null) {
                    if (!store.get("environment_validated").equals("true")) throw new Exception("Environment not connected");
                    if (current(token)) {
                        store.put("mission", mission.toString()); store.put("mission_validated", "true"); store.put("updated_at", Long.toString(System.currentTimeMillis()));
                        status("session", "Missão gerada e validada");
                    }
                    return;
                }
                Thread.sleep(3000);
            }
            if (current(token)) status("session", "Tempo de preparo excedido · toque em testar");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) { if (current(token)) status("session", safeError(e)); }
        finally { if (!sessionId.isEmpty()) cleanup(sessionId); }
    }
    JSONObject fetchMission(String sessionId) throws Exception {
        String path = "/agents/sessions/" + resourceId(sessionId);
        JSONObject session = transport.call(path, "GET", null);
        String state = session.optString("status");
        if (state.equals("failed") || state.equals("requires_action")) throw new ApiError(422, "Sessão precisa de atenção: " + state);
        JSONObject environment = session.optJSONObject("environment");
        String envId = environment == null ? store.get("environment_id") : environment.optString("id", store.get("environment_id"));
        if (!envId.isEmpty()) {
            store.put("environment_id", envId);
            JSONObject env = transport.call("/agents/environments/" + resourceId(envId), "GET", null);
            String envState = env.optString("status");
            if (envState.equals("failed")) throw new ApiError(422, "Falha ao iniciar Environment");
            if (envState.equals("connected")) { store.put("environment_validated", "true"); status("environment", "Sandbox conectado · rede desativada"); }
            else status("environment", "Sandbox: " + envState);
        }
        JSONArray turns = transport.call(path + "/turns?order=desc&limit=20", "GET", null).optJSONArray("data");
        if (turns == null) return null;
        JSONObject root = null;
        for (int i = 0; i < turns.length(); i++) {
            JSONObject turn = turns.getJSONObject(i);
            if (turn.isNull("subagent_id")) { root = turn; break; }
        }
        if (root == null) return null;
        String turnState = root.optString("status");
        if (turnState.equals("failed") || turnState.equals("cancelled")) throw new ApiError(422, "A missão não foi concluída");
        if (!turnState.equals("completed")) return null;
        String after = "";
        for (int page = 0; page < 10; page++) {
            JSONObject items = transport.call(path + "/items?order=asc&limit=100&turn_id=" + resourceId(root.getString("id")) + after, "GET", null);
            JSONArray data = items.optJSONArray("data");
            if (data == null) break;
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                if (!item.optString("type").equals("message") || !item.optString("role").equals("assistant") || !item.optString("phase").equals("final_answer")) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject part = content.getJSONObject(j);
                    if (part.optString("type").equals("output_text")) return validateMission(new JSONObject(part.getString("text")));
                }
            }
            if (!items.optBoolean("has_more")) break;
            after = "&after=" + resourceId(items.getString("last_id"));
        }
        throw new ApiError(422, "Resposta de missão inválida · mantendo jogo local");
    }
    static JSONObject validateMission(JSONObject value) throws Exception {
        if (value.length() != 3 || !value.has("mission_id") || !value.has("star_lanes") || !value.has("asteroid_speed")) throw new Exception("Invalid mission schema");
        String id = value.getString("mission_id");
        boolean valid = false; for (String allowed : MISSION_IDS) if (id.equals(allowed)) valid = true;
        if (!valid) throw new Exception("Unknown mission");
        Object speedValue = value.get("asteroid_speed");
        if (!(speedValue instanceof Number)) throw new Exception("Invalid speed");
        double speed = ((Number)speedValue).doubleValue();
        if (!Double.isFinite(speed) || speed < .8 || speed > 1.1) throw new Exception("Invalid speed bounds");
        JSONArray lanes = value.getJSONArray("star_lanes");
        if (lanes.length() != 8) throw new Exception("Invalid star count");
        for (int i = 0; i < lanes.length(); i++) {
            if (!(lanes.get(i) instanceof Number)) throw new Exception("Invalid lane");
            double lane = lanes.getDouble(i);
            if (!Double.isFinite(lane) || lane < .12 || lane > .88 || (i > 0 && Math.abs(lane - lanes.getDouble(i-1)) > .400001)) throw new Exception("Invalid lane bounds");
        }
        return obj("id", id, "star_lanes", lanes, "asteroid_speed", speed, "source", "openai_agents");
    }
    private static void validateSummary(JSONObject value) throws Exception {
        String[] keys = {"wave", "stars", "hits", "shots", "cleared", "elapsed"};
        int[] lower = {1,0,0,0,0,0}, upper = {2,30,3,500,150,60};
        if (value.length() != keys.length) throw new Exception("Invalid metrics");
        for (int i=0;i<keys.length;i++) {
            Object n = value.get(keys[i]);
            if (!(n instanceof Integer) && !(n instanceof Long)) throw new Exception("Invalid metric type");
            long v = ((Number)n).longValue();
            if (v < lower[i] || v > upper[i]) throw new Exception("Invalid metric range");
        }
    }
    private JSONObject decision(JSONObject summary) throws Exception {
        validateSummary(summary);
        store.remove("decision_validated"); store.remove("decision_id");
        reserve("decisions", 24);
        JSONObject response = transport.call("/decisions", "POST", obj("model", "gpt-6-luna", "input", summary.toString(), "questions", new JSONArray().put(obj(
            "type", "choice", "name", "pace", "instructions", "Choose gameplay pace for a young child using these aggregate metrics. Prefer gentle after collisions. Bright only with at least four collected stars and no hits. Never optimize session duration.",
            "choices", new JSONArray().put(obj("value", "gentle", "description", "Slow and forgiving"))
                .put(obj("value", "steady", "description", "Moderate unchanged pace"))
                .put(obj("value", "bright", "description", "At most 15 percent harder"))))));
        JSONArray answers = response.optJSONArray("answers");
        if (answers != null) for (int i=0;i<answers.length();i++) {
            JSONObject answer = answers.getJSONObject(i); String choice = answer.optString("choice");
            if (answer.optString("type").equals("choice") && answer.optString("name").equals("pace") && (choice.equals("gentle") || choice.equals("steady") || choice.equals("bright"))) {
                if (choice.equals("bright") && (summary.getInt("stars")<4 || summary.getInt("hits")>0)) choice="steady";
                // Decisions returns answers/model/usage, not a resource id.
                // The native transport may attach the HTTP x-request-id for diagnostics.
                String id = response.optString("_request_id", "");
                if (!id.matches("[A-Za-z0-9_-]{1,160}")) id = "";
                store.put("decision_id", id); store.put("decision_validated", "true");
                status("decisions", "Chamada real OK · " + choice); store.put("updated_at", Long.toString(System.currentTimeMillis()));
                return obj("choice", choice, "source", "openai_decisions", "provider_id", id);
            }
        }
        throw new ApiError(422, "Decisions recusou ou retornou uma resposta inválida");
    }
    public JSONObject handle(String path, String method, JSONObject body) throws Exception {
        if (path.equals("/health") && method.equals("GET")) return health();
        if (path.equals("/activate") && method.equals("POST")) return activate();
        if (path.equals("/v1/runs") && method.equals("POST")) {
            JSONObject mission = null; String id = UUID.randomUUID().toString();
            synchronized(this) {
                Iterator<Map.Entry<String,Run>> it = runs.entrySet().iterator();
                while(it.hasNext()) if(System.currentTimeMillis()-it.next().getValue().created>120000)it.remove();
                if(runs.size()>=2)throw new ApiError(429,"Duas partidas já estão em andamento");
                runs.put(id, new Run());
                if(!store.get("mission").isEmpty()){mission=new JSONObject(store.get("mission"));store.remove("mission");}
            }
            try { ensurePlan(); } catch(Exception e) { status("session",safeError(e)); }
            return obj("id",id,"status",mission==null?"planning":"ready","mission",mission==null?JSONObject.NULL:mission);
        }
        String[] parts=path.split("/");
        if(parts.length<4 || !path.startsWith("/v1/runs/"))throw new ApiError(404,"Operação inválida");
        String id=parts[3]; Run run;
        synchronized(this){run=runs.get(id);}
        if(run==null)throw new ApiError(404,"Partida não encontrada");
        if(method.equals("DELETE") && parts.length==4){synchronized(this){runs.remove(id);}return obj("closed",true);}
        if(System.currentTimeMillis()-run.created>120000)throw new ApiError(410,"Partida encerrada");
        if(method.equals("GET") && parts.length==4){synchronized(this){JSONObject mission=store.get("mission").isEmpty()?null:new JSONObject(store.get("mission"));if(mission!=null)store.remove("mission");return obj("id",id,"status",mission==null?"planning":"ready","mission",mission==null?JSONObject.NULL:mission);}}
        if(method.equals("POST") && parts.length==5 && parts[4].equals("decision")){
            validateSummary(body);int wave=body.getInt("wave");
            synchronized(run){
                if(run.decisions.containsKey(wave))return run.decisions.get(wave);
                JSONObject result;
                try{result=decision(body);}catch(Exception e){status("decisions",safeError(e));throw e;}
                run.decisions.put(wave,result);return result;
            }
        }
        throw new ApiError(404,"Operação inválida");
    }
    private static String resourceId(String id) throws Exception {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,160}"))throw new Exception("Invalid resource ID");
        return id;
    }
    private void cleanup(String id) {
        try {
            try { transport.call("/agents/sessions/"+resourceId(id),"DELETE",null); }
            catch(ApiError e) { if(e.status!=404)throw e; }
            if(store.get("pending_cleanup").equals(id))store.remove("pending_cleanup");
            if(store.get("environment_validated").equals("true"))status("environment","Sandbox validado e encerrado");
        } catch(Exception e){status("environment","Encerramento pendente · será repetido");}
    }
    public void recover() { String id=store.get("pending_cleanup");if(!id.isEmpty())cleanup(id); }
    public void stop() {
        stopped=true;generation++;
        if(planning!=null)planning.cancel(true);
        planner.shutdownNow();
        try { planner.awaitTermination(25, java.util.concurrent.TimeUnit.SECONDS); } catch(InterruptedException e){Thread.currentThread().interrupt();}
        recover();
    }
}
