package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.MaterialCategory;
import cn.caizhitong.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/material-categories")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service=service; }
    @GetMapping("/tree")
    @PreAuthorize("@moduleAccess.canAccess(authentication, 'materials')")
    public ApiResponse<List<CategoryNode>> tree() { return ApiResponse.ok(service.tree()); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaterialCategory> create(@Valid @RequestBody CategoryInput input) { return ApiResponse.ok(service.create(input)); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaterialCategory> update(@PathVariable Long id,@Valid @RequestBody CategoryInput input) { return ApiResponse.ok(service.update(id,input)); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { service.delete(id); return ApiResponse.ok(null); }
}

