package cn.caizhitong.config;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.service.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.event.*;
import java.util.concurrent.Executor;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Configuration @EnableScheduling
public class InventoryJobs {
 @Bean("notificationExecutor")public ThreadPoolTaskExecutor executor(){var executor=new ThreadPoolTaskExecutor();executor.setCorePoolSize(1);executor.setMaxPoolSize(2);executor.setQueueCapacity(100);executor.setThreadNamePrefix("inventory-mail-");return executor;}
 @org.springframework.stereotype.Component
 public static class Runner {
  private static final Logger log=LoggerFactory.getLogger(Runner.class);
  private final OperationsMapper db;private final AlertService alerts;private final NotificationWorker worker;private final MailSettings mail;private final Executor executor;private final boolean scan;
  public Runner(OperationsMapper db,AlertService alerts,NotificationWorker worker,MailSettings mail,@Qualifier("notificationExecutor")Executor executor,@Value("${app.alerts.scan-enabled:true}")boolean scan){this.db=db;this.alerts=alerts;this.worker=worker;this.mail=mail;this.executor=executor;this.scan=scan;}
  public int rescan(){var ids=db.rows("SELECT id FROM material_item ORDER BY id");for(var row:ids)alerts.reconcile(id(row,"id"));return ids.size();}
  @EventListener(ApplicationReadyEvent.class)public void startup(){if(scan)rescan();}
  @Scheduled(fixedDelayString="${app.alerts.scan-delay-ms:60000}")public void scan(){if(scan)try{rescan();}catch(Exception e){log.warn("Inventory alert scan failed: {}",e.getClass().getSimpleName());}}
  @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)public void committed(AlertService.NewNotification event){enqueue(event.id());}
  private void enqueue(Long id){if(!mail.dispatch)return;try{executor.execute(()->{try{worker.deliver(id);}catch(Exception e){log.warn("Mail notification {} remains traceable: {}",id,e.getClass().getSimpleName());}});}catch(Exception e){log.warn("Mail notification {} queued for next dispatch",id);}}
  @Scheduled(fixedDelayString="${app.mail.dispatch-delay-ms:15000}")public void pending(){if(mail.dispatch)for(var row:db.rows("SELECT id FROM mail_notification WHERE status='PENDING' ORDER BY id LIMIT 50"))enqueue(id(row,"id"));}
 }
}
