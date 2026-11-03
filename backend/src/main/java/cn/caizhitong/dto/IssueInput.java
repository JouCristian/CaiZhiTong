package cn.caizhitong.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public record IssueInput(
 @NotNull(message="缺少领用请求标识") UUID idempotencyKey,
 @NotBlank(message="请填写项目编号") @Size(max=100) String projectNo,
 @NotBlank(message="请填写领用用途") @Size(max=500) String usage,
 @Size(max=2000) String remark,
 @NotEmpty(message="至少选择一个批次") @Size(max=20,message="每单最多 20 条明细") List<@NotNull @Valid Line> lines, @Size(max=100,message="课程最多 100 字") String course
) {
 public record Line(
  @NotNull(message="请选择库存批次") @Positive Long batchId,
  @NotNull(message="请填写领用数量") @DecimalMin(value="0.001",message="领用数量必须大于零")
  @Digits(integer=15,fraction=3,message="领用数量最多 15 位整数、3 位小数") BigDecimal quantity
 ) {}
}

