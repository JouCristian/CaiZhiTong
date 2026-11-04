package cn.caizhitong;

import cn.caizhitong.service.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.oauth.github.enabled=true","app.oauth.github.client-id=mock-client","app.oauth.github.client-secret=mock-test-only-secret","app.oauth.github.frontend-url=https://caizhitong-eight.vercel.app","app.oauth.github.redirect-uri=https://caizhitong-api.onrender.com/login/oauth2/code/github","app.jwt.ttl-seconds=7200"})
@AutoConfigureMockMvc @ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubOAuthIntegrationTest {
    static final GithubMockProvider PROVIDER;
    static {try{PROVIDER=new GithubMockProvider();}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://127.0.0.1:5432/caizhitong_test");
        if(!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test(\\?.*)?"))throw new IllegalStateException("OAuth tests require an isolated *_test database");
        r.add("spring.datasource.url",()->url);r.add("app.jwt.secret",()->Base64.getEncoder().encodeToString(new byte[48]));
        r.add("app.oauth.github.authorization-uri",()->PROVIDER.origin()+"/authorize");r.add("app.oauth.github.token-uri",()->PROVIDER.origin()+"/token");r.add("app.oauth.github.user-uri",()->PROVIDER.origin()+"/user");
    }
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired GithubIdentityService identities;@Autowired OAuthExchangeService exchanges;@Autowired JwtEncoder jwtEncoder;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired DemoCatalogInitializer demoSeed;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    final Set<String> ids=ConcurrentHashMap.newKeySet();
    final Set<Long> passwordUsers=new HashSet<>();
    String id(){String id="77"+ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L,9_999_999_999_999_999L);ids.add(id);return id;}
    @BeforeAll void guard(){assertThat(db.queryForObject("SELECT current_database()",String.class)).endsWith("_test");}
    @AfterAll void clean(){for(String id:ids){var users=db.queryForList("DELETE FROM oauth_identity WHERE provider='github' AND provider_user_id=? RETURNING user_id",Long.class,id);for(Long user:users)db.update("DELETE FROM sys_user WHERE id=?",user);}for(Long user:passwordUsers)db.update("DELETE FROM sys_user WHERE id=?",user);PROVIDER.close();}
    record Flow(MockHttpSession session,String callback,String verifier){}
    Flow flow(String id,String login,String name,String email)throws Exception {
        PROVIDER.profile.set(new GithubMockProvider.Profile(id,login,name,"https://avatars.githubusercontent.com/u/7000001?v=4",email));
        String verifier="v".repeat(43);
        var start=mvc.perform(get("/oauth2/authorization/github").param("challenge",OAuthExchangeService.challenge(verifier)))
            .andExpect(status().is3xxRedirection()).andReturn();
        URI auth=URI.create(start.getResponse().getRedirectedUrl());var params=GithubMockProvider.query(auth.getRawQuery());
        assertThat(params.get("redirect_uri")).isEqualTo("https://caizhitong-api.onrender.com/login/oauth2/code/github");
        assertThat(params.get("scope")).isEqualTo("read:user");assertThat(params.get("state")).isNotBlank();
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(auth).GET().build(),HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(302);
        return new Flow((MockHttpSession)start.getRequest().getSession(false),response.headers().firstValue("location").orElseThrow(),verifier);
    }
    String callback(Flow flow)throws Exception {var p=GithubMockProvider.query(URI.create(flow.callback).getRawQuery());return mvc.perform(get("/login/oauth2/code/github").params(new org.springframework.util.LinkedMultiValueMap<>(){{p.forEach((k,v)->add(k,v));}}).session(flow.session)).andExpect(status().is3xxRedirection()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getRedirectedUrl();}
    String ticket(Flow flow)throws Exception {String redirect=callback(flow);assertThat(redirect).startsWith("https://caizhitong-eight.vercel.app/oauth/callback#code=").doesNotContain("accessToken");assertThat(URI.create(redirect).getFragment()).matches("code=[A-Za-z0-9_-]{43}");return redirect.substring(redirect.indexOf("#code=")+6);}
    ResultActions exchange(String code,String verifier)throws Exception {return mvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("code",code,"verifier",verifier))));}
    JsonNode login(String id)throws Exception {var f=flow(id,"synthetic-user","Synthetic visitor","synthetic@example.test");return json.readTree(exchange(ticket(f),f.verifier).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");}

    @Test void firstAuthorizationCreatesGuestAndExchangeIsOneTime()throws Exception {
        var f=flow(id(),"synthetic-user","Synthetic visitor","fake@example.test");String code=ticket(f);
        var response=exchange(code,f.verifier).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        var data=json.readTree(response.andReturn().getResponse().getContentAsString()).get("data");
        assertThat(data.get("user").get("roles").toString()).isEqualTo("[\"GUEST\"]");
        assertThat(data.toString()).doesNotContain("fake@example.test","passwordHash","provider_user_id");
        long user=data.at("/user/id").asLong();assertThat(db.queryForObject("SELECT password_login_enabled FROM sys_user WHERE id=?",Boolean.class,user)).isFalse();
        exchange(code,f.verifier).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("SELECT count(*) FROM oauth_login_exchange WHERE code_hash=?",Integer.class,code)).isZero();
    }
    @Test void stableIdSurvivesUsernameAndEmailChangesWithoutTakingOverPasswordAccounts()throws Exception {
        String id=id();var first=login(id);long user=first.at("/user/id").asLong();
        var f=flow(id,"renamed-synthetic","Changed name","changed@example.test");
        var next=json.readTree(exchange(ticket(f),f.verifier).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(next.at("/user/id").asLong()).isEqualTo(user);assertThat(next.at("/user/githubLogin").asText()).isEqualTo("renamed-synthetic");
        assertThat(db.queryForObject("SELECT count(*) FROM oauth_identity WHERE provider_user_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT email FROM sys_user WHERE id=?",String.class,user)).isNull();
    }
    @Test void browserVerifierAndExpiryAreEnforced()throws Exception {
        var f=flow(id(),"synthetic-user",null,null);String code=ticket(f);
        exchange(code,"x".repeat(43)).andExpect(status().isUnauthorized());
        exchange(code,f.verifier).andExpect(status().isOk());
        var another=flow(id(),"synthetic-user",null,null);String expired=ticket(another);
        db.update("UPDATE oauth_login_exchange SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        exchange(expired,another.verifier).andExpect(status().isUnauthorized());
    }
    @Test void forgedStateAndMissingSessionNeverExchangeProviderCode()throws Exception {
        var f=flow(id(),"synthetic-user",null,null);var p=GithubMockProvider.query(URI.create(f.callback).getRawQuery());int before=PROVIDER.tokenCalls.get();
        mvc.perform(get("/login/oauth2/code/github").param("code",p.get("code")).param("state","forged").session(f.session)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("https://caizhitong-eight.vercel.app/oauth/callback#error=state_invalid"));
        mvc.perform(get("/login/oauth2/code/github").param("code",p.get("code")).param("state",p.get("state"))).andExpect(status().is3xxRedirection());
        assertThat(PROVIDER.tokenCalls.get()).isEqualTo(before);
    }
    @Test void callbackReplayAndCancellationDoNotCreateAnotherUser()throws Exception {
        String id=id();var f=flow(id,"synthetic-user",null,null);ticket(f);var p=GithubMockProvider.query(URI.create(f.callback).getRawQuery());
        mvc.perform(get("/login/oauth2/code/github").param("code",p.get("code")).param("state",p.get("state"))).andExpect(redirectedUrl("https://caizhitong-eight.vercel.app/oauth/callback#error=state_invalid"));
        assertThat(db.queryForObject("SELECT count(*) FROM oauth_identity WHERE provider_user_id=?",Integer.class,id)).isEqualTo(1);
        var cancelled=flow(id(),"synthetic-user",null,null);String state=GithubMockProvider.query(URI.create(cancelled.callback).getRawQuery()).get("state");
        mvc.perform(get("/login/oauth2/code/github").param("error","access_denied").param("state",state).session(cancelled.session)).andExpect(redirectedUrl("https://caizhitong-eight.vercel.app/oauth/callback#error=cancelled"));
    }
    @Test void disabledAccountCannotLoginOrReuseExistingJwt()throws Exception {
        String id=id();var data=login(id);long user=data.at("/user/id").asLong();String token=data.get("accessToken").asText();
        db.update("UPDATE sys_user SET enabled=false,auth_version=auth_version+1 WHERE id=?",user);
        var f=flow(id,"synthetic-user",null,null);assertThat(callback(f)).endsWith("#error=disabled");
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->identities.login(id,"synthetic-user",null,null)).isInstanceOf(DisabledException.class);
    }
    @Test void pendingExchangeIsRevokedByPasswordResetOrDisable()throws Exception {
        String id=id();var f=flow(id,"synthetic-user",null,null);String code=ticket(f);
        db.update("UPDATE sys_user SET auth_version=auth_version+1 WHERE id=(SELECT user_id FROM oauth_identity WHERE provider_user_id=?)",id);
        exchange(code,f.verifier).andExpect(status().isUnauthorized());
    }
    @Test void concurrentRegistrationHasOneIdentityAndNeverResetsGrantedRoles()throws Exception {
        String id=id();var pool=Executors.newFixedThreadPool(4);
        try{
            var tasks=new ArrayList<Future<Long>>();for(int i=0;i<4;i++)tasks.add(pool.submit(()->identities.login(id,"parallel-user","Synthetic",null).user().getId()));
            var users=new HashSet<Long>();for(var task:tasks)users.add(task.get(20,TimeUnit.SECONDS));assertThat(users).hasSize(1);long user=users.iterator().next();
            db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='TEACHER'",user);
            var again=identities.login(id,"parallel-renamed",null,null);assertThat(again.roles()).containsExactly("GUEST","TEACHER");
        }finally{pool.shutdownNow();}
    }
    @Test void defaultGuestReadsOnlyDemoAndCannotWriteOrReadFormalModules()throws Exception {
        var data=login(id());String token=data.get("accessToken").asText();
        for(String path:List.of("/api/demo/materials","/api/demo/stocks","/api/demo/dashboard"))mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        for(String path:List.of("/api/materials","/api/stocks","/api/workspace/dashboard","/api/users","/api/settings","/api/issues","/api/returns","/api/stock-transactions","/api/scraps","/api/scraps/1","/api/scraps/attachments/1","/api/reports/funding"))mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        var writes=Map.of(
            "/api/material-categories",Map.of("name","Synthetic forbidden"),
            "/api/materials",Map.of("categoryId",999,"name","Synthetic forbidden","unit","piece","unitPrice","1","safeStock","0","status","ACTIVE"),
            "/api/inbound-orders",Map.of("idempotencyKey",UUID.randomUUID(),"lines",List.of(Map.of("itemId",999,"quantity","1"))),
            "/api/issues",Map.of("idempotencyKey",UUID.randomUUID(),"projectNo","SYNTHETIC","usage","Synthetic forbidden","lines",List.of(Map.of("batchId",999,"quantity","1"))),
            "/api/scraps",Map.of("idempotencyKey",UUID.randomUUID(),"batchId",999,"quantity","1","reason","Synthetic forbidden"));
        for(var write:writes.entrySet())mvc.perform(post(write.getKey()).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(write.getValue()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/demo/dashboard").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.scope").value("SYNTHETIC_DEMO")).andExpect(jsonPath("$.data.readOnly").value(true));
        mvc.perform(get("/api/workspace/overview").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void expiredJwtIsRejectedAndStatelessApiDoesNotCreateSession()throws Exception {
        var data=login(id());long user=data.at("/user/id").asLong();Instant now=Instant.now();
        String expired=jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("caizhitong").subject(""+user).issuedAt(now.minusSeconds(200)).expiresAt(now.minusSeconds(100)).build())).getTokenValue();
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        var response=mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+data.get("accessToken").asText())).andExpect(status().isOk()).andReturn();assertThat(response.getRequest().getSession(false)).isNull();
    }
    @Test void malformedExchangeIsRejectedWithoutReturningSecrets()throws Exception {
        exchange("wrong","wrong").andExpect(status().isBadRequest());
        var response=mvc.perform(get("/api/auth/oauth/config")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(response).contains("true").doesNotContain("secret","mock-client");
    }
    @Test void usernameAndEmailCollisionNeverLinksToExistingAdministrator()throws Exception {
        String id=id(),name="collision-"+id,password="synthetic-password-only-2026";
        Long admin=db.queryForObject("INSERT INTO sys_user(username,display_name,email,password_hash) VALUES(?,?,?,?) RETURNING id",Long.class,name,"Synthetic admin","collision@example.test",passwords.encode(password));passwordUsers.add(admin);
        db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='ADMIN'",admin);
        var f=flow(id,name,"Synthetic visitor","collision@example.test");
        var visitor=json.readTree(exchange(ticket(f),f.verifier).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(visitor.at("/user/id").asLong()).isNotEqualTo(admin);assertThat(visitor.at("/user/roles").toString()).isEqualTo("[\"GUEST\"]");
        var old=json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(old.at("/data/user/id").asLong()).isEqualTo(admin);
    }
    @Test void simultaneousExchangeSucceedsExactlyOnce()throws Exception {
        var f=flow(id(),"synthetic-user",null,null);String code=ticket(f);var pool=Executors.newFixedThreadPool(2);
        try{
            var a=pool.submit(()->exchange(code,f.verifier).andReturn().getResponse().getStatus());var b=pool.submit(()->exchange(code,f.verifier).andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,401);
        }finally{pool.shutdownNow();}
    }
    @Test void reverseProxyHeadersCannotChangeRegisteredCallback()throws Exception {
        var result=mvc.perform(get("/oauth2/authorization/github").param("challenge","x".repeat(43)).header("X-Forwarded-Host","evil.example").header("X-Forwarded-Proto","http"))
            .andExpect(status().is3xxRedirection()).andReturn();
        var p=GithubMockProvider.query(URI.create(result.getResponse().getRedirectedUrl()).getRawQuery());
        assertThat(p.get("redirect_uri")).isEqualTo("https://caizhitong-api.onrender.com/login/oauth2/code/github");
    }
    @Test void missingBrowserChallengeCannotStartAuthorization()throws Exception {
        var result=mvc.perform(get("/oauth2/authorization/github")).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).isNull();assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400);
    }

    @Test void allMappedFormalReadEndpointsDenyDemoOnlyAccountsBeforeControllerDispatch()throws Exception {
        String token=login(id()).get("accessToken").asText(); int checked=0;
        for(var mapping:mappings.getHandlerMethods().keySet()) {
            if(!mapping.getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.GET))continue;
            for(String pattern:mapping.getPatternValues()) {
                if(!pattern.startsWith("/api/")||pattern.startsWith("/api/demo/")||pattern.equals("/api/auth/me")||pattern.equals("/api/public/system")||pattern.equals("/api/auth/oauth/config"))continue;
                String path=pattern.replaceAll("\\{[^}]+\\}","1");
                mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());checked++;
            }
        }
        assertThat(checked).isGreaterThan(30);
    }
    @Test void formalSentinelAndDemoRecordsCoexistWithoutCrossReadsOrWrites()throws Exception {
        var guest=login(id()); String token=guest.get("accessToken").asText();
        String sentinel="FORMAL-PRIVATE-SENTINEL-"+UUID.randomUUID();
        long category=db.queryForObject("INSERT INTO material_category(name) VALUES(?) RETURNING id",Long.class,sentinel);
        long material=db.queryForObject("INSERT INTO material_item(category_id,name,unit,unit_price) VALUES(?,?,'piece',999.1234) RETURNING id",Long.class,category,sentinel);
        try {
            String demo=mvc.perform(get("/api/demo/materials?size=100").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(25)).andReturn().getResponse().getContentAsString();
            assertThat(demo).doesNotContain(sentinel,"999.1234","passwordHash","operatorName");
            mvc.perform(get("/api/demo/materials").param("keyword",sentinel).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(0));
            mvc.perform(get("/api/materials/"+material).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/stocks/"+material+"/batches").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            for(String verb:List.of("POST","PUT","PATCH","DELETE")) {
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(verb),"/api/materials/"+material).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
                mvc.perform(request(org.springframework.http.HttpMethod.valueOf(verb),"/api/demo/materials/1").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            }
            var business=login(id());long user=business.at("/user/id").asLong();
            db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='ADMIN'",user);
            mvc.perform(get("/api/materials/"+material).header("Authorization","Bearer "+business.get("accessToken").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value(sentinel));
            assertThat(db.queryForObject("SELECT name FROM material_item WHERE id=?",String.class,material)).isEqualTo(sentinel);
        } finally {db.update("DELETE FROM material_item WHERE id=?",material);db.update("DELETE FROM material_category WHERE id=?",category);}
    }
    @Test void demoSearchPagingBatchesAndMoneyUseOnlySyntheticTables()throws Exception {
        String token=login(id()).get("accessToken").asText();
        mvc.perform(get("/api/demo/materials?page=2&size=10").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.records.length()").value(10)).andExpect(jsonPath("$.data.records[0].id").value(11)).andExpect(jsonPath("$.data.total").value(25));
        mvc.perform(get("/api/demo/stocks?stockStatus=ZERO").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(5));
        mvc.perform(get("/api/demo/materials?keyword=DEMO-001").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/demo/materials?categoryId=3").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(5));
        mvc.perform(get("/api/demo/materials?keyword=%25").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/demo/materials/1").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.batches.length()").value(2)).andExpect(jsonPath("$.data.unitPrice").value("0.1000")).andExpect(jsonPath("$.data.name").value("示例·金属膜电阻 10kΩ"));
        mvc.perform(get("/api/demo/batches/11").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.events.length()").value(2));
        var d=json.readTree(mvc.perform(get("/api/demo/dashboard").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");
        assertThat(d.at("/metrics/materials").asInt()).isEqualTo(25);assertThat(d.at("/metrics/low").asInt()).isEqualTo(5);assertThat(d.at("/metrics/zero").asInt()).isEqualTo(5);
        for(var value:d.get("values")) {
            var expected=db.queryForObject("SELECT SUM(b.quantity*b.unit_price) FROM demo_stock_batch b JOIN demo_material m ON m.id=b.material_id WHERE m.currency=?",java.math.BigDecimal.class,value.get("currency").asText());
            assertThat(new java.math.BigDecimal(value.get("value").asText())).isEqualByComparingTo(expected);
        }
        for(String path:List.of("/api/demo/materials?page=0","/api/demo/stocks?size=101","/api/demo/stocks?stockStatus=BAD"))mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/demo/batches/999999").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/demo/dashboard")).andExpect(status().isUnauthorized());
    }
    @Test void demoInitializerIsIdempotentAndHasNoForeignKeysToFormalData()throws Exception {
        var before=db.queryForList("SELECT * FROM demo_stock_batch ORDER BY id");
        demoSeed.run(null);demoSeed.run(null);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM demo_material",Integer.class)).isEqualTo(25);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM demo_stock_batch",Integer.class)).isEqualTo(50);
        assertThat(db.queryForList("SELECT * FROM demo_stock_batch ORDER BY id")).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM pg_constraint WHERE contype='f' AND conrelid IN ('demo_material'::regclass,'demo_stock_batch'::regclass,'demo_category'::regclass,'demo_stock_event'::regclass) AND confrelid NOT IN ('demo_material'::regclass,'demo_stock_batch'::regclass,'demo_category'::regclass,'demo_stock_event'::regclass)",Integer.class)).isZero();
    }
    @Test void administratorCanGrantAndRevokeBusinessRolesOnSameGuestJwt()throws Exception {
        var admin=login(id());long administrator=admin.at("/user/id").asLong();
        db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='ADMIN'",administrator);
        var guest=login(id());long user=guest.at("/user/id").asLong();String token=guest.get("accessToken").asText();
        for(var role:List.of("STUDENT","GUEST")) {
            mvc.perform(put("/api/users/"+user).header("Authorization","Bearer "+admin.get("accessToken").asText()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("displayName","Synthetic visitor","enabled",true,"roles",List.of(role))))).andExpect(status().isOk());
            mvc.perform(get("/api/materials").header("Authorization","Bearer "+token)).andExpect(status().is(role.equals("STUDENT")?200:403));
            mvc.perform(get("/api/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
            mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.data.roles[0]").value(role));
        }
    }
}
