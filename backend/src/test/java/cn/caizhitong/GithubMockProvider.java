package cn.caizhitong;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Loopback-only provider. Synthetic IDs only; never accepts a real GitHub credential. */
final class GithubMockProvider implements AutoCloseable {
    record Profile(String id,String login,String name,String avatar_url,String email){}
    record Grant(Profile profile,String challenge,String redirect){}
    final HttpServer server;
    final ExecutorService executor=Executors.newCachedThreadPool();
    final AtomicReference<Profile> profile=new AtomicReference<>();
    final AtomicInteger tokenCalls=new AtomicInteger();
    private final Map<String,Grant> codes=new ConcurrentHashMap<>();
    private final Map<String,Profile> tokens=new ConcurrentHashMap<>();
    private final ObjectMapper json=new ObjectMapper();
    GithubMockProvider() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(executor);
        server.createContext("/authorize",this::authorize);
        server.createContext("/token",this::token);
        server.createContext("/user",this::user);
        server.start();
    }
    String origin(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    static Map<String,String> query(String value){var m=new HashMap<String,String>();if(value!=null)for(String p:value.split("&")){var x=p.split("=",2);m.put(URLDecoder.decode(x[0],StandardCharsets.UTF_8),x.length>1?URLDecoder.decode(x[1],StandardCharsets.UTF_8):"");}return m;}
    private void authorize(HttpExchange x)throws IOException {
        var q=query(x.getRequestURI().getRawQuery());
        if(!"mock-client".equals(q.get("client_id"))||!"read:user".equals(q.get("scope"))||!"S256".equals(q.get("code_challenge_method"))||profile.get()==null){write(x,400,Map.of("error","invalid_request"));return;}
        String code=UUID.randomUUID().toString();codes.put(code,new Grant(profile.get(),q.get("code_challenge"),q.get("redirect_uri")));
        x.getResponseHeaders().add("Location",q.get("redirect_uri")+"?code="+code+"&state="+q.get("state"));x.sendResponseHeaders(302,-1);x.close();
    }
    private void token(HttpExchange x)throws IOException {
        tokenCalls.incrementAndGet();var q=query(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));var grant=codes.remove(q.get("code"));
        String proof=cn.caizhitong.service.OAuthExchangeService.challenge(q.getOrDefault("code_verifier",""));
        if(grant==null||!"mock-client".equals(q.get("client_id"))||!"mock-test-only-secret".equals(q.get("client_secret"))||!grant.challenge.equals(proof)||!grant.redirect.equals(q.get("redirect_uri"))){write(x,400,Map.of("error","invalid_grant"));return;}
        String token=UUID.randomUUID().toString();tokens.put(token,grant.profile);write(x,200,Map.of("access_token",token,"token_type","Bearer","scope","read:user"));
    }
    private void user(HttpExchange x)throws IOException {String header=x.getRequestHeaders().getFirst("Authorization");Profile p=header==null?null:tokens.get(header.replace("Bearer ",""));if(p==null)write(x,401,Map.of("error","unauthorized"));else write(x,200,p);}
    private void write(HttpExchange x,int status,Object body)throws IOException {byte[] bytes=json.writeValueAsBytes(body);x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(status,bytes.length);x.getResponseBody().write(bytes);x.close();}
    public void close(){server.stop(0);executor.shutdownNow();}
}
