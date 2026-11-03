package cn.caizhitong.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class NotificationWorker {
 private final NotificationStore store;private final MailGateway gateway;private final MailSettings settings;private final String origin;
 public NotificationWorker(NotificationStore store,MailGateway gateway,MailSettings settings,@Value("${app.public-web-url:}")String origin){this.store=store;this.gateway=gateway;this.settings=settings;this.origin=origin;}
 @SuppressWarnings("unchecked")public void deliver(Long id){var row=store.claim(id);if(row==null)return;String failure=null;try{gateway.send("材智通低库存提醒 · "+text(row,"material_name"),"材料："+text(row,"material_name")+"\n编码："+text(row,"material_code")+"\n当前库存："+num(row,"trigger_quantity").toPlainString()+" "+text(row,"unit")+"\n安全库存："+num(row,"trigger_safe_stock").toPlainString()+"\n查看预警："+origin+"/alerts?event="+id(row,"event_id"),(List<String>)row.get("targets"));}catch(Exception e){failure=settings.safeFailure(e);}store.finish(id,failure);}
}
