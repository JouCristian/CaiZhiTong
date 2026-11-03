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
class LifecycleIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){AuthIntegrationTest.properties(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired PasswordEncoder passwords;
 @MockitoBean(name="businessClock") Clock clock;
 @Autowired cn.caizhitong.service.AlertService alerts;
 @Autowired cn.caizhitong.service.NotificationWorker worker;
 @Autowired cn.caizhitong.service.NotificationStore notifications;
 @Autowired cn.caizhitong.mapper.OperationsMapper operations;
 @MockitoBean cn.caizhitong.service.MailGateway gateway;
 @MockitoBean cn.caizhitong.service.MailSettings mail;
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
  when(mail.recipients(any())).thenReturn(List.of("buyer@example.test"));when(mail.missing(any())).thenReturn("未配置 SMTP 主机");when(mail.safeFailure(any())).thenReturn("受控 SMTP 失败");
  clear();items.clear();now.set(Instant.parse("2026-11-03T08:00:00Z"));when(clock.instant()).thenAnswer(i->now.get());when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
  category=jdbc.queryForObject("INSERT INTO material_category(name) VALUES(?) RETURNING id",Long.class,"issue_test_"+UUID.randomUUID());
 }
 @AfterEach void cleanup(){
  jdbc.execute("DROP TRIGGER IF EXISTS test_fail_lifecycle ON stock_transaction");jdbc.execute("DROP FUNCTION IF EXISTS test_fail_lifecycle()");jdbc.execute("DROP TRIGGER IF EXISTS test_fail_issue ON stock_transaction");jdbc.execute("DROP FUNCTION IF EXISTS test_fail_issue()");clear();
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
 Map<String,Object> back(JsonNode issue,String qty){return Map.of("idempotencyKey",UUID.randomUUID(),"issueOrderId",issue.at("/order/id").asLong(),"lines",List.of(Map.of("issueOrderLineId",issue.at("/lines/0/id").asLong(),"quantity",qty)));}
 Map<String,Object> scrap(long b,String qty){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID(),"batchId",b,"quantity",qty,"reason","仓库受潮损耗"));}
 JsonNode returned(JsonNode issue,String qty)throws Exception{return data(call("POST","/api/returns","LAB_TECH",back(issue,qty)));}
 JsonNode fixture(String qty)throws Exception{return issue(input(line(batch(item(),"100").get("id").asLong(),qty)),"STUDENT");}
 String report(String type){return "/api/reports/"+type+"?from=2026-11-01&to=2026-11-30";}
 void failLedger(){jdbc.execute("CREATE FUNCTION test_fail_lifecycle() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.txn_type IN ('RETURN','SCRAP') THEN RAISE EXCEPTION 'controlled rollback'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER test_fail_lifecycle BEFORE INSERT ON stock_transaction FOR EACH ROW EXECUTE FUNCTION test_fail_lifecycle()");}
 @Test void partialMultipleReturnsReplayAndCap()throws Exception{
  var issued=fixture("20");long b=issued.at("/lines/0/batchId").asLong();var body=back(issued,"5");var r=data(call("POST","/api/returns","LAB_TECH",body));data(call("POST","/api/returns","LAB_TECH",body));assertThat(balance(b)).isEqualTo("85.000");
  returned(issued,"8");call("POST","/api/returns","ADMIN",back(issued,"8")).andExpect(status().isConflict());returned(issued,"7");assertThat(balance(b)).isEqualTo("100.000");assertThat(count("return_order")).isEqualTo(3);
  var detail=data(call("GET","/api/issues/"+issued.at("/order/id").asLong(),"STUDENT",null));assertThat(detail.at("/lines/0/returnedQuantity").asText()).isEqualTo("20.000");assertThat(detail.at("/lines/0/returnableQuantity").asText()).isEqualTo("0.000");
  assertThat(data(call("GET","/api/returns/"+r.at("/order/id").asLong(),"STUDENT",null)).at("/lines/0/batchId")).isEqualTo(issued.at("/lines/0/batchId"));
  assertThat(jdbc.queryForObject("SELECT used_quantity::text FROM issue_monthly_usage WHERE material_id=? AND user_id=?",String.class,issued.at("/lines/0/materialId").asLong(),userIds.get("STUDENT"))).isEqualTo("20.000");
 }
 @Test void sourceOwnershipAndOtherUsersAreProtected()throws Exception{
  var a=fixture("20");var b=fixture("10");var body=new LinkedHashMap<>(back(a,"1"));body.put("issueOrderId",b.at("/order/id").asLong());call("POST","/api/returns","ADMIN",body).andExpect(status().isNotFound());
  var r=returned(a,"1");call("GET","/api/returns/"+r.at("/order/id").asLong(),"OTHER",null).andExpect(status().isForbidden());assertThat(data(call("GET","/api/returns","OTHER",null)).get("total").asInt()).isZero();
  var tx=data(call("GET","/api/stock-transactions?txnType=RETURN","ADMIN",null)).at("/records/0");assertThat(tx.get("referenceType").asText()).isEqualTo("RETURN");
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","0.0001","1.0001","1000000000000000"}) void invalidLifecycleQuantities(String qty)throws Exception{
  var i=fixture("20");call("POST","/api/returns","ADMIN",back(i,qty)).andExpect(status().isBadRequest());call("POST","/api/scraps","ADMIN",scrap(i.at("/lines/0/batchId").asLong(),qty)).andExpect(status().isBadRequest());assertThat(count("return_order")).isZero();assertThat(count("scrap_order")).isZero();
 }
 @Test void concurrentReturnsNeverOverReturn()throws Exception{
  var i=fixture("20");var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->call("POST","/api/returns","ADMIN",back(i,"15")).andReturn().getResponse().getStatus());var b=pool.submit(()->call("POST","/api/returns","LAB_TECH",back(i,"15")).andReturn().getResponse().getStatus());assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}assertThat(balance(i.at("/lines/0/batchId").asLong())).isEqualTo("95.000");
 }
 @Test void scrapIsImmutableIdempotentAndInLedger()throws Exception{
  long b=batch(item(),"10").get("id").asLong();var body=scrap(b,"3");var d=data(call("POST","/api/scraps","LAB_TECH",body));data(call("POST","/api/scraps","LAB_TECH",body));assertThat(balance(b)).isEqualTo("7.000");assertThat(count("scrap_order")).isEqualTo(1);assertThat(d.get("transactionNo").asText()).startsWith("TXN-");
  body.put("quantity","2");call("POST","/api/scraps","LAB_TECH",body).andExpect(status().isConflict());call("POST","/api/scraps","ADMIN",scrap(b,"8")).andExpect(status().isConflict());call("POST","/api/scraps","ADMIN",scrap(999999999L,"1")).andExpect(status().isNotFound());
  assertThatThrownBy(()->jdbc.update("DELETE FROM scrap_order WHERE id=?",d.get("id").asLong())).isInstanceOf(org.springframework.dao.DataAccessException.class);
  assertThat(data(call("GET","/api/stock-transactions?txnType=SCRAP","ADMIN",null)).at("/records/0/referenceType").asText()).isEqualTo("SCRAP");
 }
 @Test void concurrentScrapNeverNegative()throws Exception{
  long b=batch(item(),"10").get("id").asLong();var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->call("POST","/api/scraps","ADMIN",scrap(b,"7")).andReturn().getResponse().getStatus());var c=pool.submit(()->call("POST","/api/scraps","LAB_TECH",scrap(b,"7")).andReturn().getResponse().getStatus());assertThat(List.of(a.get(20,TimeUnit.SECONDS),c.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}assertThat(balance(b)).isEqualTo("3.000");
 }
 @Test void rollbackReturnAndScrapIncludeStockLedgerAndNotifications()throws Exception{
  var i=fixture("20");long b=i.at("/lines/0/batchId").asLong();long alertsBefore=count("inventory_alert");failLedger();call("POST","/api/returns","ADMIN",back(i,"5")).andExpect(status().is5xxServerError());call("POST","/api/scraps","ADMIN",scrap(b,"75")).andExpect(status().is5xxServerError());assertThat(balance(b)).isEqualTo("80.000");assertThat(count("return_order")).isZero();assertThat(count("scrap_order")).isZero();assertThat(count("inventory_alert")).isEqualTo(alertsBefore);verifyNoInteractions(gateway);
 }
 @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","PURCHASER","GUEST"}) void stockWritesRequireStaff(String role)throws Exception{
  var i=fixture("20");call("POST","/api/returns",role,back(i,"1")).andExpect(status().isForbidden());call("POST","/api/scraps",role,scrap(i.at("/lines/0/batchId").asLong(),"1")).andExpect(status().isForbidden());
 }
 @Test void photoActualTypeSizeAndAuthenticatedAccess()throws Exception{
  var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);
  var upload=mvc.perform(multipart("/api/scraps/attachments").file(new org.springframework.mock.web.MockMultipartFile("file","../../fake.exe","text/plain",out.toByteArray())).header("Authorization","Bearer "+tokens.get("LAB_TECH")));
  long id=data(upload).get("id").asLong();mvc.perform(get("/api/scraps/attachments/"+id)).andExpect(status().isUnauthorized());call("GET","/api/scraps/attachments/"+id,"STUDENT",null).andExpect(status().isForbidden());call("GET","/api/scraps/attachments/"+id,"PURCHASER",null).andExpect(status().isForbidden());call("GET","/api/scraps/attachments/"+id,"LAB_TECH",null).andExpect(content().contentType("image/png"));
  long b=batch(item(),"10").get("id").asLong();var body=scrap(b,"1");body.put("attachmentId",id);data(call("POST","/api/scraps","LAB_TECH",body));call("GET","/api/scraps/attachments/"+id,"PURCHASER",null).andExpect(status().isOk());
  for(byte[] invalid:List.of("not an image".getBytes(),new byte[5242881],Arrays.copyOf(out.toByteArray(),20))){mvc.perform(multipart("/api/scraps/attachments").file(new org.springframework.mock.web.MockMultipartFile("file","photo.png","image/png",invalid)).header("Authorization","Bearer "+tokens.get("ADMIN"))).andExpect(status().isBadRequest());}
  for(String ext:List.of("jpeg","webp")){var bytes=Files.readAllBytes(Path.of("src/test/resources/fixtures/loss."+ext));var saved=data(mvc.perform(multipart("/api/scraps/attachments").file(new org.springframework.mock.web.MockMultipartFile("file","loss."+ext,"application/octet-stream",bytes)).header("Authorization","Bearer "+tokens.get("ADMIN"))));assertThat(saved.get("mediaType").asText()).isEqualTo("image/"+ext);}
  assertThat(count("stock_attachment")).isEqualTo(3);
 }
 @Test void alertBoundaryCyclesAckAndAllInventoryHooks()throws Exception{
  long m=item(),b=batch(m,"10").get("id").asLong();alerts.reconcile(m);assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=? AND status<>'RESOLVED'",Long.class,m)).isZero();
  var i=issue(input(line(b,"1")),"STUDENT");long a=jdbc.queryForObject("SELECT id FROM inventory_alert WHERE material_id=? AND status='ACTIVE'",Long.class,m);for(int k=0;k<3;k++)alerts.reconcile(m);assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=?",Long.class,m)).isEqualTo(2); // initial zero-stock cycle + new drop
  call("POST","/api/alerts/"+a+"/acknowledge","TEACHER",Map.of("note","无权")).andExpect(status().isForbidden());data(call("POST","/api/alerts/"+a+"/acknowledge","PURCHASER",Map.of("note","已联系采购")));assertThat(data(call("GET","/api/alerts/"+a,"ADMIN",null)).get("status").asText()).isEqualTo("ACKNOWLEDGED");
  returned(i,"1");assertThat(data(call("GET","/api/alerts/"+a,"ADMIN",null)).get("status").asText()).isEqualTo("RESOLVED");data(call("POST","/api/scraps","ADMIN",scrap(b,"1")));batch(m,"1");assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=? AND status<>'RESOLVED'",Long.class,m)).isZero();
 }
 @Test void safeZeroAndThresholdEditRescanAreCorrect()throws Exception{
  long m=item();jdbc.update("UPDATE material_item SET safe_stock=0 WHERE id=?",m);alerts.reconcile(m);assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=? AND status<>'RESOLVED'",Long.class,m)).isZero();
  var body=new LinkedHashMap<String,Object>(Map.of("categoryId",category,"name","阈值变更","unit","个","unitPrice","1","safeStock","100","status","ACTIVE"));data(call("PUT","/api/materials/"+m,"ADMIN",body));long a=jdbc.queryForObject("SELECT id FROM inventory_alert WHERE material_id=? AND status='ACTIVE'",Long.class,m);assertThat(data(call("GET","/api/alerts/"+a,"ADMIN",null)).get("mailStatus").asText()).isEqualTo("NOT_CONFIGURED");data(call("POST","/api/alerts/rescan","ADMIN",null));assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_alert WHERE material_id=? AND status='ACTIVE'",Long.class,m)).isEqualTo(1);
 }
 @Test void mailFailureDoesNotRollbackAndExplicitRetryCanSucceed()throws Exception{
  when(mail.missing(any())).thenReturn(null);long m=item();long n=jdbc.queryForObject("SELECT n.id FROM mail_notification n JOIN inventory_alert a ON a.id=n.alert_id WHERE a.material_id=?",Long.class,m);assertThat(count("material_item")).isGreaterThan(0);verifyNoInteractions(gateway);
  doThrow(new java.io.IOException("controlled SMTP down")).when(gateway).send(anyString(),anyString(),anyList());worker.deliver(n);assertThat(jdbc.queryForObject("SELECT status FROM mail_notification WHERE id=?",String.class,n)).isEqualTo("FAILED");batch(m,"100");assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM stock_batch WHERE item_id=?",java.math.BigDecimal.class,m)).isEqualByComparingTo("100");
  long a=jdbc.queryForObject("SELECT alert_id FROM mail_notification WHERE id=?",Long.class,n);notifications.retry(a);doNothing().when(gateway).send(anyString(),anyString(),anyList());worker.deliver(n);worker.deliver(n);assertThat(jdbc.queryForObject("SELECT status FROM mail_notification WHERE id=?",String.class,n)).isEqualTo("SENT");assertThat(jdbc.queryForObject("SELECT attempts FROM mail_notification WHERE id=?",Integer.class,n)).isEqualTo(2);verify(gateway,times(2)).send(contains("低库存"),contains("安全库存"),anyList());
 }
 @Test void reportsExactHistoricalPricesCrossPeriodAndOwnPrivacy()throws Exception{
  var i=fixture("20");returned(i,"5");long m=i.at("/lines/0/materialId").asLong();jdbc.update("UPDATE material_item SET unit_price=999 WHERE id=?",m);
  var r=data(call("GET",report("semester"),"ADMIN",null));assertThat(r.at("/records/0/issueQuantity").asText()).isEqualTo("20.000");assertThat(r.at("/records/0/returnQuantity").asText()).isEqualTo("5.000");assertThat(new java.math.BigDecimal(r.at("/records/0/netAmount").asText())).isEqualByComparingTo("1.851");
  var funding=data(call("GET",report("funding")+"&projectNo=P-2026-04","LAB_TECH",null));assertThat(funding.at("/records/0/projectNo").asText()).isEqualTo("P-2026-04");assertThat(funding.at("/records/0/unitPriceSnapshot").asText()).isEqualTo("0.1234");assertThat(funding.at("/records/0/usage").asText()).isEqualTo("实验电路搭建");
  now.set(Instant.parse("2026-12-03T08:00:00Z"));returned(i,"3");var dec=data(call("GET","/api/reports/semester?from=2026-12-01&to=2026-12-31","ADMIN",null));assertThat(new java.math.BigDecimal(dec.at("/records/0/netQuantity").asText())).isEqualByComparingTo("-3");assertThat(new java.math.BigDecimal(dec.at("/records/0/netAmount").asText())).isEqualByComparingTo("-0.3702");
  call("GET",report("personal")+"&userId="+userIds.get("STUDENT"),"OTHER",null).andExpect(status().isForbidden());call("GET","/api/reports/personal/export/pdf?userId="+userIds.get("STUDENT"),"OTHER",null).andExpect(status().isForbidden());assertThat(data(call("GET",report("personal"),"OTHER",null)).get("total").asInt()).isZero();call("GET",report("funding"),"TEACHER",null).andExpect(status().isForbidden());
 }
 @Test void reportCurrenciesUnitsCoursePagingAndScrapSeparate()throws Exception{
  long m=item();long b=batch(m,"100").get("id").asLong();var body=input(line(b,"20"));body.put("course","电子技术");issue(body,"STUDENT");jdbc.update("UPDATE material_item SET currency='USD' WHERE id=?",m);var usd=data(call("POST","/api/inbound-orders","ADMIN",Map.of("idempotencyKey",UUID.randomUUID(),"lines",List.of(Map.of("itemId",m,"quantity","100","currency","USD","unitPrice","2.5"))))).at("/lines/0/id").asLong();issue(input(line(usd,"3")),"STUDENT");data(call("POST","/api/scraps","ADMIN",scrap(b,"2")));
  var r=data(call("GET",report("semester")+"&size=1","ADMIN",null));assertThat(r.get("total").asInt()).isEqualTo(2);assertThat(r.get("records").size()).isEqualTo(1);assertThat(r.get("totals").size()).isEqualTo(2);var filtered=data(call("GET",report("semester")+"&course="+java.net.URLEncoder.encode("电子技术",java.nio.charset.StandardCharsets.UTF_8),"ADMIN",null));assertThat(filtered.get("total").asInt()).isEqualTo(1);assertThat(new java.math.BigDecimal(filtered.at("/records/0/scrapQuantity").asText())).isZero();
 }
 @Test void allSixExportsAreLegalMatchPreviewAndSaveActualExamples()throws Exception{
  var i=fixture("20");returned(i,"5");var dir=Path.of("../docs/examples/sprint05");Files.createDirectories(dir);
  for(String type:List.of("semester","funding","personal")){
   String suffix=type.equals("personal")?"&userId="+userIds.get("STUDENT"):"";var preview=data(call("GET",report(type)+suffix,"ADMIN",null));assertThat(preview.get("total").asInt()).isEqualTo(1);
   for(String format:List.of("xlsx","pdf")){
    byte[] bytes=call("GET","/api/reports/"+type+"/export/"+format+"?from=2026-11-01&to=2026-11-30"+suffix,"ADMIN",null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();Files.write(dir.resolve(type+"_2026-11."+format),bytes);
    if(format.equals("xlsx"))try(var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))){var sheet=workbook.getSheetAt(0);assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).contains(preview.at("/meta/title").asText());assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();boolean exact=false;for(var cell:sheet.getRow(4)){assertThat(cell.getCellType()).isNotEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.NUMERIC&&Math.abs(cell.getNumericCellValue()-1.851)<.0000001)exact=true;}assertThat(exact).isTrue();}
    else try(var doc=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(doc);assertThat(text.replaceAll("\\s+", "")).contains(preview.at("/meta/title").asText(),"精密电阻领用验收","1.851","2026-11-01");assertThat(doc.getNumberOfPages()).isPositive();}
   }
  }
 }
 @Test void emptyExportsDateValidationAndStudentOwnExport()throws Exception{
  for(String type:List.of("semester","funding","personal"))for(String format:List.of("xlsx","pdf")){byte[] bytes=call("GET","/api/reports/"+type+"/export/"+format,"ADMIN",null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();if(format.equals("pdf"))try(var doc=Loader.loadPDF(bytes)){assertThat(new PDFTextStripper().getText(doc)).contains("无符合条件的记录");}else try(var w=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))){assertThat(w.getSheetAt(0).getRow(4).getCell(0).getStringCellValue()).contains("无符合条件");}}
  call("GET","/api/reports/semester?from=2026-11-01","ADMIN",null).andExpect(status().isBadRequest());call("GET","/api/reports/personal/export/xlsx","STUDENT",null).andExpect(status().isOk());call("GET","/api/reports/semester","STUDENT",null).andExpect(status().isForbidden());
 }
}
