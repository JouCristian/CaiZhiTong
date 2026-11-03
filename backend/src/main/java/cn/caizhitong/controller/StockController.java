package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.service.StockService;
import cn.caizhitong.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@PreAuthorize("@moduleAccess.canAccess(authentication, 'materials')")
public class StockController {
 private final StockService service;
 public StockController(StockService service){this.service=service;}
 @GetMapping("/api/stocks") public ApiResponse<PageResult<StockRow>> stocks(@Valid @ModelAttribute StockQuery q){return ApiResponse.ok(service.stocks(q));}
 @GetMapping("/api/stocks/summary") public ApiResponse<StockSummary> summary(){return ApiResponse.ok(service.summary());}
 @GetMapping("/api/stocks/{id}/batches") public ApiResponse<ItemBatches> batches(@PathVariable Long id,@Valid @ModelAttribute StockQuery q){return ApiResponse.ok(service.batches(id,q));}
 @GetMapping("/api/stock-batches/{code}") public ApiResponse<BatchRow> batch(@PathVariable String code){return ApiResponse.ok(service.batch(code));}
 @GetMapping(value="/api/stock-batches/{code}/qrcode",produces=MediaType.IMAGE_PNG_VALUE)
 public ResponseEntity<byte[]> qr(@PathVariable String code){
  return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+code.replaceAll("[^A-Za-z0-9-]","")+".png\"").body(service.png(code));
 }
 @GetMapping("/api/stock-transactions") @PreAuthorize("@moduleAccess.canAccess(authentication, 'inventory')")
 public ApiResponse<PageResult<TransactionRow>> transactions(@Valid @ModelAttribute StockQuery q,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(service.transactions(q,user));}
}

