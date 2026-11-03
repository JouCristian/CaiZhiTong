package cn.caizhitong.dto;
import jakarta.validation.constraints.*;
public record UserQuery(@Min(1) @Max(1000000) Integer page,@Min(1) @Max(100) Integer size,@Size(max=100) String keyword,Boolean enabled){
 public int p(){return page==null?1:page;}public int s(){return size==null?20:size;}public long offset(){return (long)(p()-1)*s();}
}
