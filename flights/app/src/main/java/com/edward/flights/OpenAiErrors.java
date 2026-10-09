package com.edward.flights;

import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static com.edward.flights.FlightContracts.obj;

/** Small, redacted diagnostics; never stores request bodies or authorization headers. */
public final class OpenAiErrors {
    private OpenAiErrors() {}
    public static String readBody(InputStream in,int limit)throws Exception {
        if(in==null)return "";
        try(InputStream input=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[Math.min(4096,limit)];int remaining=limit,n;
            while(remaining>0&&(n=input.read(buffer,0,Math.min(buffer.length,remaining)))!=-1){out.write(buffer,0,n);remaining-=n;}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public static String shortStatus(int status){
        switch(status){
            case 400:return "requisição inválida (400)";
            case 401:return "chave inválida (401)";
            case 403:return "acesso negado (403)";
            case 404:return "recurso indisponível (404)";
            case 429:return "limite de uso ou saldo (429)";
            default:return "HTTP "+status;
        }
    }
    private static String redacted(String value,String key,int limit){
        String text=value==null?"":value;
        if(key!=null&&!key.isEmpty())text=text.replace(key,"[removido]");
        text=text.replaceAll("sk-[A-Za-z0-9_-]{8,}","[removido]").replaceAll("(?i)Bearer\\s+\\S+","Bearer [removido]")
            .replaceAll("[\\p{Cntrl}\\u2028\\u2029]"," ").replaceAll("\\s+"," ").trim();
        return text.substring(0,Math.min(limit,text.length()));
    }
    private static String identifier(String value,String key){
        String clean=redacted(value,key,160);
        return clean.equals("null")||!clean.matches("[A-Za-z0-9_.\\[\\]-]{1,160}")?"":clean;
    }
    public static FlightAgent.ApiError fromResponse(int status,String path,String raw,String requestId,String key)throws Exception {
        JSONObject error=null;
        try{error=new JSONObject(raw).optJSONObject("error");}catch(Exception ignored){}
        String service=path.startsWith("/live/")?"GPT Live":path.startsWith("/decisions")?"Decisions":path.startsWith("/vaults")?"Vault":path.startsWith("/agents/environments")?"Environment":path.startsWith("/agents/sessions")?"Session":"OpenAI";
        String param=error==null?"":identifier(error.optString("param",""),key);
        String code=error==null?"":identifier(error.optString("code",""),key);
        String type=error==null?"":identifier(error.optString("type",""),key);
        String detail=error==null?"":redacted(error.optString("message",""),key,600);
        String id=identifier(requestId,key);
        String message=service+" · "+shortStatus(status)+".";
        if(!param.isEmpty())message+=" Campo: "+param+".";
        else if(status==400&&!detail.isEmpty())message+=" "+detail.substring(0,Math.min(180,detail.length()));
        JSONObject diagnostic=obj("service",service,"status",status,"param",param,"code",code,"type",type,"request_id",id,"detail",detail,"at",System.currentTimeMillis());
        return new FlightAgent.ApiError(status,message,diagnostic);
    }
}
