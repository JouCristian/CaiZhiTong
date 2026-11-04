package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record DemoQuery(
    @Min(1) @Max(1000000) Integer page,
    @Min(1) @Max(100) Integer size,
    @Size(max=100) String keyword,
    @Positive Long categoryId,
    @Pattern(regexp="NORMAL|LOW|ZERO") String stockStatus
) {
    public int pageNumber(){return page==null?1:page;}
    public int pageSize(){return size==null?20:size;}
    public long offset(){return (long)(pageNumber()-1)*pageSize();}
    public String pattern(){return keyword==null||keyword.isBlank()?null:"%"+keyword.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
}
