package cn.caizhitong.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
@TableName("material_item")
public class MaterialItem {
    @TableId(type=IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String materialCode;
    private String name;
    private String brand;
    private String model;
    private String spec;
    private String unit;
    private BigDecimal unitPrice;
    private String currency;
    private BigDecimal safeStock;
    private BigDecimal monthlyIssueLimit;
    private String status;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @TableField(exist=false)
    private String categoryPath;
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { this.categoryId=value; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String value) { this.materialCode=value; }
    public String getName() { return name; }
    public void setName(String value) { this.name=value; }
    public String getBrand() { return brand; }
    public void setBrand(String value) { this.brand=value; }
    public String getModel() { return model; }
    public void setModel(String value) { this.model=value; }
    public String getSpec() { return spec; }
    public void setSpec(String value) { this.spec=value; }
    public String getUnit() { return unit; }
    public void setUnit(String value) { this.unit=value; }
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal value) { this.unitPrice=value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency=value; }
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    public BigDecimal getSafeStock() { return safeStock; }
    public void setSafeStock(BigDecimal value) { this.safeStock=value; }
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    public BigDecimal getMonthlyIssueLimit() { return monthlyIssueLimit; }
    public void setMonthlyIssueLimit(BigDecimal value) { this.monthlyIssueLimit=value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status=value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description=value; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime value) { this.createdAt=value; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime value) { this.updatedAt=value; }
    public String getCategoryPath() { return categoryPath; }
    public void setCategoryPath(String value) { this.categoryPath=value; }
}

