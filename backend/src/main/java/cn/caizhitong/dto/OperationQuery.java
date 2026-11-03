package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
public record OperationQuery(@Min(1) @Max(1000000) Integer page,@Min(1) @Max(100) Integer size,
 @Size(max=100) String keyword,@Size(max=100) String user,
 @Pattern(regexp="ACTIVE|ACKNOWLEDGED|RESOLVED") String status,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
 public int p(){return page==null?1:page;} public int s(){return size==null?20:size;}public long offset(){return (long)(p()-1)*s();}
 @AssertTrue(message="开始日期不能晚于结束日期")public boolean isDatesValid(){return from==null||to==null||!from.isAfter(to);}
 public static String pattern(String s){return s==null||s.isBlank()?null:"%"+s.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
}
