package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
@RestController @RequestMapping("/api/reports") @PreAuthorize("@moduleAccess.canAccess(authentication,'reports')")
public class ReportController {
 private final ReportService reports;private final ReportExporter exporter;
 public ReportController(ReportService reports,ReportExporter exporter){this.reports=reports;this.exporter=exporter;}
 @GetMapping("/users") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")public ApiResponse<?> users(@RequestParam(defaultValue="")String keyword){if(keyword.length()>100)throw new BusinessException(400,"关键词过长");return ApiResponse.ok(reports.users(keyword));}
 @GetMapping("/{type}")public ApiResponse<?> preview(@PathVariable String type,@Valid @ModelAttribute ReportQuery query,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(reports.preview(type,query,user));}
 @GetMapping("/{type}/export/{format}")public ResponseEntity<byte[]> export(@PathVariable String type,@PathVariable String format,@Valid @ModelAttribute ReportQuery query,@AuthenticationPrincipal UserPrincipal user){
  if(!format.equals("xlsx")&&!format.equals("pdf"))throw new BusinessException(400,"仅支持 Excel 或 PDF");var bundle=reports.export(type,query,user);byte[] bytes=format.equals("xlsx")?exporter.excel(bundle):exporter.pdf(bundle);
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(format.equals("xlsx")?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"application/pdf")).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(bundle.meta().get("fileStem")+"."+format,StandardCharsets.UTF_8).build().toString()).body(bytes);
 }
}
