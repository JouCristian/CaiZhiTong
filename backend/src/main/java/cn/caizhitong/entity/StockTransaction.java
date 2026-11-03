package cn.caizhitong.entity;
import java.math.BigDecimal;
import java.time.*;
/** Write model; creation is coordinated only by InboundService. */
public class StockTransaction {
 public Long id;
 public Long batchId;
 public BigDecimal qty;
 public BigDecimal beforeQuantity;
 public BigDecimal afterQuantity;
 public Long userId;
 public Long inboundOrderId;
 public String referenceNo;
 public String remark;
}

