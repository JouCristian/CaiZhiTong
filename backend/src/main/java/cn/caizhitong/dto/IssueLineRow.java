package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public class IssueLineRow {
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal returnedQuantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal returnableQuantity;
 public Long id; public Long issueOrderId; public Integer lineNo;
 public Long materialId; public Long batchId; public String materialCode; public String materialName;
 public String spec; public String unit; public String batchCode;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal quantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal unitPriceSnapshot;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal amount;
 public OffsetDateTime createdAt; public String transactionNo;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal beforeQuantity;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal afterQuantity;
}

