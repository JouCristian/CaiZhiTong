package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record MaterialInput(
    @NotNull(message="请选择所属类目") @Positive(message="类目 ID 必须为正数") Long categoryId,
    @Size(max=32,message="材料编码最多 32 个字符") String materialCode,
    @NotBlank(message="请输入材料名称") @Size(max=100,message="材料名称最多 100 个字符") String name,
    @Size(max=100,message="品牌最多 100 个字符") String brand,
    @Size(max=100,message="型号最多 100 个字符") String model,
    @Size(max=2000,message="规格参数最多 2000 个字符") String spec,
    @NotBlank(message="请输入单位") @Size(max=20,message="单位最多 20 个字符") String unit,
    @NotNull(message="请输入单价") @DecimalMin(value="0",message="单价不能为负数")
    @Digits(integer=10,fraction=4,message="单价最多 10 位整数、4 位小数") BigDecimal unitPrice,
    @Pattern(regexp="[A-Z]{3}",message="币种须为三位大写字母") String currency,
    @NotNull(message="请输入安全库存") @DecimalMin(value="0",message="安全库存不能为负数")
    @Digits(integer=9,fraction=3,message="安全库存最多 9 位整数、3 位小数") BigDecimal safeStock,
    @Pattern(regexp="ACTIVE|DISABLED",message="表单状态只支持启用或停用，归档请使用专用操作") String status,
    @Size(max=2000,message="备注最多 2000 个字符") String description
) {}

