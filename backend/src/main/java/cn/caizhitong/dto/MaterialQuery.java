package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record MaterialQuery(
    @Min(value=1,message="页码从 1 开始") @Max(value=1000000,message="页码超出范围") Integer page,
    @Min(value=1,message="每页至少 1 条") @Max(value=100,message="每页最多 100 条") Integer size,
    @Size(max=100,message="关键词最多 100 个字符") String keyword,
    @Positive(message="类目 ID 必须为正数") Long categoryId,
    @Size(max=100,message="品牌最多 100 个字符") String brand,
    @Size(max=100,message="型号最多 100 个字符") String model,
    @Pattern(regexp="ACTIVE|DISABLED|ARCHIVED",message="材料状态不正确") String status,
    @Pattern(regexp="createdAt",message="不支持该排序字段") String sort,
    @Pattern(regexp="asc|desc",message="排序方向只支持 asc 或 desc") String order
) {
    public int getPageNumber() { return page == null ? 1 : page; }
    public int getPageSize() { return size == null ? 20 : size; }
    public long getOffset() { return (long)(getPageNumber()-1)*getPageSize(); }
    public String getKeywordPattern() {
        if (keyword == null || keyword.isBlank()) return null;
        return "%" + keyword.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_") + "%";
    }
}

