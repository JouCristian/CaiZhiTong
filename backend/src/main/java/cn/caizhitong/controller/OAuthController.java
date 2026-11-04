package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {
    private final OAuthExchangeService exchange;private final boolean enabled;
    public OAuthController(OAuthExchangeService exchange,@Value("${app.oauth.github.enabled:false}")boolean enabled){this.exchange=exchange;this.enabled=enabled;}
    @GetMapping("/config") public ResponseEntity<ApiResponse<Map<String,Boolean>>> config(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(Map.of("githubEnabled",enabled)));}
    public record ExchangeInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}")String code,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43,128}")String verifier){@Override public String toString(){return "ExchangeInput[REDACTED]";}}
    @PostMapping("/exchange") public ResponseEntity<ApiResponse<LoginResponse>> exchange(@Valid @RequestBody ExchangeInput input){
        if(!enabled)throw new BusinessException(404,"GitHub 登录尚未启用");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA,"no-cache").body(ApiResponse.ok(exchange.exchange(input.code(),input.verifier())));
    }
}
