package cn.caizhitong;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "app.cors.allowed-origins=http://localhost:5173",
    "app.demo.enabled=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest {
    private static final String PASSWORD="test-only-password-2026";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        String url=System.getenv().getOrDefault("TEST_DB_URL","jdbc:postgresql://localhost:5432/caizhitong_test");
        if (!url.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test(\\?.*)?"))
            throw new IllegalStateException("Tests require a dedicated *_test database before applying migrations");
        registry.add("spring.datasource.url", () -> url);
        registry.add("app.jwt.secret", () -> Base64.getEncoder().encodeToString(new byte[48]));
        registry.add("app.public-web-url", () -> "http://localhost:5173");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll void seed() {
        // Never run tests against the application database.
        String db=jdbc.queryForObject("SELECT current_database()",String.class);
        if (db==null || !db.endsWith("_test")) throw new IllegalStateException("Tests require a dedicated *_test database");
        for (String role: List.of("GUEST","STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN")) {
            String username="test_"+role.toLowerCase(Locale.ROOT);
            SysUser user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,username));
            if (user==null) { user=new SysUser(); user.setUsername(username); user.setDisplayName("测试账户"); user.setPasswordHash(passwords.encode(PASSWORD)); user.setEnabled(true); users.insert(user); }
            users.assignRole(user.getId(),role);
        }
    }
    String login(String role) throws Exception {
        String body=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username","test_"+role.toLowerCase(Locale.ROOT),"password",PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andReturn().getResponse().getContentAsString();
        return json.readTree(body).at("/data/accessToken").asText();
    }
    @Test void healthIsPublicAndPostgresIsConnected() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_role",Integer.class)).isEqualTo(6);
    }
    @Test void anonymousCannotReadIdentity() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
    }
    @ParameterizedTest @ValueSource(strings={"GUEST","STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN"})
    void realLoginAndMeForEveryRole(String role) throws Exception {
        String token=login(role);
        mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0]").value(role))
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings={"GUEST","STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN"})
    void everyModuleIsAuthorizedOnServer(String role) throws Exception {
        String token=login(role);
        Set<String> allowed=switch (role) {
            case "ADMIN" -> Set.of("materials","inventory","inbound","requisitions","returns","alerts","reports","users","settings");
            case "LAB_TECH" -> Set.of("materials","inventory","inbound","requisitions","returns","alerts","reports");
            case "TEACHER" -> Set.of("materials","inventory","requisitions","returns","alerts","reports");
            case "PURCHASER" -> Set.of("materials","inventory","inbound","alerts","reports");
            case "STUDENT" -> Set.of("materials","requisitions","returns","reports");
            default -> Set.of("materials","inventory");
        };
        for (String module: List.of("materials","inventory","inbound","requisitions","returns","alerts","reports","users","settings")) {
            mvc.perform(get("/api/workspace/modules/"+module).header("Authorization","Bearer "+token))
                .andExpect(status().is(allowed.contains(module)?200:403));
        }
    }
    @Test void wrongPasswordAndUnknownUserHaveSameResponse() throws Exception {
        for (String username: List.of("test_admin","does-not-exist")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",username,"password","incorrect"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("用户名或密码错误"));
        }
    }
    @Test void validationAndMalformedJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username","test_admin","password","密".repeat(30)))))
            .andExpect(status().isBadRequest());
    }
    @Test void rejectsTamperedExpiredAndWrongIssuerTokens() throws Exception {
        String valid=login("ADMIN");
        String[] parts=valid.split("\\.");
        String bad=parts[0]+"."+parts[1]+"."+(parts[2].startsWith("a")?"b":"a")+parts[2].substring(1);
        for (String token: List.of(bad,customToken("caizhitong",Instant.now().minusSeconds(5)),customToken("wrong-issuer",Instant.now().plusSeconds(100)))) {
            mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401));
        }
    }
    String customToken(String issuer,Instant expiration) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer(issuer).subject("1").issuedAt(Instant.now().minusSeconds(60)).expiresAt(expiration).build())).getTokenValue();
    }
    @Test void disabledUserCannotLoginOrReuseToken() throws Exception {
        String token=login("STUDENT");
        Long id=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,"test_student")).getId();
        jdbc.update("UPDATE sys_user SET enabled=false WHERE id=?",id);
        try {
            mvc.perform(get("/api/auth/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username","test_student","password",PASSWORD)))).andExpect(status().isUnauthorized());
        } finally { jdbc.update("UPDATE sys_user SET enabled=true WHERE id=?",id); }
    }
    @Test void currentDatabaseRolesOverrideAlreadyIssuedToken() throws Exception {
        String token=login("ADMIN");
        Long id=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,"test_admin")).getId();
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",id);
        try {
            mvc.perform(get("/api/workspace/modules/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        } finally { users.assignRole(id,"ADMIN"); }
    }
    @Test void dashboardContainsNoFabricatedActivity() throws Exception {
        mvc.perform(get("/api/workspace/overview").header("Authorization","Bearer "+login("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.businessAvailable").value(true))
            .andExpect(jsonPath("$.data.metrics.materials").value(0)).andExpect(jsonPath("$.data.activities").isEmpty());
    }
    @Test void unknownAuthenticatedEndpointReturns404() throws Exception {
        mvc.perform(get("/api/unknown").header("Authorization","Bearer "+login("ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void allowedCorsOriginAndRejectedOrigin() throws Exception {
        mvc.perform(options("/api/auth/me").header("Origin","http://localhost:5173").header("Access-Control-Request-Method","GET").header("Access-Control-Request-Headers","Authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"));
        mvc.perform(options("/api/auth/me").header("Origin","https://untrusted.example").header("Access-Control-Request-Method","GET"))
            .andExpect(status().isForbidden());
    }
    @Test void passwordsUseBcrypt() {
        String hash=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,"test_admin")).getPasswordHash();
        assertThat(hash).startsWith("$2"); assertThat(hash).isNotEqualTo(PASSWORD); assertThat(passwords.matches(PASSWORD,hash)).isTrue();
    }
}


