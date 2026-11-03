package cn.caizhitong.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
public record ReturnInput(@NotNull UUID idempotencyKey,@NotNull @Positive Long issueOrderId,@Size(max=2000)String remark,
 @NotEmpty @Size(max=20)List<@NotNull @Valid Line> lines){
 public record Line(@NotNull @Positive Long issueOrderLineId,@NotNull @DecimalMin(value="0.001",message="归还数量须大于零") @Digits(integer=15,fraction=3)BigDecimal quantity){}
}
