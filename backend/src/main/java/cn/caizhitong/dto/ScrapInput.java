package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;
public record ScrapInput(@NotNull UUID idempotencyKey,@NotNull @Positive Long batchId,
 @NotNull @DecimalMin(value="0.001",message="报废数量须大于零") @Digits(integer=15,fraction=3)BigDecimal quantity,
 @NotBlank(message="请填写报废原因") @Size(max=1000)String reason,@Size(max=2000)String remark,@Positive Long attachmentId){}
