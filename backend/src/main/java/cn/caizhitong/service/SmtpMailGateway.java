package cn.caizhitong.service;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class SmtpMailGateway implements MailGateway {
 private final MailSettings settings;public SmtpMailGateway(MailSettings settings){this.settings=settings;}
 public void send(String subject,String body,List<String> recipients)throws Exception{
  var sender=new JavaMailSenderImpl();sender.setHost(settings.host);sender.setPort(settings.port);if(!settings.username.isBlank())sender.setUsername(settings.username);if(!settings.password.isBlank())sender.setPassword(settings.password);
  var properties=sender.getJavaMailProperties();properties.setProperty("mail.smtp.auth",String.valueOf(!settings.username.isBlank()));properties.setProperty("mail.smtp.connectiontimeout","3000");properties.setProperty("mail.smtp.timeout","3000");properties.setProperty("mail.smtp.writetimeout","3000");properties.setProperty("mail.smtp.starttls.enable",String.valueOf(settings.starttls&&!settings.ssl));properties.setProperty("mail.smtp.starttls.required",String.valueOf(settings.starttls&&!settings.ssl));properties.setProperty("mail.smtp.ssl.enable",String.valueOf(settings.ssl));
  var message=sender.createMimeMessage();var helper=new MimeMessageHelper(message,false,"UTF-8");helper.setFrom(settings.from);helper.setTo(recipients.toArray(String[]::new));helper.setSubject(subject);helper.setText(body,false);sender.send(message);
 }
}
