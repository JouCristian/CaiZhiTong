package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/users") @PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
 private final AdminUserService users;public AdminUserController(AdminUserService users){this.users=users;}
 public record EnabledInput(@NotNull Boolean enabled){}
 @GetMapping public ApiResponse<?> list(@Valid @ModelAttribute UserQuery q){return ApiResponse.ok(users.list(q));}
 @GetMapping("/{id}")public ApiResponse<?> detail(@PathVariable long id){return ApiResponse.ok(users.detail(id));}
 @PostMapping public ApiResponse<?> create(@Valid @RequestBody CreateUserInput in,@AuthenticationPrincipal UserPrincipal actor){return ApiResponse.ok(users.create(in,actor));}
 @PutMapping("/{id}")public ApiResponse<?> update(@PathVariable long id,@Valid @RequestBody AdminUserInput in,@AuthenticationPrincipal UserPrincipal actor){return ApiResponse.ok(users.update(id,in,actor));}
 @PatchMapping("/{id}/status")public ApiResponse<?> status(@PathVariable long id,@Valid @RequestBody EnabledInput in,@AuthenticationPrincipal UserPrincipal actor){return ApiResponse.ok(users.status(id,in.enabled(),actor));}
 @PatchMapping("/{id}/password")public ApiResponse<?> password(@PathVariable long id,@Valid @RequestBody PasswordInput in,@AuthenticationPrincipal UserPrincipal actor){return ApiResponse.ok(users.reset(id,in,actor));}
}
