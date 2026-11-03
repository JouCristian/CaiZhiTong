package cn.caizhitong;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.cors.allowed-origins=http://localhost:5173","app.demo.enabled=false"})
@ActiveProfiles("test") @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministrationIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){AuthIntegrationTest.properties(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired PasswordEncoder passwords;
 @MockitoBean(name="businessClock") Clock clock;
 final List<Long> createdUsers=new ArrayList<>();
 final AtomicReference<Instant> now=new AtomicReference<>();
 final Map<String,String> tokens=new HashMap<>();final Map<String,Long> userIds=new HashMap<>();final List<Long> items=new ArrayList<>();Long category;
 @BeforeAll void seed()throws Exception {
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).endsWith("_test");
  for(String role:List.of("ADMIN","LAB_TECH","STUDENT","TEACHER","PURCHASER","GUEST","OTHER")){
   String name=role.equals("OTHER")?"test_issue_student2":"test_"+role.toLowerCase(Locale.ROOT);
   var user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,name));
   if(user==null){user=new SysUser();user.setUsername(name);user.setDisplayName("领用测试学生");user.setEnabled(true);user.setPasswordHash(passwords.encode("test-only-password-2026"));users.insert(user);}
   users.assignRole(user.getId(),role.equals("OTHER")?"STUDENT":role);userIds.put(role,user.getId());
   var result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password","test-only-password-2026")))).andExpect(status().isOk()).andReturn();
   tokens.put(role,json.readTree(result.getResponse().getContentAsByteArray()).at("/data/accessToken").asText());
  }
 }
 void clear(){jdbc.execute("TRUNCATE mail_notification,inventory_alert,return_order_line,return_order,scrap_order,stock_attachment,stock_transaction,issue_order_line,issue_order,issue_monthly_usage,stock_batch,inbound_order_line,inbound_order RESTART IDENTITY");}
 @BeforeEach void setup(){
  resetSettings();createdUsers.clear();clear();items.clear();now.set(Instant.parse("2026-11-03T08:00:00Z"));when(clock.instant()).thenAnswer(i->now.get());when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
  category=jdbc.queryForObject("INSERT INTO material_category(name) VALUES(?) RETURNING id",Long.class,"issue_test_"+UUID.randomUUID());
 }
 @AfterEach void cleanup(){
  jdbc.execute("DROP TRIGGER IF EXISTS test_fail_issue ON stock_transaction");jdbc.execute("DROP FUNCTION IF EXISTS test_fail_issue()");clear();
  resetSettings();for(Long id:createdUsers)jdbc.update("DELETE FROM sys_user WHERE id=?",id);
  for(Long id:items)jdbc.update("DELETE FROM material_item WHERE id=?",id);jdbc.update("DELETE FROM material_category WHERE id=?",category);
 }
 ResultActions call(String method,String path,String role,Object body)throws Exception{
  var builder=switch(method){case "POST"->post(path);case "PATCH"->patch(path);case "PUT"->put(path);default->get(java.net.URI.create(path));};
  builder.header("Authorization","Bearer "+tokens.get(role));if(body!=null)builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));return mvc.perform(builder);
 }
 JsonNode data(ResultActions r)throws Exception{return json.readTree(r.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("data");}
 long item()throws Exception{var row=data(call("POST","/api/materials","ADMIN",Map.of("categoryId",category,"name","精密电阻领用验收","spec","10kΩ，0.25W","unit","个","unitPrice","0.1234","safeStock","10","status","ACTIVE")));long id=row.get("id").asLong();items.add(id);return id;}
 JsonNode batch(long item,String qty)throws Exception{return data(call("POST","/api/inbound-orders","ADMIN",Map.of("idempotencyKey",UUID.randomUUID(),"lines",List.of(Map.of("itemId",item,"quantity",qty))))).at("/lines/0");}
 Map<String,Object> line(long batch,String qty){return new LinkedHashMap<>(Map.of("batchId",batch,"quantity",qty));}
 @SafeVarargs final Map<String,Object> input(Map<String,Object>... lines){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID(),"projectNo","P-2026-04","usage","实验电路搭建","remark","真实数据库事务验收","lines",List.of(lines)));}
 JsonNode issue(Map<String,Object> input,String role)throws Exception{return data(call("POST","/api/issues",role,input));}
 String balance(long batch){return jdbc.queryForObject("SELECT quantity::text FROM stock_batch WHERE id=?",String.class,batch);}
 long count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class);}
 void limit(long item,String value)throws Exception{data(call("PATCH","/api/materials/"+item+"/issue-limit","ADMIN",Collections.singletonMap("monthlyIssueLimit",value)));}
 void resetSettings(){jdbc.update("UPDATE app_settings SET system_name='材智通',system_description='智慧材料库数字化管理系统',business_zone=NULL,academic_year=NULL,autumn_start=NULL,autumn_end=NULL,spring_start=NULL,spring_end=NULL,notification_recipients='',revision=0,updated_by=NULL WHERE id=1");}
 Map<String,Object> profile(String role,boolean enabled){return new LinkedHashMap<>(Map.of("displayName","用户管理验收","email","buyer@example.test","roles",List.of(role),"enabled",enabled));}
 Map<String,Object> userInput(String role){return new LinkedHashMap<>(Map.of("username","admin_test_"+UUID.randomUUID().toString().replace("-",""),"credentials",Map.of("password","test-only-password-2026"),"profile",profile(role,true)));}
 JsonNode createUser(String role)throws Exception{var u=data(call("POST","/api/users","ADMIN",userInput(role)));createdUsers.add(u.get("id").asLong());return u;}
 String token(JsonNode u,String password)throws Exception{return data(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",u.get("username").asText(),"password",password))))).get("accessToken").asText();}
 ResultActions bearer(String method,String path,String token,Object body)throws Exception{var r=switch(method){case"POST"->post(path);case"PUT"->put(path);case"PATCH"->patch(path);default->get(java.net.URI.create(path));};r.header("Authorization","Bearer "+token);if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));return mvc.perform(r);}
 Map<String,Object> config()throws Exception{return json.convertValue(data(call("GET","/api/settings","ADMIN",null)),new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String,Object>>(){});}
 @Test void newUserPersistsLogsInAndNeverReturnsCredentials()throws Exception{
  var u=createUser("STUDENT");long id=u.get("id").asLong();String t=token(u,"test-only-password-2026");var me=data(bearer("GET","/api/auth/me",t,null));assertThat(me.get("roles").toString()).contains("STUDENT");
  var detail=data(call("GET","/api/users/"+id,"ADMIN",null));assertThat(detail.get("lastLoginAt").asText()).isNotBlank();assertThat(detail.toString()).doesNotContain("password","passwordHash","password_hash","authVersion","test-only-password");
  assertThat(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,id)).startsWith("$2");assertThat(data(call("GET","/api/users?size=1&keyword="+u.get("username").asText(),"ADMIN",null)).get("total").asInt()).isEqualTo(1);
 }
 @Test void duplicateUsernameRejectedWithoutAdditionalUser()throws Exception{var body=userInput("STUDENT");var u=data(call("POST","/api/users","ADMIN",body));createdUsers.add(u.get("id").asLong());call("POST","/api/users","ADMIN",body).andExpect(status().isConflict());}
 @ParameterizedTest @ValueSource(strings={"email","roles","displayName","password","username"})void invalidUserFieldsRejected(String field)throws Exception{
  var body=userInput("STUDENT");@SuppressWarnings("unchecked")var p=new LinkedHashMap<>((Map<String,Object>)body.get("profile"));switch(field){case"email"->p.put("email","invalid-email");case"roles"->p.put("roles",List.of("SUPERUSER"));case"displayName"->p.put("displayName"," ");case"password"->body.put("credentials",Map.of("password","密".repeat(30)));case"username"->body.put("username","x/");}body.put("profile",p);call("POST","/api/users","ADMIN",body).andExpect(status().isBadRequest());
 }
 @Test void disabledOldJwtStaysRevokedAfterReenable()throws Exception{var u=createUser("STUDENT");String t=token(u,"test-only-password-2026");String path="/api/users/"+u.get("id").asLong()+"/status";data(call("PATCH",path,"ADMIN",Map.of("enabled",false)));bearer("GET","/api/auth/me",t,null).andExpect(status().isUnauthorized());data(call("PATCH",path,"ADMIN",Map.of("enabled",true)));bearer("GET","/api/auth/me",t,null).andExpect(status().isUnauthorized());bearer("GET","/api/auth/me",token(u,"test-only-password-2026"),null).andExpect(status().isOk());}
 @Test void revokedRoleCannotSurviveInOldJwt()throws Exception{var u=createUser("ADMIN");String t=token(u,"test-only-password-2026");bearer("GET","/api/users",t,null).andExpect(status().isOk());data(call("PUT","/api/users/"+u.get("id").asLong(),"ADMIN",profile("STUDENT",true)));bearer("GET","/api/users",t,null).andExpect(status().isForbidden());assertThat(data(bearer("GET","/api/auth/me",t,null)).get("roles").toString()).isEqualTo("[\"STUDENT\"]");}
 @Test void resetPasswordRevokesTokenAndChangesBcrypt()throws Exception{var u=createUser("STUDENT");String t=token(u,"test-only-password-2026");data(call("PATCH","/api/users/"+u.get("id").asLong()+"/password","ADMIN",Map.of("password","replacement-password-2026")));bearer("GET","/api/auth/me",t,null).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",u.get("username").asText(),"password","test-only-password-2026")))).andExpect(status().isUnauthorized());bearer("GET","/api/auth/me",token(u,"replacement-password-2026"),null).andExpect(status().isOk());assertThat(passwords.matches("replacement-password-2026",jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=?",String.class,u.get("id").asLong()))).isTrue();}
 @ParameterizedTest @ValueSource(strings={"STUDENT","LAB_TECH","TEACHER","PURCHASER","GUEST"})void onlyAdminManagesUsersAndSettings(String role)throws Exception{var u=createUser("STUDENT");call("GET","/api/users",role,null).andExpect(status().isForbidden());call("POST","/api/users",role,userInput("STUDENT")).andExpect(status().isForbidden());call("PUT","/api/users/"+u.get("id").asLong(),role,profile("ADMIN",true)).andExpect(status().isForbidden());call("PATCH","/api/users/"+u.get("id").asLong()+"/password",role,Map.of("password","replacement-password-2026")).andExpect(status().isForbidden());call("PUT","/api/settings",role,config()).andExpect(status().isForbidden());call("GET","/api/settings",role,null).andExpect(status().isForbidden());}
 @Test void currentAdminCannotDisableOrDemoteSelf()throws Exception{long id=userIds.get("ADMIN");call("PATCH","/api/users/"+id+"/status","ADMIN",Map.of("enabled",false)).andExpect(status().isConflict());call("PUT","/api/users/"+id,"ADMIN",profile("STUDENT",true)).andExpect(status().isConflict());}
 @Test void competingAdministratorsCannotRemoveLastEffectiveAdministrator()throws Exception{
  var a=createUser("ADMIN");var b=createUser("ADMIN");String at=token(a,"test-only-password-2026"),bt=token(b,"test-only-password-2026");long ai=a.get("id").asLong(),bi=b.get("id").asLong();var otherIds=jdbc.queryForList("SELECT id FROM sys_user WHERE enabled AND id NOT IN (?,?) AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=sys_user.id AND r.code='ADMIN')",Long.class,ai,bi);otherIds.forEach(id->jdbc.update("UPDATE sys_user SET enabled=false WHERE id=?",id));var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->bearer("PATCH","/api/users/"+bi+"/status",at,Map.of("enabled",false)).andReturn().getResponse().getStatus());var y=pool.submit(()->bearer("PATCH","/api/users/"+ai+"/status",bt,Map.of("enabled",false)).andReturn().getResponse().getStatus());var statuses=List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS));assertThat(statuses.stream().filter(c->c==200).count()).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM sys_user u WHERE enabled AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='ADMIN')",Long.class)).isEqualTo(1);}finally{pool.shutdownNow();otherIds.forEach(id->jdbc.update("UPDATE sys_user SET enabled=true WHERE id=?",id));}
 }
 @Test void userEditsAndStatusFiltersPersist()throws Exception{var u=createUser("STUDENT");var p=profile("PURCHASER",false);p.put("displayName","已更新采购员");p.put("email","purchaser@example.test");data(call("PUT","/api/users/"+u.get("id").asLong(),"ADMIN",p));var saved=data(call("GET","/api/users/"+u.get("id").asLong(),"ADMIN",null));assertThat(saved.get("displayName").asText()).isEqualTo("已更新采购员");assertThat(saved.get("roles").toString()).contains("PURCHASER");assertThat(data(call("GET","/api/users?enabled=false&keyword="+u.get("username").asText(),"ADMIN",null)).get("total").asInt()).isEqualTo(1);}
 @Test void settingsPersistAndReportsUseConfiguredSemesterWithoutBreakingCustomDates()throws Exception{
  var c=config();c.put("systemName","材料中心验收");c.put("academicYear",2025);c.put("autumnStart","2025-10-01");c.put("autumnEnd","2026-01-20");c.put("springStart","2026-03-01");c.put("springEnd","2026-07-10");c.put("notificationRecipients",List.of("buyer@example.test"));data(call("PUT","/api/settings","ADMIN",c));assertThat(data(call("GET","/api/settings","ADMIN",null)).get("systemName").asText()).isEqualTo("材料中心验收");var r=data(call("GET","/api/reports/semester","ADMIN",null));assertThat(r.at("/meta/from").asText()).isEqualTo("2025-10-01");assertThat(r.at("/meta/to").asText()).isEqualTo("2026-01-20");var spring=data(call("GET","/api/reports/semester?term=SPRING","ADMIN",null));assertThat(spring.at("/meta/from").asText()).isEqualTo("2026-03-01");var custom=data(call("GET","/api/reports/semester?from=2024-01-01&to=2024-01-31","ADMIN",null));assertThat(custom.at("/meta/from").asText()).isEqualTo("2024-01-01");assertThat(data(call("GET","/api/settings","ADMIN",null)).get("effectiveRecipients").toString()).contains("buyer@example.test");
 }
 @Test void staleSettingsAreRejectedAndPublicInfoContainsNoSecrets()throws Exception{var c=config();data(call("PUT","/api/settings","ADMIN",c));call("PUT","/api/settings","ADMIN",c).andExpect(status().isConflict());var info=data(mvc.perform(get("/api/public/system")));assertThat(info.toString()).doesNotContain("password","secret","MAIL_","recipients","jwt");assertThat(info.get("version").asText()).isEqualTo("0.6.0");}
 @Test void invalidSettingsDatesAndTimezoneRejected()throws Exception{var c=config();c.put("springStart",c.get("autumnStart"));call("PUT","/api/settings","ADMIN",c).andExpect(status().isBadRequest());c=config();c.put("businessZone","Invalid/Timezone");call("PUT","/api/settings","ADMIN",c).andExpect(status().isBadRequest());c=config();c.put("notificationRecipients",List.of("bad-email"));call("PUT","/api/settings","ADMIN",c).andExpect(status().isBadRequest());}
 @Test void timezoneCanChangeBeforeBusinessAndLocksAfterInbound()throws Exception{var c=config();c.put("businessZone","UTC");data(call("PUT","/api/settings","ADMIN",c));assertThat(data(call("GET","/api/reports/semester","ADMIN",null)).at("/meta/businessZone").asText()).isEqualTo("UTC");batch(item(),"100");c=config();c.put("businessZone","Asia/Shanghai");call("PUT","/api/settings","ADMIN",c).andExpect(status().isConflict());assertThat(data(call("GET","/api/settings","ADMIN",null)).get("zoneLocked").asBoolean()).isTrue();}
 @Test void purchaserEmailIsUsedForNotifications()throws Exception{createUser("PURCHASER");assertThat(data(call("GET","/api/settings","ADMIN",null)).get("effectiveRecipients").toString()).contains("buyer@example.test");}
 @Test void dashboardMatchesSnapshotsReturnScrapAndRoleScope()throws Exception{
  long m=item(),b=batch(m,"100").get("id").asLong();var i=issue(input(line(b,"20")),"STUDENT");data(call("POST","/api/returns","ADMIN",Map.of("idempotencyKey",UUID.randomUUID(),"issueOrderId",i.at("/order/id").asLong(),"lines",List.of(Map.of("issueOrderLineId",i.at("/lines/0/id").asLong(),"quantity","5")))));data(call("POST","/api/scraps","ADMIN",Map.of("idempotencyKey",UUID.randomUUID(),"batchId",b,"quantity","3","reason","仓库损耗")));jdbc.update("UPDATE material_item SET unit_price=999 WHERE id=?",m);
  var d=data(call("GET","/api/workspace/dashboard","ADMIN",null));assertThat(new java.math.BigDecimal(d.at("/stockValues/0/amount").asText())).isEqualByComparingTo("10.1188");assertThat(new java.math.BigDecimal(d.at("/monthTotals/0/netQuantity").asText())).isEqualByComparingTo("15");assertThat(new java.math.BigDecimal(d.at("/monthTotals/0/netAmount").asText())).isEqualByComparingTo("1.851");assertThat(new java.math.BigDecimal(d.at("/monthTotals/0/scrapQuantity").asText())).isEqualByComparingTo("3");assertThat(d.at("/ranking/0/userId").asLong()).isEqualTo(userIds.get("STUDENT"));assertThat(d.at("/trend/0/day").asText()).isEqualTo("2026-11-03");
  long other=batch(item(),"100").get("id").asLong();var body=input(line(other,"10"));body.put("projectNo","PRIVATE-PROJECT");issue(body,"OTHER");var personal=data(call("GET","/api/workspace/dashboard","STUDENT",null));assertThat(personal.get("ranking").size()).isZero();assertThat(personal.toString()).doesNotContain("PRIVATE-PROJECT");assertThat(personal.get("scope").asText()).isEqualTo("PERSONAL");var teacher=data(call("GET","/api/workspace/dashboard","TEACHER",null));assertThat(teacher.get("ranking").size()).isZero();assertThat(teacher.toString()).doesNotContain("PRIVATE-PROJECT");assertThat(teacher.get("fundingDimension").asText()).isEqualTo("CATEGORY");assertThat(data(call("GET","/api/workspace/dashboard","GUEST",null)).get("monthTotals").size()).isZero();
 }
 @Test void dashboardEmptyStateAndCurrenciesRemainSeparate()throws Exception{assertThat(data(call("GET","/api/workspace/dashboard","ADMIN",null)).get("monthTotals").size()).isZero();long a=item(),b=item();jdbc.update("UPDATE material_item SET currency='USD',unit='米',unit_price=2.5 WHERE id=?",b);issue(input(line(batch(a,"100").get("id").asLong(),"1")),"STUDENT");issue(input(line(batch(b,"100").get("id").asLong(),"2")),"STUDENT");var d=data(call("GET","/api/workspace/dashboard","ADMIN",null));assertThat(d.get("monthTotals").size()).isEqualTo(2);assertThat(d.get("stockValues").size()).isEqualTo(2);assertThat(d.get("funding").size()).isEqualTo(2);}
}
