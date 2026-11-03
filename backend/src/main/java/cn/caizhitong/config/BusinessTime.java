package cn.caizhitong.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import java.time.*;
@Configuration
public class BusinessTime {
 @Bean public Clock businessClock(){return Clock.systemUTC();}
 @Bean public ZoneId businessZone(@Value("${app.business-zone:Asia/Shanghai}") String zone){return ZoneId.of(zone);}
}
