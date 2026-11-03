package cn.caizhitong.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CloudDatabaseConfigurationTest {
    private final CloudDatabaseConfiguration config=new CloudDatabaseConfiguration();
    private final String url="jdbc:postgresql://session-pooler.example.test:5432/postgres?sslmode=require";
    @Test void permitsEncryptedSessionAndDirectConnections() {
        assertThatCode(()->CloudDatabaseConfiguration.validateUrl(url)).doesNotThrowAnyException();
        assertThatCode(()->CloudDatabaseConfiguration.validateUrl("jdbc:postgresql://[::1]:5432/postgres?sslmode=verify-full")).doesNotThrowAnyException();
    }
    @Test void refusesPlaintextCredentialsAndTransactionPooler() {
        for(String bad:new String[]{url.replace("require","disable"),url.replace(":5432",":6543"),url+"&password=test-only-value",url.replace("//","//user:test@"),"jdbc:postgresql://host/postgres"})
            assertThatThrownBy(()->CloudDatabaseConfiguration.validateUrl(bad)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("test-only-value");
    }
    @Test void refusesPlatformAndMultipleMigrationSchemasBeforeConnecting() {
        var customizer=config.isolatedCloudMigrations(url,url,"caizhitong");
        for(String schema:new String[]{"public","auth","storage","extensions","pg_catalog"})
            assertThatThrownBy(()->customizer.customize(Flyway.configure().defaultSchema(schema).schemas(schema))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->customizer.customize(Flyway.configure().defaultSchema("caizhitong").schemas("caizhitong","auth"))).isInstanceOf(IllegalStateException.class);
    }
    @Test void permitsOnlyOwnedSingleSchema() {
        assertThatCode(()->config.isolatedCloudMigrations(url,url,"caizhitong").customize(Flyway.configure().defaultSchema("caizhitong").schemas("caizhitong"))).doesNotThrowAnyException();
    }
    @Test void runtimeAndMigrationSchemaMustAgreeAndValidationCannotBeDisabled() {
        assertThatThrownBy(()->config.isolatedCloudMigrations(url,url,"public").customize(Flyway.configure().defaultSchema("caizhitong").schemas("caizhitong"))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->config.isolatedCloudMigrations(url,url,"caizhitong").customize(Flyway.configure().defaultSchema("caizhitong").schemas("caizhitong").validateOnMigrate(false))).isInstanceOf(IllegalStateException.class);
    }
}
