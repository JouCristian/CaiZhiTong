package cn.caizhitong.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** Private objects are proxied through the existing authenticated attachment API. */
@Component
@ConditionalOnProperty(name="app.storage.mode",havingValue="supabase")
public class SupabaseAttachmentStorage implements AttachmentStorage {
    private static final int MAX_BYTES=5*1024*1024;
    private final URI endpoint;
    private final String bucket, key;
    private final HttpClient client;
    @Autowired public SupabaseAttachmentStorage(
        @Value("${app.storage.supabase.url:}") String url,
        @Value("${app.storage.supabase.bucket:caizhitong-attachments}") String bucket,
        @Value("${app.storage.supabase.key:}") String key,
        @Qualifier("attachmentHttpClient") HttpClient client) {
        URI parsed;
        try { parsed=URI.create(url); } catch(Exception e) { throw new IllegalArgumentException("Supabase project URL is invalid"); }
        if(!"https".equals(parsed.getScheme()) || parsed.getHost()==null || !parsed.getHost().endsWith(".supabase.co") || parsed.getUserInfo()!=null || parsed.getQuery()!=null || parsed.getFragment()!=null || (parsed.getPort()!=-1 && parsed.getPort()!=443) || !(parsed.getPath().isEmpty() || parsed.getPath().equals("/")))
            throw new IllegalArgumentException("Use the exact HTTPS Supabase project URL without credentials or path");
        if(!bucket.matches("[a-z0-9][a-z0-9-]{1,62}") || key.isBlank() || key.contains("\r") || key.contains("\n"))
            throw new IllegalArgumentException("Supabase private bucket and server-side storage key are required");
        this.endpoint=URI.create(url.replaceAll("/+$","")+"/storage/v1/");
        this.bucket=bucket; this.key=key; this.client=client;
    }
    private String object(String objectKey) {
        if(!objectKey.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)"))
            throw new BusinessException(400,"附件标识不合法");
        return bucket+"/"+objectKey;
    }
    private HttpRequest.Builder request(String path) {
        var builder=HttpRequest.newBuilder(endpoint.resolve(path)).timeout(Duration.ofSeconds(20)).header("apikey",key);
        // Legacy service_role JWTs need Bearer; new sb_secret keys authenticate at the gateway via apikey.
        if(key.startsWith("eyJ")) builder.header("Authorization","Bearer "+key);
        return builder;
    }
    private HttpResponse<InputStream> send(HttpRequest request) throws IOException {
        try { return client.send(request,HttpResponse.BodyHandlers.ofInputStream()); }
        catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Storage request interrupted"); }
        catch(IOException e) { throw new IOException("Supabase storage is unavailable"); }
    }
    private byte[] bounded(InputStream body) throws IOException {
        byte[] bytes=body.readNBytes(MAX_BYTES+1);
        if(bytes.length>MAX_BYTES) throw new IOException("Stored object exceeds attachment size limit");
        return bytes;
    }
    @PostConstruct public void verifyPrivateBucket() {
        try {
            var response=send(request("bucket/"+bucket).GET().build());
            try(var body=response.body()) {
                if(response.statusCode()!=200) throw new IOException("Cannot verify storage bucket (HTTP "+response.statusCode()+")");
                var info=new ObjectMapper().readTree(bounded(body));
                if(!info.path("public").isBoolean() || info.path("public").asBoolean() || !bucket.equals(info.path("id").asText()))
                    throw new IOException("Storage bucket must exist and be private");
            }
        } catch(IOException e) { throw new IllegalStateException("Supabase private bucket verification failed; check bucket privacy and backend credentials."); }
    }
    @Override public void save(String objectKey,byte[] bytes) throws IOException {
        String path=object(objectKey);
        if(bytes.length==0 || bytes.length>MAX_BYTES) throw new IOException("Attachment size is invalid");
        String type=objectKey.endsWith(".jpg")?"image/jpeg":objectKey.endsWith(".png")?"image/png":"image/webp";
        var response=send(request("object/"+path).header("Content-Type",type).header("x-upsert","false").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build());
        try(var body=response.body()) {
            if(response.statusCode()!=200 && response.statusCode()!=201) throw new IOException("Storage upload failed (HTTP "+response.statusCode()+")");
        }
    }
    @Override public byte[] read(String objectKey) throws IOException {
        var response=send(request("object/authenticated/"+object(objectKey)).GET().build());
        try(var body=response.body()) {
            if(response.statusCode()==400 || response.statusCode()==404) throw new IOException("Stored attachment not found");
            if(response.statusCode()!=200) throw new BusinessException(503,"附件存储服务暂时不可用，请稍后重试");
            return bounded(body);
        }
    }
    @Override public boolean exists(String objectKey) {
        try {
            var response=send(request("object/"+object(objectKey)).method("HEAD",HttpRequest.BodyPublishers.noBody()).build());
            try(var body=response.body()) {
                if(response.statusCode()==200) return true;
                if(response.statusCode()==400 || response.statusCode()==404) return false;
                throw new IOException("Storage metadata unavailable");
            }
        } catch(IOException e) { throw new BusinessException(503,"附件存储服务暂时不可用，请稍后重试"); }
    }
    @Override public void delete(String objectKey) throws IOException {
        String path=object(objectKey);
        // The Storage API removes a list of object prefixes from one private bucket.
        var response=send(request("object/"+bucket).header("Content-Type","application/json").method("DELETE",HttpRequest.BodyPublishers.ofString("{\"prefixes\":[\""+path.substring(bucket.length()+1)+"\"]}")).build());
        try(var body=response.body()) {
            if(response.statusCode()!=200 && response.statusCode()!=204 && response.statusCode()!=404)
                throw new IOException("Storage cleanup failed (HTTP "+response.statusCode()+")");
        }
    }
}
