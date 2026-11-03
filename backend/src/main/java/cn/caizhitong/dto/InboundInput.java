package cn.caizhitong.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
public record InboundInput(
 @NotNull(message="缺少入库请求标识") UUID idempotencyKey,
 @PastOrPresent(message="入库时间不能晚于当前时间") OffsetDateTime receivedAt,
 @Size(max=2000,message="入库备注最多 2000 字") String remark,
 @NotEmpty(message="至少添加一条入库明细") @Size(max=50,message="每单最多 50 条明细") List<@NotNull(message="明细不能为空") @Valid Line> lines
) {
 public record Line(
  @NotNull(message="请选择材料") @Positive(message="材料 ID 必须为正数") Long itemId,
  @NotNull(message="请输入入库数量") @DecimalMin(value="0.001",message="入库数量须大于零且至少为 0.001")
  @Digits(integer=15,fraction=3,message="数量最多 15 位整数、3 位小数") BigDecimal quantity,
  @DecimalMin(value="0",message="单价不能为负数") @Digits(integer=10,fraction=4,message="单价最多 10 位整数、4 位小数") BigDecimal unitPrice,
  @Pattern(regexp="[A-Z]{3}",message="币种格式不正确") String currency,
  @Size(max=20,message="单位最多 20 字") String unit,
  @Size(max=100,message="库位最多 100 字") String location,
  LocalDate expireDate,
  @Size(max=2000,message="明细备注最多 2000 字") String remark
 ) {}
}

