package cn.caizhitong;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
@MapperScan("cn.caizhitong.mapper")
public class CaiZhiTongApplication {
    public static void main(String[] args) { SpringApplication.run(CaiZhiTongApplication.class, args); }
}

