package cn.caizhitong.controller;
import cn.caizhitong.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final cn.caizhitong.service.MaterialService materials;
    private final cn.caizhitong.service.StockService stocks;
    private final cn.caizhitong.service.ReportService reports;
    public WorkspaceController(cn.caizhitong.service.MaterialService materials,cn.caizhitong.service.StockService stocks,cn.caizhitong.service.ReportService reports) { this.materials=materials; this.stocks=stocks; this.reports=reports; }
    @GetMapping("/dashboard") @PreAuthorize("@moduleAccess.canAccess(authentication,'dashboard')")
    public ApiResponse<?> dashboard(@org.springframework.security.core.annotation.AuthenticationPrincipal cn.caizhitong.security.UserPrincipal user){return ApiResponse.ok(reports.dashboard(user));}
    @GetMapping("/overview")
    @PreAuthorize("@moduleAccess.canAccess(authentication, 'dashboard')")
    public ApiResponse<Map<String,Object>> overview() {
        var summary=stocks.summary();
        return ApiResponse.ok(Map.of("businessAvailable",true,"metrics",Map.of("materials",materials.total(),"stock",summary.stockedMaterialCount(),"pending",0,"alerts",summary.lowStockCount()),"activities",stocks.activities()));
    }
    // Access metadata only. No material/inventory business endpoints are implemented in Sprint 01.
    @GetMapping("/modules/{module}")
    @PreAuthorize("@moduleAccess.canAccess(authentication, #module)")
    public ApiResponse<Map<String,String>> module(@PathVariable String module) {
        return ApiResponse.ok(Map.of("module",module,"status",Set.of("materials","inventory","inbound","requisitions","returns","alerts","reports","users","settings").contains(module)?"ready":"planned"));
    }
}


