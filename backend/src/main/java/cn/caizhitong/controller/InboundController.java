package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.security.UserPrincipal;
import cn.caizhitong.service.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/inbound-orders")
public class InboundController {
 private final InboundService writes;private final StockService reads;
 public InboundController(InboundService writes,StockService reads){this.writes=writes;this.reads=reads;}
 @GetMapping @PreAuthorize("@moduleAccess.canAccess(authentication, 'inbound')")
 public ApiResponse<PageResult<InboundRow>> list(@Valid @ModelAttribute StockQuery q){return ApiResponse.ok(reads.orders(q));}
 @GetMapping("/{id}") @PreAuthorize("@moduleAccess.canAccess(authentication, 'inbound')")
 public ApiResponse<InboundDetail> detail(@PathVariable Long id){return ApiResponse.ok(reads.order(id));}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
 public ApiResponse<InboundDetail> create(@Valid @RequestBody InboundInput input,@AuthenticationPrincipal UserPrincipal user){return ApiResponse.ok(writes.create(input,user));}
}

