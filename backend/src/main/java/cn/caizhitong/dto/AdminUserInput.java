package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import java.util.Set;
public record AdminUserInput(
 @NotBlank @Size(max=64) String displayName,
 @Email(message="邮箱格式不正确") @Size(max=254) String email,
 @NotEmpty(message="至少选择一个角色") @Size(max=6) Set<@Pattern(regexp="GUEST|STUDENT|LAB_TECH|TEACHER|PURCHASER|ADMIN",message="角色编码不正确") String> roles,
 @NotNull Boolean enabled) {}
