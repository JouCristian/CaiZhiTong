package cn.caizhitong.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record CreateUserInput(
 @NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,64}",message="用户名须为 3–64 位字母、数字、点、横线或下划线") String username,
 @NotNull @Valid PasswordInput credentials,
 @NotNull @Valid AdminUserInput profile) {
 @Override public String toString(){return "CreateUserInput[credentials=REDACTED]";}
}
