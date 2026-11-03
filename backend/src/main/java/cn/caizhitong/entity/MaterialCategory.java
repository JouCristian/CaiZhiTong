package cn.caizhitong.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
@TableName("material_category")
public class MaterialCategory {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String description;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableField(exist=false)
    private Long materialCount;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long value) { this.parentId=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer value) { this.sortOrder=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt=value; }
    public Long getMaterialCount() { return materialCount; }
    public void setMaterialCount(Long value) { this.materialCount=value; }
}

