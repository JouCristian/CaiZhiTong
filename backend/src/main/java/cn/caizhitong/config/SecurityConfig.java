package cn.caizhitong.config;
import cn.caizhitong.dto.ApiResponse;
import cn.caizhitong.security.*;
import cn.caizhitong.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecretKeySpec jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] key;
        try { key=Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("JWT_SECRET must be Base64", e); }
        if (key.length < 32) throw new IllegalStateException("JWT_SECRET needs at least 32 random bytes");
        return new SecretKeySpec(key, "HmacSHA256");
    }
    @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean JwtDecoder jwtDecoder(SecretKeySpec key) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator(JwtService.ISSUER),
            jwt -> jwt.getExpiresAt() != null && jwt.getSubject() != null && jwt.getSubject().matches("[1-9][0-9]*")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",config); return source;
    }
    @Bean @org.springframework.core.annotation.Order(2) SecurityFilterChain security(HttpSecurity http, UserService users, ObjectMapper json) throws Exception {
        org.springframework.security.web.AuthenticationEntryPoint unauth=(request,response,ex) -> writeError(response,json,401,"登录状态已失效，请重新登录");
        org.springframework.security.web.access.AccessDeniedHandler denied=(request,response,ex) -> writeError(response,json,403,"当前账户无权访问此页面");
        return http.csrf(csrf -> csrf.disable()).cors(cors -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST,"/api/auth/login","/api/auth/oauth/exchange").permitAll()
                .requestMatchers(HttpMethod.GET,"/actuator/health","/api/public/system","/api/auth/oauth/config").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/auth/me","/api/demo/**").authenticated()
                .requestMatchers("/api/demo/**").denyAll()
                // Deny formal data to demo-only accounts before controller dispatch,
                // including legacy, download and future business endpoints.
                .requestMatchers("/api/**").hasAnyRole("ADMIN","LAB_TECH","STUDENT","TEACHER","PURCHASER")
                .anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(unauth).accessDeniedHandler(denied))
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(unauth).accessDeniedHandler(denied)
                .jwt(j -> j.jwtAuthenticationConverter(jwt -> {
                    try {
                        UserPrincipal p=users.findActive(Long.valueOf(jwt.getSubject()));
                        Number version=jwt.getClaim("av");
                        if ((version==null?0L:version.longValue()) != (p.user().getAuthVersion()==null?0L:p.user().getAuthVersion())) throw new org.springframework.security.authentication.BadCredentialsException("会话已撤销");
                        return UsernamePasswordAuthenticationToken.authenticated(p,jwt,p.authorities());
                    } catch (org.springframework.security.core.AuthenticationException | NumberFormatException ex) {
                        throw new org.springframework.security.oauth2.server.resource.InvalidBearerTokenException("登录状态已失效");
                    }
                }))).build();
    }
    private static void writeError(HttpServletResponse response, ObjectMapper json, int code, String message) throws java.io.IOException {
        response.setStatus(code); response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(),ApiResponse.error(code,message));
    }
}

