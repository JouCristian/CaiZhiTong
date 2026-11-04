package cn.caizhitong.controller;
import cn.caizhitong.dto.*;
import cn.caizhitong.service.DemoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/demo")
public class DemoController {
    private final DemoService service;
    public DemoController(DemoService service){this.service=service;}
    @GetMapping("/dashboard") public ApiResponse<Map<String,Object>> dashboard(){return ApiResponse.ok(service.dashboard());}
    @GetMapping("/categories") public ApiResponse<List<Map<String,Object>>> categories(){return ApiResponse.ok(service.categories());}
    @GetMapping({"/materials","/stocks"}) public ApiResponse<PageResult<Map<String,Object>>> list(@Valid @ModelAttribute DemoQuery q){return ApiResponse.ok(service.materials(q));}
    @GetMapping("/materials/{id}") public ApiResponse<Map<String,Object>> material(@PathVariable long id){return ApiResponse.ok(service.detail(id));}
    @GetMapping("/batches/{id}") public ApiResponse<Map<String,Object>> batch(@PathVariable long id){return ApiResponse.ok(service.batch(id));}
}
