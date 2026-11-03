package cn.caizhitong.entity;
import java.math.BigDecimal;
import java.time.*;
/** Write model; creation is coordinated only by InboundService. */
public class StockBatch {
 public Long id;
 public Long itemId;
 public Long inboundLineId;
 public String batchCode;
 public BigDecimal initialQuantity;
 public BigDecimal quantity;
 public BigDecimal unitPriceSnapshot;
 public OffsetDateTime receivedAt;
 public LocalDate expireDate;
 public String location;
 public String remark;
 public Long createdBy;
}

