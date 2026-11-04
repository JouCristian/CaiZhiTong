package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class SystemSettingsService {
 private final OperationsMapper db;private final Clock clock;private final MailSettings mail;private final String fallbackZone,version;
 public SystemSettingsService(OperationsMapper db,Clock clock,MailSettings mail,@Value("${app.business-zone:Asia/Shanghai}")String fallbackZone,@Value("${app.version:0.7.0}")String version){this.db=db;this.clock=clock;this.mail=mail;this.fallbackZone=fallbackZone;this.version=version;}
 private Map<String,Object> row(){return db.one("SELECT * FROM app_settings WHERE id=1");}
 private ZoneId zone(Map<String,Object> row){return ZoneId.of(row.get("business_zone")==null?fallbackZone:text(row,"business_zone"));}
 public ZoneId zone(){return zone(row());}
 // Writes hold a shared settings lock before material / batch locks. Settings
 // changes wait until these transactions commit before checking history.
 public ZoneId lockZone(){return zone(db.one("SELECT * FROM app_settings WHERE id=1 FOR SHARE"));}
 public Map<String,Object> publicInfo(){var r=row();return Map.of("systemName",text(r,"system_name"),"systemDescription",text(r,"system_description"),"businessZone",zone(r).getId(),"version",version);}
 private boolean zoneLocked(){return db.count("SELECT count(*) FROM inbound_order")>0||db.count("SELECT count(*) FROM issue_order")>0;}
 public record Period(int year,String term,LocalDate from,LocalDate to,boolean configured){}
 public Period period(Integer requestedYear,String requestedTerm){
  var r=row();var today=LocalDate.now(clock.withZone(zone(r)));int computed=today.getMonthValue()>=9?today.getYear():today.getYear()-1;
  int configuredYear=r.get("academic_year")==null?computed:((Number)r.get("academic_year")).intValue();int year=requestedYear==null?configuredYear:requestedYear;boolean configured=r.get("academic_year")!=null&&year==configuredYear;
  String term=requestedTerm==null?(today.getMonthValue()>=9||today.getMonthValue()==1?"AUTUMN":"SPRING"):requestedTerm;
  if(requestedTerm==null&&configured){var a=((java.sql.Date)r.get("autumn_start")).toLocalDate();var b=((java.sql.Date)r.get("autumn_end")).toLocalDate();var c=((java.sql.Date)r.get("spring_start")).toLocalDate();var d=((java.sql.Date)r.get("spring_end")).toLocalDate();if(!today.isBefore(a)&&!today.isAfter(b))term="AUTUMN";else if(!today.isBefore(c)&&!today.isAfter(d))term="SPRING";}
  var from=configured?((java.sql.Date)r.get(term.equals("AUTUMN")?"autumn_start":"spring_start")).toLocalDate():term.equals("AUTUMN")?LocalDate.of(year,9,1):LocalDate.of(year+1,2,1);
  var to=configured?((java.sql.Date)r.get(term.equals("AUTUMN")?"autumn_end":"spring_end")).toLocalDate():term.equals("AUTUMN")?LocalDate.of(year+1,1,31):LocalDate.of(year+1,8,31);
  return new Period(year,term,from,to,configured);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public Map<String,Object> detail(){
  var r=new LinkedHashMap<>(row());r.put("business_zone",zone(r).getId());int year=period(null,null).year();r.put("academic_year",year);
  if(r.get("autumn_start")==null){r.put("autumn_start",LocalDate.of(year,9,1));r.put("autumn_end",LocalDate.of(year+1,1,31));r.put("spring_start",LocalDate.of(year+1,2,1));r.put("spring_end",LocalDate.of(year+1,8,31));}
  String recipients=text(r,"notification_recipients");r.put("notification_recipients",recipients.isBlank()?List.of():Arrays.asList(recipients.split(",")));r.put("version",version);r.put("zone_locked",zoneLocked());r.put("time_zones",ZoneId.getAvailableZoneIds().stream().sorted().toList());
  var targets=mail.recipients(db);r.put("smtp_configured",!mail.host.isBlank()&&mail.valid(mail.from));r.put("notification_dispatch_enabled",mail.dispatch);r.put("effective_recipients",targets);String missing=mail.missing(targets);r.put("notification_status",missing==null?(mail.dispatch?"READY":"DISABLED"):"NOT_CONFIGURED");if(missing!=null)r.put("notification_reason",missing);
  r.put("recent_notifications",db.rows("SELECT n.status,n.attempts,n.failure_reason,n.created_at,n.sent_at,a.material_name,a.material_code FROM mail_notification n JOIN inventory_alert a ON a.id=n.alert_id ORDER BY n.id DESC LIMIT 5"));return api(r);
 }
 @Transactional public Map<String,Object> update(SettingsInput in,UserPrincipal user){
  var r=db.one("SELECT * FROM app_settings WHERE id=1 FOR UPDATE");if(db.count("SELECT count(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.id=? AND u.enabled AND r.code='ADMIN'",user.user().getId())!=1)throw new BusinessException(403,"管理员权限已变更");
  if(((Number)r.get("revision")).longValue()!=in.revision())throw new BusinessException(409,"配置已被其他管理员更新，请重新加载后保存");
  ZoneId next;try{next=ZoneId.of(in.businessZone());}catch(Exception e){throw new BusinessException(400,"业务时区不是有效的 IANA 时区");}
  if(!zone(r).equals(next)&&zoneLocked())throw new BusinessException(409,"已有库存业务记录，业务时区已锁定，避免历史统计和月度额度漂移");
  String targets=String.join(",",in.notificationRecipients().stream().map(String::trim).distinct().toList());if(targets.length()>4000)throw new BusinessException(400,"通知收件人总长度不能超过 4000 字符");
  db.update("UPDATE app_settings SET system_name=?,system_description=?,business_zone=?,academic_year=?,autumn_start=?,autumn_end=?,spring_start=?,spring_end=?,notification_recipients=?,revision=revision+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=1",in.systemName().trim(),in.systemDescription().trim(),next.getId(),in.academicYear(),in.autumnStart(),in.autumnEnd(),in.springStart(),in.springEnd(),targets,user.user().getId());return detail();
 }
}
