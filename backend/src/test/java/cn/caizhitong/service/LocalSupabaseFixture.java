package cn.caizhitong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Loopback-only Storage contract fixture. Never contacts a Supabase project. */
final class LocalSupabaseFixture implements AutoCloseable {
    final HttpServer server;
    final Path files;
    final String bucket="caizhitong-attachments";
    final String credential="isolated-storage-fixture-key";
    final AtomicInteger uploads=new AtomicInteger(),downloads=new AtomicInteger();
    LocalSupabaseFixture() throws IOException {
        files=Files.createTempDirectory("czt-storage-contract-");
        server=HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),0),0);
        server.createContext("/storage/v1/",exchange->{
            int status=200; byte[] result=new byte[0];
            try {
                if(!credential.equals(exchange.getRequestHeaders().getFirst("apikey"))) {status=401;}
                else {
                    String path=exchange.getRequestURI().getPath().substring("/storage/v1/".length());
                    String method=exchange.getRequestMethod();
                    if(path.equals("bucket/"+bucket) && method.equals("GET")) result=("{\"id\":\""+bucket+"\",\"public\":false}").getBytes(StandardCharsets.UTF_8);
                    else if(path.equals("object/"+bucket) && method.equals("DELETE")) {
                        for(var item:new ObjectMapper().readTree(exchange.getRequestBody()).path("prefixes")) Files.deleteIfExists(safe(item.asText()));
                        result="[]".getBytes(StandardCharsets.UTF_8);
                    } else {
                        String prefix=path.startsWith("object/authenticated/")?"object/authenticated/"+bucket+"/":"object/"+bucket+"/";
                        if(!path.startsWith(prefix)) {status=404;}
                        else {
                            Path file=safe(path.substring(prefix.length()));
                            switch(method) {
                                case "POST" -> {
                                    byte[] bytes=exchange.getRequestBody().readNBytes(5*1024*1024+1);
                                    if(bytes.length>5*1024*1024) status=413;
                                    else if(Files.exists(file)) status=409;
                                    else {Files.write(file,bytes,StandardOpenOption.CREATE_NEW);uploads.incrementAndGet();result="{}".getBytes();}
                                }
                                case "GET" -> {if(!Files.exists(file))status=404;else{result=Files.readAllBytes(file);downloads.incrementAndGet();}}
                                case "HEAD" -> {if(!Files.exists(file))status=404;}
                                default -> status=405;
                            }
                        }
                    }
                }
            } catch(Exception e) {status=400;}
            exchange.sendResponseHeaders(status,exchange.getRequestMethod().equals("HEAD")?-1:result.length==0?-1:result.length);
            if(result.length>0&&!exchange.getRequestMethod().equals("HEAD"))exchange.getResponseBody().write(result);
            exchange.close();
        });
        server.start();
    }
    private Path safe(String key) throws IOException {
        if(!key.matches("[0-9a-f-]{36}\\.(png|jpg|webp)"))throw new IOException("Invalid fixture key");
        return files.resolve(key);
    }
    @SuppressWarnings({"unchecked","rawtypes"}) HttpClient client() throws Exception {
        HttpClient proxy=mock(HttpClient.class),real=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        when(proxy.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenAnswer(invocation->{
            HttpRequest original=invocation.getArgument(0);
            // Fixed loopback destination; the production HTTPS origin never receives any traffic.
            var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+original.uri().getPath())).timeout(java.time.Duration.ofSeconds(10));
            original.headers().map().forEach((name,values)->values.forEach(value->builder.header(name,value)));
            return real.send(builder.method(original.method(),original.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody())).build(),(HttpResponse.BodyHandler)invocation.getArgument(1));
        });
        return proxy;
    }
    @Override public void close() {server.stop(0);}
}
