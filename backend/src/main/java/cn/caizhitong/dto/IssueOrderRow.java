package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
public class IssueOrderRow {
 public Long id; public String issueNo; public Long userId;
 public String accountName; public String recipientName;
 public String course;public String projectNo; public String usage; public String remark; public String status;
 public String currency;
 @JsonFormat(shape=JsonFormat.Shape.STRING) public BigDecimal totalAmount;
 @com.fasterxml.jackson.annotation.JsonIgnore public String idempotencyKey;
 @com.fasterxml.jackson.annotation.JsonIgnore public String requestHash;
 public LocalDate monthStart; public String businessZone; public OffsetDateTime createdAt;
 public Long lineCount; public String materialNames; public String batchCodes; public String quantitySummary;
}


