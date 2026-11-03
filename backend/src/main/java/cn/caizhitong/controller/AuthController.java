package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.*;
import cn.caizhitong.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final JwtService tokens;
    public AuthController(UserService users, JwtService tokens) { this.users=users; this.tokens=tokens; }
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest input) {
        UserPrincipal p=users.login(input.username().trim(),input.password());
        var token=tokens.issue(p);
        return ApiResponse.ok(new LoginResponse(token.getTokenValue(),"Bearer",token.getExpiresAt(),users.view(p)));
    }
    @GetMapping("/me")
    public ApiResponse<UserView> me(@AuthenticationPrincipal UserPrincipal user) { return ApiResponse.ok(users.view(user)); }
}

