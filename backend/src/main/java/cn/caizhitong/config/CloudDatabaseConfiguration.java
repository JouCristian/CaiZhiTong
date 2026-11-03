package cn.caizhitong.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.*;
import java.net.URI;
import java.util.*;

/** Fail before migrations if cloud configuration could touch platform schemas. */
@Configuration
@Profile("cloud")
public class CloudDatabaseConfiguration {
    @Bean FlywayConfigurationCustomizer isolatedCloudMigrations(
        @Value("${spring.datasource.url}") String appUrl,
        @Value("${spring.flyway.url}") String migrationUrl,
        @Value("${spring.datasource.hikari.schema}") String runtimeSchema) {
        return configuration -> {
            validateUrl(appUrl); validateUrl(migrationUrl);
            String schema=configuration.getDefaultSchema();
            if(schema==null || !schema.matches("caizhitong(?:_[a-z0-9]+)*") || !schema.equals(runtimeSchema) || !Arrays.equals(configuration.getSchemas(),new String[]{schema}) || !configuration.isCleanDisabled() || !configuration.isValidateOnMigrate())
                throw new IllegalStateException("Cloud Flyway must use one dedicated caizhitong schema; platform/public schemas are prohibited.");
        };
    }
    static void validateUrl(String url) {
        try {
            if(!url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri=URI.create(url.substring(5));
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getPort()==6543) throw new IllegalArgumentException();
            var query=new HashMap<String,String>();
            if(uri.getRawQuery()!=null) for(String part:uri.getRawQuery().split("&")) {
                var pair=part.split("=",2); if(pair.length==2)query.put(pair[0],pair[1]);
            }
            if(!Set.of("require","verify-ca","verify-full").contains(query.getOrDefault("sslmode","")) || query.containsKey("password") || query.containsKey("user")) throw new IllegalArgumentException();
        } catch(Exception e) {
            throw new IllegalStateException("Cloud PostgreSQL requires a credential-free JDBC URL with SSL; use Direct or Session pooler 5432, not Transaction pooler 6543.");
        }
    }
}
