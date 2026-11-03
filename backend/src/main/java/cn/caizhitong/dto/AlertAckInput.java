package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record AlertAckInput(@NotBlank(message="请填写处理备注") @Size(max=2000)String note){}
