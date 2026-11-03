package cn.caizhitong.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;
public record StockSummary(long materialCount,long stockedMaterialCount,long lowStockCount,List<Money> values) {
 public record Money(String currency,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal amount) {}
}

