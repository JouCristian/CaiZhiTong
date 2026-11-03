package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record PasswordInput(@NotBlank @Size(min=12,max=72,message="密码须为 12–72 个字符，且 UTF-8 不超过 72 字节") String password){
 @com.fasterxml.jackson.annotation.JsonIgnore @AssertTrue(message="密码 UTF-8 长度不能超过 72 字节")
 public boolean isBytesValid(){return password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72;}
 @Override public String toString(){return "PasswordInput[REDACTED]";}
}
