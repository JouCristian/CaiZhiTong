package cn.caizhitong.entity;
import java.math.BigDecimal;
import java.time.*;
/** Write model; creation is coordinated only by InboundService. */
public class InboundOrder {
 public Long id;
 public String orderNo;
 public String idempotencyKey;
 public String requestHash;
 public Long createdBy;
 public String remark;
 public OffsetDateTime receivedAt;
}


