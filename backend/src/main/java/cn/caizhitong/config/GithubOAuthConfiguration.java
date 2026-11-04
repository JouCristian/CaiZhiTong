package cn.caizhitong.config;

import cn.caizhitong.service.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.*;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import java.net.URI;

/** Only the short OAuth handshake uses a session. All /api endpoints remain stateless JWT. */
@Configuration
@ConditionalOnProperty(name="app.oauth.github.enabled",havingValue="true")
public class GithubOAuthConfiguration {
    private static final String FLOW=GithubOAuthConfiguration.class.getName()+".flow";
    private record Flow(String state,String challenge) implements java.io.Serializable {}

    @Bean ClientRegistrationRepository githubClients(
        @Value("${app.oauth.github.client-id:}")String clientId,
        @Value("${app.oauth.github.client-secret:}")String clientSecret,
        @Value("${app.oauth.github.redirect-uri:}")String redirect,
        @Value("${app.oauth.github.authorization-uri:https://github.com/login/oauth/authorize}")String authorize,
        @Value("${app.oauth.github.token-uri:https://github.com/login/oauth/access_token}")String token,
        @Value("${app.oauth.github.user-uri:https://api.github.com/user}")String user,
        @Value("${spring.datasource.url}")String database,
        Environment environment){
        boolean test=environment.acceptsProfiles(Profiles.of("test"));
        if(test && (!authorize.startsWith("https://github.com/") || !user.startsWith("https://api.github.com/")) && !database.matches("jdbc:postgresql://[^/]+/[A-Za-z0-9_]+_test(\\?.*)?"))throw new IllegalStateException("Mock OAuth providers require an isolated *_test database");
        validateOrigin(redirect,true,test);
        if(clientId.isBlank() || clientSecret.isBlank())throw new IllegalStateException("GitHub OAuth backend credentials are required when enabled");
        if(!test && (!authorize.equals("https://github.com/login/oauth/authorize")||!token.equals("https://github.com/login/oauth/access_token")||!user.equals("https://api.github.com/user")))throw new IllegalStateException("Production OAuth endpoints must be GitHub");
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("github")
            .clientId(clientId).clientSecret(clientSecret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri(redirect).scope("read:user")
            .authorizationUri(authorize).tokenUri(token).userInfoUri(user).userNameAttributeName("id").clientName("GitHub").build());
    }
    static void validateOrigin(String value,boolean callback,boolean test){
        try{
            URI u=URI.create(value);
            boolean loopback="127.0.0.1".equals(u.getHost())||"localhost".equals(u.getHost());
            if(u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || !("https".equals(u.getScheme()) || (loopback && "http".equals(u.getScheme()))) || (!callback && !(u.getPath().isEmpty()||u.getPath().equals("/"))) || (callback && !u.getPath().equals("/login/oauth2/code/github")))throw new IllegalArgumentException();
        }catch(IllegalArgumentException e){throw new IllegalStateException("OAuth requires explicit frontend origin and exact backend callback URL");}
    }
    @Bean @Order(1) SecurityFilterChain githubHandshake(HttpSecurity http,ClientRegistrationRepository clients,
        GithubIdentityService identities,OAuthExchangeService exchanges,
        @Value("${app.oauth.github.frontend-url:${app.public-web-url:}}")String frontend) throws Exception {
        validateOrigin(frontend,false,false);String target=frontend.replaceAll("/+$","")+"/oauth/callback";
        var delegate=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        var transport=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        transport.setConnectTimeout(10000);transport.setReadTimeout(20000);
        var tokenClient=new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        tokenClient.setRestClient(org.springframework.web.client.RestClient.builder().requestFactory(transport)
            .messageConverters(c->{c.clear();c.add(new org.springframework.http.converter.FormHttpMessageConverter());c.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());})
            .defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
        var userClient=new org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService();
        var profileHttp=new org.springframework.web.client.RestTemplate();profileHttp.setRequestFactory(transport);
        profileHttp.setErrorHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler());userClient.setRestOperations(profileHttp);
        // PKCE protects the provider code as well as our separate browser-bound exchange.
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        OAuth2AuthorizationRequestResolver resolver=new OAuth2AuthorizationRequestResolver(){
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request){return save(request,delegate.resolve(request));}
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String id){return save(request,delegate.resolve(request,id));}
            private OAuth2AuthorizationRequest save(HttpServletRequest request,OAuth2AuthorizationRequest auth){
                if(auth==null)return null;
                String challenge=request.getParameter("challenge");
                if(challenge==null||!challenge.matches("[A-Za-z0-9_-]{43}"))throw new OAuth2AuthenticationException(new OAuth2Error("invalid_request"));
                request.getSession(true).setAttribute(FLOW,new Flow(auth.getState(),challenge));return auth;
            }
        };
        // Never persist provider access/refresh tokens in a database, browser session, or JWT.
        OAuth2AuthorizedClientRepository discardTokens=new OAuth2AuthorizedClientRepository(){
            public <T extends OAuth2AuthorizedClient>T loadAuthorizedClient(String id,Authentication principal,HttpServletRequest request){return null;}
            public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal,HttpServletRequest request,HttpServletResponse response){}
            public void removeAuthorizedClient(String id,Authentication principal,HttpServletRequest request,HttpServletResponse response){}
        };
        return http.securityMatcher("/oauth2/**","/login/oauth2/**")
            .authorizeHttpRequests(a->a.anyRequest().permitAll())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(c->c.disable())
            .oauth2Login(o->o.authorizationEndpoint(a->a.authorizationRequestResolver(resolver))
                .tokenEndpoint(t->t.accessTokenResponseClient(tokenClient)).userInfoEndpoint(u->u.userService(userClient))
                .authorizedClientRepository(discardTokens)
                .successHandler((request,response,authentication)->{
                    String result;
                    try{
                        HttpSession session=request.getSession(false);
                        Flow flow=session==null?null:(Flow)session.getAttribute(FLOW);
                        if(flow==null || !flow.state().equals(request.getParameter("state")))throw new IllegalArgumentException("Invalid flow");
                        OAuth2User profile=((OAuth2AuthenticationToken)authentication).getPrincipal();
                        Object rawId=profile.getAttribute("id");
                        var user=identities.login(rawId==null?null:rawId.toString(),profile.getAttribute("login"),profile.getAttribute("name"),profile.getAttribute("avatar_url"));
                        result="#code="+exchanges.create(user,flow.challenge());
                    }catch(DisabledException e){result="#error=disabled";}
                    catch(Exception e){result="#error=failed";}
                    finally{invalidate(request);}
                    secureRedirect(response,target+result);
                })
                .failureHandler((request,response,exception)->{
                    invalidate(request);
                    String reason="failed";
                    if(exception instanceof OAuth2AuthenticationException e){
                        String code=e.getError().getErrorCode();
                        if("access_denied".equals(code))reason="cancelled";
                        else if("authorization_request_not_found".equals(code)||"invalid_state_parameter".equals(code))reason="state_invalid";
                    }
                    secureRedirect(response,target+"#error="+reason);
                }))
            .build();
    }
    private static void invalidate(HttpServletRequest request){HttpSession session=request.getSession(false);if(session!=null)session.invalidate();}
    private static void secureRedirect(HttpServletResponse response,String target)throws java.io.IOException{
        response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");response.sendRedirect(target);
    }
}
