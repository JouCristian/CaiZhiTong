package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
public class StockRow {
 public Long itemId;
 public String materialCode;
 public String name;
 public String brand;
 public String model;
 public Long categoryId;
 public String categoryPath;
 public String unit;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal safeStock;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal quantity;
 public Long batchCount;
 public OffsetDateTime lastReceivedAt;
 public String stockStatus;
 public String materialStatus;
}

