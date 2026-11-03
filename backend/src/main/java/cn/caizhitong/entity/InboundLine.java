package cn.caizhitong.entity;
import java.math.BigDecimal;
import java.time.*;
/** Write model; creation is coordinated only by InboundService. */
public class InboundLine {
 public Long id;
 public Long orderId;
 public Integer lineNo;
 public Long itemId;
 public BigDecimal quantity;
 public BigDecimal unitPriceSnapshot;
 public String currencySnapshot;
 public String itemCodeSnapshot;
 public String itemNameSnapshot;
 public String specSnapshot;
 public String unitSnapshot;
 public String location;
 public LocalDate expireDate;
 public String remark;
}

