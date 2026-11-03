package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
public record IssueQuery(
 @Min(1) @Max(1000000) Integer page, @Min(1) @Max(100) Integer size,
 @Size(max=100) String keyword, @Size(max=100) String material,
 @Size(max=100) String projectNo, @Size(max=100) String user,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to
) {
 public int getPageNumber(){return page==null?1:page;}
 public int getPageSize(){return size==null?20:size;}
 public long getOffset(){return (long)(getPageNumber()-1)*getPageSize();}
 public String getPattern(){return pattern(keyword);}
 public String getMaterialPattern(){return pattern(material);}
 public String getUserPattern(){return pattern(user);}
 private String pattern(String s){return s==null||s.isBlank()?null:"%"+s.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
 @AssertTrue(message="开始日期不能晚于结束日期") public boolean isDateRangeValid(){return from==null||to==null||!from.isAfter(to);}
}
