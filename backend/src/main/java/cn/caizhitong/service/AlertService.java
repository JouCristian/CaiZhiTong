package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.*;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class AlertService {
 public record NewNotification(Long id){}
 private final OperationsMapper db;private final MaterialMapper materials;private final MailSettings mail;private final ApplicationEventPublisher events;private final Clock clock;private final SystemSettingsService settings;
 public AlertService(OperationsMapper db,MaterialMapper materials,MailSettings mail,ApplicationEventPublisher events,Clock clock,SystemSettingsService settings){this.db=db;this.materials=materials;this.mail=mail;this.events=events;this.clock=clock;this.settings=settings;}
 @Transactional public void reconcile(Long id){
  var zone=settings.zone();
  if(materials.lockItem(id)==null)return;var item=db.one("SELECT m.*,COALESCE((SELECT sum(quantity) FROM stock_batch WHERE item_id=m.id),0) AS quantity FROM material_item m WHERE m.id=?",id);var now=OffsetDateTime.ofInstant(clock.instant(),zone);var open=db.one("SELECT * FROM inventory_alert WHERE material_id=? AND status IN ('ACTIVE','ACKNOWLEDGED') FOR UPDATE",id);
  var quantity=num(item,"quantity");var safe=num(item,"safe_stock");boolean low=safe.signum()>0&&quantity.compareTo(safe)<0;
  if(!low){if(open!=null)db.update("UPDATE inventory_alert SET status='RESOLVED',current_quantity=?,current_safe_stock=?,updated_at=?,resolved_at=? WHERE id=?",quantity,safe,now,now,id(open,"id"));return;}
  String level=quantity.signum()==0?"CRITICAL":"LOW";
  if(open!=null){db.update("UPDATE inventory_alert SET current_quantity=?,current_safe_stock=?,level=?,updated_at=? WHERE id=?",quantity,safe,level,now,id(open,"id"));return;}
  var alert=db.one("INSERT INTO inventory_alert(material_id,status,level,trigger_quantity,trigger_safe_stock,current_quantity,current_safe_stock,material_name,material_code,unit,created_at,updated_at) VALUES(?,'ACTIVE',?,?,?,?,?,?,?,?,?,?) RETURNING id",id,level,quantity,safe,quantity,safe,text(item,"name"),text(item,"material_code"),text(item,"unit"),now,now);
  var recipients=mail.recipients(db);String missing=mail.missing(recipients);
  var notification=db.one("INSERT INTO mail_notification(alert_id,status,recipients,failure_reason,created_at) VALUES(?,?,?,?,?) RETURNING id",id(alert,"id"),missing==null?"PENDING":"NOT_CONFIGURED",String.join(",",recipients),missing,now);
  if(missing==null)events.publishEvent(new NewNotification(id(notification,"id")));
 }
 @Transactional public Map<String,Object> acknowledge(Long id,AlertAckInput input,UserPrincipal user){
  var zone=settings.zone();
  var row=db.one("SELECT * FROM inventory_alert WHERE id=? FOR UPDATE",id);if(row==null)throw new BusinessException(404,"预警不存在");if("RESOLVED".equals(row.get("status")))throw new BusinessException(409,"库存已恢复，预警已解除");var now=OffsetDateTime.ofInstant(clock.instant(),zone);
  db.update("UPDATE inventory_alert SET status='ACKNOWLEDGED',acknowledged_by=?,handling_note=?,acknowledged_at=?,updated_at=? WHERE id=?",user.user().getId(),input.note().trim(),now,now,id);return detail(id);
 }
 private static final String SELECT="SELECT a.*,u.display_name AS acknowledged_name,n.id AS mail_id,n.status AS mail_status,n.attempts AS mail_attempts,n.failure_reason,n.recipients,n.sent_at FROM inventory_alert a LEFT JOIN sys_user u ON u.id=a.acknowledged_by LEFT JOIN mail_notification n ON n.alert_id=a.id";
 public Map<String,Object> detail(Long id){var row=db.one(SELECT+" WHERE a.id=?",id);if(row==null)throw new BusinessException(404,"预警不存在");return api(row);}
 public Map<String,Object> summary(){return api(db.one("SELECT count(*) FILTER(WHERE status<>'RESOLVED') AS low_material_count,count(*) FILTER(WHERE status='ACTIVE') AS pending_count,count(*) FILTER(WHERE status='ACKNOWLEDGED') AS acknowledged_count FROM inventory_alert"));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public PageResult<Map<String,Object>> list(OperationQuery q){
  var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();if(q.status()!=null){where.append(" AND a.status=?");args.add(q.status());}if(q.keyword()!=null&&!q.keyword().isBlank()){where.append(" AND (a.material_name ILIKE ? ESCAPE '\\' OR a.material_code ILIKE ? ESCAPE '\\')");args.add(OperationQuery.pattern(q.keyword()));args.add(OperationQuery.pattern(q.keyword()));}
  long count=db.count("SELECT count(*) FROM inventory_alert a"+where,args.toArray());args.add(q.s());args.add(q.offset());return new PageResult<>(db.rows(SELECT+where+" ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",args.toArray()).stream().map(OperationsMapper::api).toList(),count,q.p(),q.s());
 }
}
