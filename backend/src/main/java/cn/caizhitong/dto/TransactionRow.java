package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
public class TransactionRow {
 public String referenceType;
 public Long id;
 public String transactionNo;
 public Long itemId;
 public String itemName;
 public String itemCode;
 public String batchCode;
 public String txnType;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal qty;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal beforeQuantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING)
 public BigDecimal afterQuantity;
 public String unit;
 public Long userId;
 public String operatorName;
 public Long orderId;
 public String referenceNo;
 public String projectNo;
 public String usage;
 public String remark;
 public OffsetDateTime createdAt;
}

