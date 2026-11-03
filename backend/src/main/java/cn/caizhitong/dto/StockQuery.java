package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.*;
import java.util.List;
public record StockQuery(
 @Min(value=1,message="页码从 1 开始") @Max(1000000) Integer page,
 @Min(1) @Max(value=100,message="每页最多 100 条") Integer size,
 @Size(max=100,message="关键词最多 100 字") String keyword,
 @Positive Long categoryId,
 @Pattern(regexp="ZERO|LOW|NORMAL",message="库存状态不正确") String stockStatus,
 @Positive Long itemId,
 @Size(max=40) String batchCode,
 @Pattern(regexp="IN|OUT|RETURN|SCRAP",message="流水类型不正确") String txnType,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
 @Size(max=100) List<@Positive Long> materialIds
) {
 public int getPageNumber(){return page==null?1:page;}
 public int getPageSize(){return size==null?20:size;}
 public long getOffset(){return (long)(getPageNumber()-1)*getPageSize();}
 public String getPattern(){return keyword==null||keyword.isBlank()?null:"%"+keyword.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
 public OffsetDateTime getFromTime(){return from==null?null:from.atStartOfDay().atOffset(ZoneOffset.ofHours(8));}
 public OffsetDateTime getToTime(){return to==null?null:to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(8));}
 @AssertTrue(message="开始日期不能晚于结束日期")
 public boolean isDateRangeValid(){return from==null||to==null||!from.isAfter(to);}
}

