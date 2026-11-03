package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record CategoryInput(
    @Positive(message="父级类目 ID 必须为正数") Long parentId,
    @NotBlank(message="请输入分类名称") @Size(max=100, message="分类名称最多 100 个字符") String name,
    @Size(max=2000, message="描述最多 2000 个字符") String description,
    @Min(value=0,message="排序值不能为负数") @Max(value=9999,message="排序值最多 9999") int sortOrder
) {}

