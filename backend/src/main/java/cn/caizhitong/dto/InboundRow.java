package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.*;
public class InboundRow {
 public Long id;
 public String orderNo;
 public Long createdBy;
 public String operatorName;
 public String remark;
 public String status;
 public OffsetDateTime receivedAt;
 public OffsetDateTime createdAt;
 public Long lineCount;
}

