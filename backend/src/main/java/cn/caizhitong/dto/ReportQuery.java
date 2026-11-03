package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
public record ReportQuery(@Min(1990) @Max(2200)Integer academicYear,@Pattern(regexp="AUTUMN|SPRING")String term,
 @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,
 @Positive Long categoryId,@Size(max=100)String keyword,@Size(max=100)String course,@Size(max=100)String projectNo,@Positive Long userId,
 @Min(1) @Max(1000000)Integer page,@Min(1) @Max(100)Integer size){
 public int p(){return page==null?1:page;}public int s(){return size==null?20:size;}public long offset(){return (long)(p()-1)*s();}
 @AssertTrue(message="请同时填写有效的统计起止日期")public boolean isDatesValid(){return (from==null&&to==null)||(from!=null&&to!=null&&!from.isAfter(to));}
}
