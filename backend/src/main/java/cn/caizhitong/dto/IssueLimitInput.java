package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record IssueLimitInput(
 @DecimalMin(value="0.001",message="限额须大于零，留空表示不限")
 @Digits(integer=15,fraction=3,message="限额最多 15 位整数、3 位小数") BigDecimal monthlyIssueLimit
) {}
