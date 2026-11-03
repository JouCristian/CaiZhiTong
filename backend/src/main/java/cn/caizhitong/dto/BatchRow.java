package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
public class BatchRow {
 public Long id;
 public Long itemId;
 public String materialStatus;
 public String batchCode;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal initialQuantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal quantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal unitPriceSnapshot;
 public String currency;
 public String itemCode;
 public String itemName;
 public String spec;
 public String unit;
 public OffsetDateTime receivedAt;
 public LocalDate expireDate;
 public String location;
 public String remark;
 public Long createdBy;
 public String operatorName;
 public Long orderId;
 public String orderNo;
 public Integer lineNo;
 public OffsetDateTime createdAt;
 public String qrContent;
}

