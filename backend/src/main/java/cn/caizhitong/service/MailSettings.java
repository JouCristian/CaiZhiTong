package cn.caizhitong.service;
import cn.caizhitong.mapper.OperationsMapper;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class MailSettings {
 public final String host,username,password,from,configuredRecipients;public final int port;public final boolean starttls,ssl,dispatch;
 public MailSettings(@Value("${app.mail.host:}")String host,@Value("${app.mail.port:587}")int port,@Value("${app.mail.username:}")String username,@Value("${app.mail.password:}")String password,@Value("${app.mail.from:}")String from,@Value("${app.mail.recipients:}")String recipients,@Value("${app.mail.starttls:true}")boolean tls,@Value("${app.mail.ssl:false}")boolean ssl,@Value("${app.mail.dispatch-enabled:true}")boolean dispatch){this.host=host;this.port=port;this.username=username;this.password=password;this.from=from;this.configuredRecipients=recipients;this.starttls=tls;this.ssl=ssl;this.dispatch=dispatch;}
 public List<String> recipients(OperationsMapper db){var values=new ArrayList<String>();values.addAll(Arrays.asList(configuredRecipients.split("[,;]")));var config=db.one("SELECT notification_recipients FROM app_settings WHERE id=1");if(config!=null)values.addAll(Arrays.asList(config.get("notification_recipients").toString().split(",")));db.rows("SELECT DISTINCT u.email FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.code='PURCHASER' AND u.enabled AND u.email IS NOT NULL").forEach(r->values.add(r.get("email").toString()));return values.stream().map(String::trim).filter(this::valid).distinct().toList();}
 public boolean valid(String email){try{var a=new InternetAddress(email,true);a.validate();return email.contains("@")&&!email.contains("\n")&&!email.contains("\r");}catch(Exception e){return false;}}
 public String missing(List<String> recipients){if(host.isBlank())return "未配置 SMTP 主机";if(!valid(from))return "未配置有效发件人";if(recipients.isEmpty())return "未配置有效采购员邮箱或收件人";return null;}
 public String safeFailure(Exception e){String text=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());if(!password.isBlank())text=text.replace(password,"***");return text.substring(0,Math.min(text.length(),900));}
}
