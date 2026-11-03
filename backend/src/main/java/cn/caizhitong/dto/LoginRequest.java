package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record LoginRequest(
    @NotBlank(message="请输入用户名") @Size(max=64, message="用户名最多 64 个字符") String username,
    @NotBlank(message="请输入密码") @Size(max=72, message="密码最多 72 个字符") String password) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    @AssertTrue(message="密码 UTF-8 长度不能超过 72 字节")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}

