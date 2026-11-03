package cn.caizhitong.controller;
import cn.caizhitong.dto.ApiResponse;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log=LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(cn.caizhitong.service.BusinessException.class)
    ResponseEntity<?> business(cn.caizhitong.service.BusinessException e) { return error(e.status(),e.getMessage()); }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(org.springframework.dao.DataIntegrityViolationException e) {
        String detail=String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.contains("uq_category_sibling_name")) return error(409,"同一父级下已存在同名类目");
        if (detail.contains("material_item_material_code_key")) return error(409,"材料编码已存在");
        return error(409,"数据关联已变更或存在重复记录，请刷新后重试");
    }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<?> auth(AuthenticationException e) { return error(401,"用户名或密码错误"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> denied() { return error(403,"当前账户无权访问此页面"); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        return error(400,e.getBindingResult().getFieldErrors().stream().findFirst().map(f -> f.getDefaultMessage()).orElse("请求参数不正确"));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() { return error(400,"请求格式不正确"); }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> notFound() { return error(404,"接口不存在"); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method() { return error(405,"请求方法不支持"); }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> uploadSize(){return error(400,"每张照片不超过 5 MB");}
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        log.error("Unhandled API exception",e); return error(500,"服务暂时不可用，请稍后重试");
    }
    private ResponseEntity<?> error(int code,String message) {
        return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.error(code,message));
    }
}


