package cn.caizhitong.service;
import java.util.List;
public interface MailGateway {void send(String subject,String body,List<String> recipients)throws Exception;}
