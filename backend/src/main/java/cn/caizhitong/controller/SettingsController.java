package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.SystemSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class SettingsController {
 private final SystemSettingsService settings;public SettingsController(SystemSettingsService settings){this.settings=settings;}
 @GetMapping("/api/public/system")public ResponseEntity<?> info(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(settings.publicInfo()));}
 @GetMapping("/api/settings") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> detail(){return ApiResponse.ok(settings.detail());}
 @PutMapping("/api/settings") @PreAuthorize("hasRole('ADMIN')")public ApiResponse<?> update(@Valid @RequestBody SettingsInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(settings.update(input,user));}
}
