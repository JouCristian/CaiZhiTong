package cn.caizhitong.service;

import cn.caizhitong.config.ProductionAdminInitializer;
import cn.caizhitong.mapper.OperationsMapper;
import com.fasterxml.jackson.databind.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.io.*;
import java.net.http.HttpClient;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.demo.enabled=false","app.bootstrap.enabled=false","app.alerts.scan-enabled=false","app.mail.dispatch-enabled=false","spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(CloudAdaptationIntegrationTest.StorageFixtureConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CloudAdaptationIntegrationTest {
    private static final String SCHEMA="caizhitong_cloud_test";
    private static final String PASSWORD="isolated-cloud-password-2026";
    private static final LocalSupabaseFixture STORAGE;
    static {try {STORAGE=new LocalSupabaseFixture();}catch(IOException e){throw new ExceptionInInitializerError(e);}}
    @TestConfiguration(proxyBeanMethods=false)
    static class StorageFixtureConfiguration {
        @Bean("attachmentHttpClient") HttpClient client() throws Exception {return STORAGE.client();}
    }
    @DynamicPropertySource static void configure(DynamicPropertyRegistry r) {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/caizhitong_test");
        if(!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test(\\?.*)?"))throw new IllegalStateException("Cloud adaptation tests require an independent *_test database");
        r.add("spring.datasource.url",()->url);
        r.add("spring.flyway.url",()->url);
        r.add("spring.datasource.hikari.schema",()->SCHEMA);
        r.add("spring.flyway.default-schema",()->SCHEMA);
        r.add("spring.flyway.schemas",()->SCHEMA);
        r.add("app.jwt.secret",()->Base64.getEncoder().encodeToString(new byte[48]));
        r.add("app.storage.mode",()->"supabase");
        r.add("app.storage.supabase.url",()->"https://cloud-fixture.supabase.co");
        r.add("app.storage.supabase.bucket",()->STORAGE.bucket);
        r.add("app.storage.supabase.key",()->STORAGE.credential);
    }
    @Autowired OperationsMapper db;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AttachmentStorage storage;
    @Autowired DataSource datasource;
    @Autowired Flyway flyway;
    private String admin;
    private void initialize(String name,String password) {
        new TransactionTemplate(transactions).executeWithoutResult(s->new ProductionAdminInitializer(db,passwords,name,password).run(null));
    }
    private void clear() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).endsWith("_test");
        assertThat(jdbc.queryForObject("SELECT current_schema()",String.class)).isEqualTo(SCHEMA);
        jdbc.execute("TRUNCATE mail_notification,inventory_alert,return_order_line,return_order,scrap_order,stock_attachment,stock_transaction,issue_order_line,issue_order,issue_monthly_usage,stock_batch,inbound_order_line,inbound_order,material_item,material_category,sys_user_role,sys_user RESTART IDENTITY CASCADE");
        db.update("INSERT INTO app_settings(id) VALUES(1) ON CONFLICT DO NOTHING");
    }
    @BeforeEach void setup()throws Exception {clear();initialize("cloud_admin",PASSWORD);admin=login("cloud_admin",PASSWORD);}
    @AfterEach void cleanup() {clear();}
    @AfterAll void stopFixture() {STORAGE.close();}
    private String login(String name,String password)throws Exception {
        var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).at("/data/accessToken").asText();
    }
    private JsonNode create(String path,Object value,String token)throws Exception {
        var result=mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }
    private byte[] photo()throws IOException {
        var out=new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
        return out.toByteArray();
    }
    private long upload(byte[] bytes)throws Exception {
        var result=mvc.perform(multipart("/api/scraps/attachments").file(new MockMultipartFile("file","fixture.png","image/png",bytes)).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).at("/data/id").asLong();
    }
    @Test void originalSixMigrationsCreatePrivateSchemaUsingIndependentConnection() {
        assertThat(storage).isInstanceOf(SupabaseAttachmentStorage.class);
        assertThat(flyway.getConfiguration().getDataSource()).isNotSameAs(datasource);
        assertThat(Arrays.stream(flyway.info().applied()).filter(i->i.getVersion()!=null).map(i->i.getVersion().getVersion()).toList()).containsExactly("1","2","3","4","5","6");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(db.count("SELECT count(*) FROM sys_role")).isEqualTo(6);
        assertThat(db.count("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='stock_attachment'",SCHEMA)).isEqualTo(1);
        assertThat(db.count("SELECT count(*) FROM pg_namespace n CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl,acldefault('n',n.nspowner))) a WHERE n.nspname=? AND a.grantee=0 AND a.privilege_type='USAGE'",SCHEMA)).isZero();
    }
    @Test void initialAdminIsHashedCanLoginAndMissingCredentialsDoNotResetIt() {
        String hash=jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username='cloud_admin'",String.class);
        assertThat(hash).startsWith("$2"); assertThat(passwords.matches(PASSWORD,hash)).isTrue();
        initialize("","");initialize("different_admin","different-initial-password-2026");
        assertThat(db.count("SELECT count(*) FROM sys_user")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username='cloud_admin'",String.class)).isEqualTo(hash);
    }
    @Test void refusesExistingUsernameTakeoverWhenNoAdminIsActive() {
        clear();db.update("INSERT INTO sys_user(username,display_name,password_hash,enabled) VALUES('occupied','已有账户','unchanged-hash',false)");
        assertThatThrownBy(()->initialize("occupied",PASSWORD)).isInstanceOf(IllegalStateException.class).hasMessageContaining("already exists");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE username='occupied'",String.class)).isEqualTo("unchanged-hash");
        assertThat(db.count("SELECT count(*) FROM sys_user_role")).isZero();
    }
    @Test void invalidInitialPasswordDoesNotCreateAnAccount() {
        clear();assertThatThrownBy(()->initialize("cloud_admin","short")).isInstanceOf(IllegalStateException.class).hasMessageNotContaining("short");
        assertThat(db.count("SELECT count(*) FROM sys_user")).isZero();
    }
    @Test void concurrentFirstBootCreatesExactlyOneAdministrator()throws Exception {
        clear();var executor=Executors.newFixedThreadPool(3);
        try {var tasks=new ArrayList<Future<?>>();for(int i=0;i<3;i++){String name="bootstrap_"+i;tasks.add(executor.submit(()->initialize(name,PASSWORD)));}for(var task:tasks)task.get(20,TimeUnit.SECONDS);}
        finally {executor.shutdownNow();}
        assertThat(db.count("SELECT count(*) FROM sys_user")).isEqualTo(1);
        assertThat(db.count("SELECT count(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE r.code='ADMIN'")).isEqualTo(1);
    }
    @Test void attachmentApiKeepsAuthorizationAndMetadataAfterReconnecting()throws Exception {
        byte[] bytes=photo();long id=upload(bytes);
        String key=jdbc.queryForObject("SELECT storage_key FROM stock_attachment WHERE id=?",String.class,id);
        assertThat(key).endsWith(".png");assertThat(STORAGE.uploads.get()).isPositive();
        mvc.perform(get("/api/scraps/attachments/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/scraps/attachments/"+id).header("Authorization","Bearer "+admin)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        var user=create("/api/users",Map.of("username","cloud_student","credentials",Map.of("password",PASSWORD),"profile",Map.of("displayName","隔离测试学生","enabled",true,"roles",List.of("STUDENT"))),admin);
        String student=login("cloud_student",PASSWORD);
        mvc.perform(get("/api/scraps/attachments/"+id).header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/scraps/attachments").file(new MockMultipartFile("file","a.png","image/png",bytes)).header("Authorization","Bearer "+student)).andExpect(status().isForbidden());
        // New storage instance + separate DB connection see the same committed object and identifier.
        var restarted=new SupabaseAttachmentStorage("https://cloud-fixture.supabase.co",STORAGE.bucket,STORAGE.credential,STORAGE.client());
        restarted.verifyPrivateBucket();assertThat(restarted.read(key)).isEqualTo(bytes);
        try(var c=datasource.getConnection();var p=c.prepareStatement("SELECT storage_key FROM stock_attachment WHERE id=?")){p.setLong(1,id);try(var rows=p.executeQuery()){assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo(key);}}
        assertThat(user.path("id").asLong()).isPositive();
    }
    @Test void cloudAdapterRejectsInvalidImagesBeforeWritingStorage()throws Exception {
        int before=STORAGE.uploads.get();
        mvc.perform(multipart("/api/scraps/attachments").file(new MockMultipartFile("file","fake.png","image/png",new byte[]{1,2,3})).header("Authorization","Bearer "+admin)).andExpect(status().isBadRequest());
        assertThat(STORAGE.uploads.get()).isEqualTo(before);assertThat(db.count("SELECT count(*) FROM stock_attachment")).isZero();
    }
    @Test void materialInboundAndScrapPersistWithPrivatePhoto()throws Exception {
        var category=create("/api/material-categories",Map.of("name","云适配隔离分类"),admin);
        var material=create("/api/materials",Map.of("categoryId",category.path("id").asLong(),"name","云适配隔离电阻","unit","个","unitPrice","0.1234","safeStock","0","status","ACTIVE"),admin);
        var inbound=create("/api/inbound-orders",Map.of("idempotencyKey",UUID.randomUUID().toString(),"lines",List.of(Map.of("itemId",material.path("id").asLong(),"quantity","100"))),admin);
        long batch=inbound.path("lines").get(0).path("id").asLong();long photo=upload(photo());
        create("/api/scraps",Map.of("idempotencyKey",UUID.randomUUID().toString(),"batchId",batch,"quantity","3","reason","隔离环境验证","attachmentId",photo),admin);
        assertThat(jdbc.queryForObject("SELECT quantity::text FROM stock_batch WHERE id=?",String.class,batch)).isEqualTo("97.000");
        assertThat(db.count("SELECT count(*) FROM scrap_order WHERE attachment_id=?",photo)).isEqualTo(1);
        mvc.perform(get("/api/materials/"+material.path("id").asLong()).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
    }
}
