package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.*;
import cn.caizhitong.config.InventoryJobs;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
@RestController
public class LifecycleController {
 private final StockLifecycleService flows;private final AttachmentService files;private final AlertService alerts;private final NotificationStore notifications;private final InventoryJobs.Runner jobs;
 public LifecycleController(StockLifecycleService flows,AttachmentService files,AlertService alerts,NotificationStore notifications,InventoryJobs.Runner jobs){this.flows=flows;this.files=files;this.alerts=alerts;this.notifications=notifications;this.jobs=jobs;}
 @PostMapping("/api/returns") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> createReturn(@Valid @RequestBody ReturnInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(flows.createReturn(input,user));}
 @GetMapping("/api/returns") @PreAuthorize("@moduleAccess.canAccess(authentication,'returns')")public ApiResponse<?> returns(@Valid @ModelAttribute OperationQuery q,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(flows.list(q,user,false));}
 @GetMapping("/api/returns/{id}") @PreAuthorize("@moduleAccess.canAccess(authentication,'returns')")public ApiResponse<?> returnDetail(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(flows.returnDetail(id,user));}
 @PostMapping("/api/scraps") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> scrap(@Valid @RequestBody ScrapInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(flows.createScrap(input,user));}
 @GetMapping("/api/scraps") @PreAuthorize("@moduleAccess.canAccess(authentication,'inventory')")public ApiResponse<?> scraps(@Valid @ModelAttribute OperationQuery q,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(flows.list(q,user,true));}
 @GetMapping("/api/scraps/{id}") @PreAuthorize("@moduleAccess.canAccess(authentication,'inventory')")public ApiResponse<?> scrapDetail(@PathVariable Long id){return ApiResponse.ok(flows.scrapDetail(id));}
 @PostMapping("/api/scraps/attachments") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> upload(@RequestParam("file")MultipartFile file,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(files.upload(file,user));}
 @GetMapping("/api/scraps/attachments/{id}") @PreAuthorize("@moduleAccess.canAccess(authentication,'inventory')")public ResponseEntity<byte[]> photo(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){var meta=files.metadata(id,user);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(meta.get("media_type").toString())).body(files.read(meta));}
 @GetMapping("/api/alerts") @PreAuthorize("@moduleAccess.canAccess(authentication,'alerts')")public ApiResponse<?> alerts(@Valid @ModelAttribute OperationQuery q){return ApiResponse.ok(alerts.list(q));}
 @GetMapping("/api/alerts/summary") @PreAuthorize("@moduleAccess.canAccess(authentication,'alerts')")public ApiResponse<?> summary(){return ApiResponse.ok(alerts.summary());}
 @GetMapping("/api/alerts/{id}") @PreAuthorize("@moduleAccess.canAccess(authentication,'alerts')")public ApiResponse<?> alert(@PathVariable Long id){return ApiResponse.ok(alerts.detail(id));}
 @PostMapping("/api/alerts/{id}/acknowledge") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH','PURCHASER')")public ApiResponse<?> ack(@PathVariable Long id,@Valid @RequestBody AlertAckInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(alerts.acknowledge(id,input,user));}
 @PostMapping("/api/alerts/rescan") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> rescan(){return ApiResponse.ok(Map.of("scanned",jobs.rescan()));}
 @PostMapping("/api/alerts/{id}/mail-retry") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> retry(@PathVariable Long id){return ApiResponse.ok(notifications.retry(id));}
}
