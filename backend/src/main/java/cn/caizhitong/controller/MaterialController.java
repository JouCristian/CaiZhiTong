package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.MaterialItem;
import cn.caizhitong.service.MaterialService;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/materials")
@PreAuthorize("@moduleAccess.canAccess(authentication, 'materials')")
public class MaterialController {
    private final MaterialService service;
    public MaterialController(MaterialService service) { this.service=service; }
    @GetMapping public ApiResponse<PageResult<MaterialItem>> query(@Valid @ModelAttribute MaterialQuery query) { return ApiResponse.ok(service.query(query)); }
    @GetMapping("/facets") public ApiResponse<List<MaterialFacet>> facets() { return ApiResponse.ok(service.facets()); }
    @GetMapping("/{id}") public ApiResponse<MaterialItem> detail(@PathVariable Long id) { return ApiResponse.ok(service.detail(id)); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
    public ApiResponse<MaterialItem> create(@Valid @RequestBody MaterialInput input) { return ApiResponse.ok(service.create(input)); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
    public ApiResponse<MaterialItem> update(@PathVariable Long id,@Valid @RequestBody MaterialInput input) { return ApiResponse.ok(service.update(id,input)); }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAnyRole('ADMIN','LAB_TECH')")
    public ApiResponse<MaterialItem> status(@PathVariable Long id,@Valid @RequestBody MaterialStatusInput input,Authentication auth) {
        if ("ARCHIVED".equals(input.status()) && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")))
            throw new AccessDeniedException("只有管理员可以归档材料");
        return ApiResponse.ok(service.status(id,input.status()));
    }
}

