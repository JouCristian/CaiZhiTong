package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
public record IssueQuota(Long materialId,LocalDate monthStart,String businessZone,
 @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal monthlyIssueLimit,
 @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal usedQuantity,
 @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal remainingQuantity) {}
