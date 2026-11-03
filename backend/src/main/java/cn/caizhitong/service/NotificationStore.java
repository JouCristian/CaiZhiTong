package cn.caizhitong.service;
import cn.caizhitong.mapper.OperationsMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class NotificationStore {
 private final OperationsMapper db;private final MailSettings settings;private final ApplicationEventPublisher events;
 public NotificationStore(OperationsMapper db,MailSettings settings,ApplicationEventPublisher events){this.db=db;this.settings=settings;this.events=events;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)public Map<String,Object> claim(Long id){
  var row=db.one("SELECT n.*,a.material_name,a.material_code,a.trigger_quantity,a.trigger_safe_stock,a.unit,a.id AS event_id FROM mail_notification n JOIN inventory_alert a ON a.id=n.alert_id WHERE n.id=? AND n.status='PENDING' FOR UPDATE OF n SKIP LOCKED",id);if(row==null)return null;
  var recipients=settings.recipients(db);String missing=settings.missing(recipients);if(missing!=null){db.update("UPDATE mail_notification SET status='NOT_CONFIGURED',failure_reason=? WHERE id=?",missing,id);return null;}
  db.update("UPDATE mail_notification SET status='SENDING',attempts=attempts+1,recipients=?,attempted_at=CURRENT_TIMESTAMP,failure_reason=NULL WHERE id=?",String.join(",",recipients),id);row.put("targets",recipients);return row;
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)public void finish(Long id,String failure){db.update("UPDATE mail_notification SET status=?,failure_reason=?,sent_at=CASE WHEN CAST(? AS TEXT) IS NULL THEN CURRENT_TIMESTAMP ELSE NULL END WHERE id=? AND status='SENDING'",failure==null?"SENT":"FAILED",failure,failure,id);}
 @Transactional public Map<String,Object> retry(Long alertId){var row=db.one("SELECT * FROM mail_notification WHERE alert_id=? FOR UPDATE",alertId);if(row==null)throw new BusinessException(404,"通知记录不存在");if(!Set.of("FAILED","NOT_CONFIGURED").contains(text(row,"status")))throw new BusinessException(409,"仅失败或未配置的通知可以重试；发送中结果需先核对");db.update("UPDATE mail_notification SET status='PENDING',failure_reason=NULL WHERE id=?",id(row,"id"));events.publishEvent(new AlertService.NewNotification(id(row,"id")));return Map.of("mailId",id(row,"id"),"status","PENDING");}
}

