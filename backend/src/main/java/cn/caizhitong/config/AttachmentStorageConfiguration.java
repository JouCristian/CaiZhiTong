package cn.caizhitong.config;

import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@ConditionalOnProperty(name="app.storage.mode",havingValue="supabase")
public class AttachmentStorageConfiguration {
    @Bean("attachmentHttpClient")
    @ConditionalOnMissingBean(name="attachmentHttpClient")
    HttpClient attachmentHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }
}
