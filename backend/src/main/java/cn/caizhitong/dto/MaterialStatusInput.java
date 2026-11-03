package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record MaterialStatusInput(
    @NotBlank(message="请选择材料状态") @Pattern(regexp="ACTIVE|DISABLED|ARCHIVED",message="材料状态不正确") String status
) {}

