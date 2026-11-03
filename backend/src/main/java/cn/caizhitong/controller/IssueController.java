package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.MaterialItem;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@PreAuthorize("@moduleAccess.canAccess(authentication, 'requisitions')")
public class IssueController {
 private final IssueService service;private final IssuePdfService pdf;
 public IssueController(IssueService service,IssuePdfService pdf){this.service=service;this.pdf=pdf;}
 @PostMapping("/api/issues") public ApiResponse<IssueDetail> create(@Valid @RequestBody IssueInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(service.create(input,user));}
 @GetMapping("/api/issues/my") public ApiResponse<PageResult<IssueOrderRow>> my(@Valid @ModelAttribute IssueQuery q,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(service.query(q,user.user().getId()));}
 @GetMapping("/api/issues") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
 public ApiResponse<PageResult<IssueOrderRow>> all(@Valid @ModelAttribute IssueQuery q){return ApiResponse.ok(service.query(q,null));}
 @GetMapping("/api/issues/{id}") public ApiResponse<IssueDetail> detail(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(service.detail(id,user));}
 @GetMapping(value="/api/issues/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
 public ResponseEntity<byte[]> pdf(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){
  var detail=service.detail(id,user);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+detail.order().issueNo+".pdf\"").body(pdf.generate(detail));
 }
 @GetMapping("/api/materials/{id}/issue-quota") public ApiResponse<IssueQuota> quota(@PathVariable Long id,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(service.quota(id,user.user().getId()));}
 @GetMapping("/api/materials/{id}/issue-batches") public ApiResponse<PageResult<BatchRow>> available(@PathVariable Long id,@Valid @ModelAttribute StockQuery q){return ApiResponse.ok(service.available(id,q));}
 @PatchMapping("/api/materials/{id}/issue-limit") @PreAuthorize("hasRole('ADMIN')")
 public ApiResponse<MaterialItem> limit(@PathVariable Long id,@Valid @RequestBody IssueLimitInput input){return ApiResponse.ok(service.setLimit(id,input));}
}
