package cn.caizhitong;
import cn.caizhitong.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.demo.enabled=false","app.mail.host=127.0.0.1","app.mail.from=lab@example.test","app.mail.recipients=buyer@example.test","app.mail.dispatch-enabled=true","app.mail.dispatch-delay-ms=3600000"})
@ActiveProfiles("test")
class NotificationCommitTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){AuthIntegrationTest.properties(r);}
 @Autowired JdbcTemplate jdbc;@Autowired AlertService alerts;@Autowired PlatformTransactionManager transactions;
 @MockitoBean MailGateway gateway;
 @Test void notificationIsInvisibleUntilCommitAndRollbackNeverDispatches()throws Exception{
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).endsWith("_test");var tx=new TransactionTemplate(transactions);String marker="commit_"+UUID.randomUUID();
  long category=jdbc.queryForObject("INSERT INTO material_category(name) VALUES(?) RETURNING id",Long.class,marker);
  long material=jdbc.queryForObject("INSERT INTO material_item(category_id,name,unit,unit_price,safe_stock) VALUES(?,'提交后邮件验收','个',1,100) RETURNING id",Long.class,category);
  try {
   tx.executeWithoutResult(s->{alerts.reconcile(material);verifyNoInteractions(gateway);s.setRollbackOnly();});Thread.sleep(150);verifyNoInteractions(gateway);assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=?",Long.class,material)).isZero();
   doAnswer(invocation->{assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=?",Long.class,material)).isEqualTo(1);return null;}).when(gateway).send(anyString(),anyString(),anyList());
   tx.executeWithoutResult(s->{alerts.reconcile(material);verifyNoInteractions(gateway);});verify(gateway,timeout(5000).times(1)).send(contains("提交后邮件验收"),contains("安全库存"),eq(List.of("buyer@example.test")));
   long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);while(System.nanoTime()<deadline&&!"SENT".equals(jdbc.queryForObject("SELECT n.status FROM mail_notification n JOIN inventory_alert a ON a.id=n.alert_id WHERE a.material_id=?",String.class,material)))Thread.sleep(20);
   assertThat(jdbc.queryForObject("SELECT n.status FROM mail_notification n JOIN inventory_alert a ON a.id=n.alert_id WHERE a.material_id=?",String.class,material)).isEqualTo("SENT");alerts.reconcile(material);verify(gateway,times(1)).send(anyString(),anyString(),anyList());
  } finally {jdbc.update("DELETE FROM mail_notification WHERE alert_id IN (SELECT id FROM inventory_alert WHERE material_id=?)",material);jdbc.update("DELETE FROM inventory_alert WHERE material_id=?",material);jdbc.update("DELETE FROM material_item WHERE id=?",material);jdbc.update("DELETE FROM material_category WHERE id=?",category);}
 }
}
