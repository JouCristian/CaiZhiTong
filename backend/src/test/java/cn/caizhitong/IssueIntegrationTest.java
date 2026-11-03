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
class IssueIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){AuthIntegrationTest.properties(r);}
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired UserMapper users; @Autowired PasswordEncoder passwords;
 @MockitoBean(name="businessClock") Clock clock;
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
  clear();items.clear();now.set(Instant.parse("2026-11-03T08:00:00Z"));when(clock.instant()).thenAnswer(i->now.get());when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
  category=jdbc.queryForObject("INSERT INTO material_category(name) VALUES(?) RETURNING id",Long.class,"issue_test_"+UUID.randomUUID());
 }
 @AfterEach void cleanup(){
  jdbc.execute("DROP TRIGGER IF EXISTS test_fail_issue ON stock_transaction");jdbc.execute("DROP FUNCTION IF EXISTS test_fail_issue()");clear();
  for(Long id:items)jdbc.update("DELETE FROM material_item WHERE id=?",id);jdbc.update("DELETE FROM material_category WHERE id=?",category);
 }
 ResultActions call(String method,String path,String role,Object body)throws Exception{
  var builder=switch(method){case "POST"->post(path);case "PATCH"->patch(path);case "PUT"->put(path);default->get(path);};
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
 @Test void issue800Minus20ProducesOrderQuotaAndTraceableOut()throws Exception{
  long material=item();var b=batch(material,"800");long id=b.get("id").asLong();var result=issue(input(line(id,"20")),"STUDENT");
  assertThat(balance(id)).isEqualTo("780.000");assertThat(result.at("/order/issueNo").asText()).matches("ISS-\\d{10}");
  assertThat(result.at("/order/userId").asLong()).isEqualTo(userIds.get("STUDENT"));assertThat(result.at("/order/totalAmount").asText()).isEqualTo("2.4680000");
  assertThat(result.at("/lines/0/transactionNo").asText()).startsWith("TXN-");assertThat(result.at("/lines/0/beforeQuantity").asText()).isEqualTo("800.000");assertThat(result.at("/lines/0/afterQuantity").asText()).isEqualTo("780.000");
  var tx=data(call("GET","/api/stock-transactions?txnType=OUT&itemId="+material,"ADMIN",null)).at("/records/0");
  assertThat(tx.get("referenceType").asText()).isEqualTo("ISSUE");assertThat(tx.get("referenceNo")).isEqualTo(result.at("/order/issueNo"));assertThat(tx.get("orderId")).isEqualTo(result.at("/order/id"));
  assertThat(data(call("GET","/api/materials/"+material+"/issue-quota","STUDENT",null)).get("usedQuantity").asText()).isEqualTo("20.000");
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","0.0001","1.0001","1000000000000000"})
 void invalidQuantitiesRejected(String qty)throws Exception{long b=batch(item(),"800").get("id").asLong();call("POST","/api/issues","STUDENT",input(line(b,qty))).andExpect(status().isBadRequest());assertThat(balance(b)).isEqualTo("800.000");assertThat(count("issue_order")).isZero();}
 @Test void stockInsufficiencyAndMissingOrInactiveBatchAreClear()throws Exception{
  long item=item(),b=batch(item,"800").get("id").asLong();call("POST","/api/issues","STUDENT",input(line(b,"801"))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存不足")));
  call("POST","/api/issues","STUDENT",input(line(99999999,"1"))).andExpect(status().isNotFound());
  for(String status:List.of("DISABLED","ARCHIVED")){call("PATCH","/api/materials/"+item+"/status","ADMIN",Map.of("status",status)).andExpect(status().isOk());call("POST","/api/issues","STUDENT",input(line(b,"1"))).andExpect(status().isConflict());}
  assertThat(balance(b)).isEqualTo("800.000");assertThat(count("issue_monthly_usage")).isZero();
 }
 @Test void idempotencyReplaysWithoutRecheckingChangedCatalogOrQuota()throws Exception{
  long item=item(),b=batch(item,"800").get("id").asLong();var body=input(line(b,"20"));var first=issue(body,"STUDENT");
  jdbc.update("UPDATE material_item SET status='ARCHIVED',unit_price=99,monthly_issue_limit=1 WHERE id=?",item);
  var again=issue(body,"STUDENT");assertThat(again).isEqualTo(first);assertThat(balance(b)).isEqualTo("780.000");assertThat(count("issue_order")).isEqualTo(1);
  body.put("usage","不同用途");call("POST","/api/issues","STUDENT",body).andExpect(status().isConflict());body.put("usage","实验电路搭建");call("POST","/api/issues","OTHER",body).andExpect(status().isConflict());
 }
 @Test void quota50SharedAcrossBatchesAllows20And30ButRejects1()throws Exception{
  long item=item(),a=batch(item,"800").get("id").asLong(),b=batch(item,"800").get("id").asLong();limit(item,"50");
  issue(input(line(a,"20")),"STUDENT");issue(input(line(b,"30")),"STUDENT");
  call("POST","/api/issues","STUDENT",input(line(a,"1"))).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("额度不足")));
  var q=data(call("GET","/api/materials/"+item+"/issue-quota","STUDENT",null));assertThat(q.get("usedQuantity").asText()).isEqualTo("50.000");assertThat(q.get("remainingQuantity").asText()).isEqualTo("0.000");assertThat(balance(a)).isEqualTo("780.000");assertThat(balance(b)).isEqualTo("770.000");
 }
 @Test void unlimitedStillTracksUsageAndNullCanRestoreUnlimited()throws Exception{
  long item=item(),b=batch(item,"800").get("id").asLong();issue(input(line(b,"100")),"STUDENT");limit(item,"50");call("POST","/api/issues","STUDENT",input(line(b,"1"))).andExpect(status().isConflict());
  limit(item,null);issue(input(line(b,"1")),"STUDENT");assertThat(balance(b)).isEqualTo("699.000");
 }
 @Test void multiLineQuotaIsGroupedAndSpecifiedBatchOnlyIsDeducted()throws Exception{
  long item=item(),a=batch(item,"800").get("id").asLong(),b=batch(item,"800").get("id").asLong();limit(item,"50");
  call("POST","/api/issues","STUDENT",input(line(a,"20"),line(b,"31"))).andExpect(status().isConflict());assertThat(balance(a)).isEqualTo("800.000");
  issue(input(line(b,"10.125")),"STUDENT");assertThat(balance(a)).isEqualTo("800.000");assertThat(balance(b)).isEqualTo("789.875");
  var result=issue(input(line(a,"1"),line(b,"2")),"STUDENT");assertThat(result.get("lines").size()).isEqualTo(2);
 }
 @Test void competingForLastStockAllowsExactlyOneSuccess()throws Exception{
  long b=batch(item(),"1").get("id").asLong();var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->call("POST","/api/issues","STUDENT",input(line(b,"1"))).andReturn().getResponse().getStatus());var c=pool.submit(()->call("POST","/api/issues","OTHER",input(line(b,"1"))).andReturn().getResponse().getStatus());assertThat(List.of(a.get(20,TimeUnit.SECONDS),c.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
  assertThat(balance(b)).isEqualTo("0.000");assertThat(count("issue_order")).isEqualTo(1);
 }
 @Test void concurrentDifferentBatchesCannotBypassPersonalQuota()throws Exception{
  long item=item(),a=batch(item,"800").get("id").asLong(),b=batch(item,"800").get("id").asLong();limit(item,"50");var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->call("POST","/api/issues","STUDENT",input(line(a,"30"))).andReturn().getResponse().getStatus());var y=pool.submit(()->call("POST","/api/issues","STUDENT",input(line(b,"30"))).andReturn().getResponse().getStatus());assertThat(List.of(x.get(20,TimeUnit.SECONDS),y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("SELECT used_quantity::text FROM issue_monthly_usage",String.class)).isEqualTo("30.000");assertThat(count("issue_order")).isEqualTo(1);
 }
 @Test void concurrentIdenticalIssueCommitsOnlyOnce()throws Exception{
  long b=batch(item(),"800").get("id").asLong();var body=input(line(b,"20"));var pool=Executors.newFixedThreadPool(2);
  try{var x=pool.submit(()->issue(body,"STUDENT"));var y=pool.submit(()->issue(body,"STUDENT"));assertThat(x.get(20,TimeUnit.SECONDS).at("/order/id")).isEqualTo(y.get(20,TimeUnit.SECONDS).at("/order/id"));}finally{pool.shutdownNow();}
  assertThat(balance(b)).isEqualTo("780.000");assertThat(count("issue_monthly_usage")).isEqualTo(1);
 }
 @Test void lateOutFailureRollsBackStockOrderLinesQuotaAndLedger()throws Exception{
  long b=batch(item(),"800").get("id").asLong();jdbc.execute("CREATE FUNCTION test_fail_issue() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.txn_type='OUT' THEN RAISE EXCEPTION 'Forced late issue failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER test_fail_issue BEFORE INSERT ON stock_transaction FOR EACH ROW EXECUTE FUNCTION test_fail_issue()");
  call("POST","/api/issues","STUDENT",input(line(b,"20"))).andExpect(status().isConflict());assertThat(balance(b)).isEqualTo("800.000");
  for(String table:List.of("issue_order","issue_order_line","issue_monthly_usage"))assertThat(count(table)).isZero();assertThat(count("stock_transaction")).isEqualTo(1);
 }
 @ParameterizedTest @ValueSource(strings={"GUEST","PURCHASER"})
 void nonIssuingRolesCannotSubmitOrReadPrivateIssues(String role)throws Exception{long b=batch(item(),"800").get("id").asLong();var body=input(line(b,"1"));var order=issue(body,"STUDENT");call("POST","/api/issues",role,body).andExpect(status().isForbidden());call("GET","/api/issues/"+order.at("/order/id").asLong()+"/pdf",role,null).andExpect(status().isForbidden());}
 @Test void privateRecordsPdfAndActorCannotBeSpoofed()throws Exception{
  long b=batch(item(),"800").get("id").asLong();var body=input(line(b,"20"));body.put("userId",userIds.get("ADMIN"));var result=issue(body,"STUDENT");long id=result.at("/order/id").asLong();assertThat(result.at("/order/userId").asLong()).isEqualTo(userIds.get("STUDENT"));
  for(String role:List.of("OTHER","TEACHER")){call("GET","/api/issues/"+id,role,null).andExpect(status().isForbidden());call("GET","/api/issues/"+id+"/pdf",role,null).andExpect(status().isForbidden());}
  assertThat(data(call("GET","/api/issues/my?userId="+userIds.get("STUDENT"),"OTHER",null)).get("total").asInt()).isZero();
  for(String role:List.of("ADMIN","LAB_TECH")){call("GET","/api/issues/"+id,role,null).andExpect(status().isOk());assertThat(data(call("GET","/api/issues",role,null)).get("total").asInt()).isEqualTo(1);}
  call("GET","/api/issues","STUDENT",null).andExpect(status().isForbidden());
 }
 @Test void pdfIsA4EmbeddedChineseAndUsesSavedPriceAfterCatalogEdit()throws Exception{
  long material=item(),b=batch(material,"800").get("id").asLong();var result=issue(input(line(b,"20")),"STUDENT");long id=result.at("/order/id").asLong();jdbc.update("UPDATE material_item SET unit_price=99,name='改名后档案' WHERE id=?",material);
  byte[] bytes=call("GET","/api/issues/"+id+"/pdf","STUDENT",null).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andReturn().getResponse().getContentAsByteArray();
  try(var document=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(document);assertThat(text).contains("材智通","精密电阻领用验收","0.1234","2.468","P-2026-04","实验电路搭建","领用人签字","test_student",result.at("/order/issueNo").asText()).doesNotContain("null");assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());for(var name:document.getPage(0).getResources().getFontNames())assertThat(document.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();}
  Path out=Path.of("..",".runtime","sprint04-issue-example.pdf");Files.createDirectories(out.getParent());Files.write(out,bytes);
 }
 @Test void queriesAreServerPagedAndHistoryPersists()throws Exception{
  long b=batch(item(),"800").get("id").asLong();issue(input(line(b,"1")),"TEACHER");issue(input(line(b,"2")),"STUDENT");
  var page=data(call("GET","/api/issues?size=1&page=2&material=精密电阻&projectNo=P-2026-04","ADMIN",null));assertThat(page.get("total").asInt()).isEqualTo(2);assertThat(page.get("records").size()).isEqualTo(1);
  assertThat(data(call("GET","/api/issues?user=test_student","LAB_TECH",null)).get("total").asInt()).isEqualTo(1);
  assertThat(data(call("GET","/api/issues/my?from=2099-01-01","STUDENT",null)).get("total").asInt()).isZero();call("GET","/api/issues/my?from=2026-12-01&to=2026-01-01","STUDENT",null).andExpect(status().isBadRequest());
 }
 @Test void shanghaiMonthBoundaryResetsQuotaWithoutBrowserTimezone()throws Exception{
  long material=item(),b=batch(material,"800").get("id").asLong();limit(material,"1");now.set(Instant.parse("2026-11-30T15:59:59Z"));issue(input(line(b,"1")),"STUDENT");now.set(Instant.parse("2026-11-30T16:00:00Z"));var result=issue(input(line(b,"1")),"STUDENT");assertThat(result.at("/order/monthStart").asText()).isEqualTo("2026-12-01");assertThat(count("issue_monthly_usage")).isEqualTo(2);assertThat(balance(b)).isEqualTo("798.000");
 }
 @Test void onlyAdminCanConfigurePositiveLimitAndOldPutPreservesIt()throws Exception{
  long material=item();for(String role:List.of("LAB_TECH","STUDENT"))call("PATCH","/api/materials/"+material+"/issue-limit",role,Map.of("monthlyIssueLimit","50")).andExpect(status().isForbidden());
  for(String value:List.of("0","-1","0.0001"))call("PATCH","/api/materials/"+material+"/issue-limit","ADMIN",Map.of("monthlyIssueLimit",value)).andExpect(status().isBadRequest());limit(material,"50");
  var updated=data(call("PUT","/api/materials/"+material,"LAB_TECH",Map.of("categoryId",category,"name","实验员维护档案","unit","个","unitPrice","0.2345","safeStock","10","status","ACTIVE")));assertThat(updated.get("monthlyIssueLimit").asText()).isEqualTo("50.000");
 }
 @Test void duplicateBatchAndBlankPurposeAreRejected()throws Exception{
  long b=batch(item(),"800").get("id").asLong();call("POST","/api/issues","STUDENT",input(line(b,"1"),line(b,"2"))).andExpect(status().isBadRequest());var body=input(line(b,"1"));body.put("usage","  ");call("POST","/api/issues","STUDENT",body).andExpect(status().isBadRequest());
 }
 @Test void multipleMaterialsWorkButMixedCurrenciesRequireSeparateOrders()throws Exception{
  long a=item(),b=item();long ba=batch(a,"10").get("id").asLong(),bb=batch(b,"20").get("id").asLong();
  var result=issue(input(line(ba,"2"),line(bb,"3")),"ADMIN");assertThat(result.at("/order/totalAmount").asText()).isEqualTo("0.6170000");assertThat(balance(ba)).isEqualTo("8.000");assertThat(balance(bb)).isEqualTo("17.000");
  long c=item();jdbc.update("UPDATE material_item SET currency='USD' WHERE id=?",c);long bc=batch(c,"10").get("id").asLong();
  call("POST","/api/issues","STUDENT",input(line(ba,"1"),line(bc,"1"))).andExpect(status().isBadRequest());assertThat(balance(ba)).isEqualTo("8.000");assertThat(balance(bc)).isEqualTo("10.000");assertThat(count("issue_order")).isEqualTo(1);
 }
 @Test void availableBatchesExcludeZeroAndRejectDisabledMaterials()throws Exception{
  long material=item(),a=batch(material,"1").get("id").asLong(),b=batch(material,"2").get("id").asLong();issue(input(line(a,"1")),"STUDENT");
  var page=data(call("GET","/api/materials/"+material+"/issue-batches?size=1","STUDENT",null));assertThat(page.get("total").asInt()).isEqualTo(1);assertThat(page.at("/records/0/id").asLong()).isEqualTo(b);
  call("PATCH","/api/materials/"+material+"/status","ADMIN",Map.of("status","DISABLED")).andExpect(status().isOk());call("GET","/api/materials/"+material+"/issue-batches","STUDENT",null).andExpect(status().isConflict());
 }
 @Test void longChinesePdfWrapsAcrossPagesAndRetainsSignatureFields()throws Exception{
  long material=item();jdbc.update("UPDATE material_item SET spec=? WHERE id=?","精密实验规格及参数说明。".repeat(100),material);
  long a=batch(material,"10").get("id").asLong(),b=batch(material,"10").get("id").asLong(),c=batch(material,"10").get("id").asLong();var body=input(line(a,"1"),line(b,"1"),line(c,"1"));body.put("usage","实验与教学用途说明。".repeat(40));body.put("remark","领用凭证的长备注测试。".repeat(100));
  var result=issue(body,"STUDENT");byte[] pdf=call("GET","/api/issues/"+result.at("/order/id").asLong()+"/pdf","STUDENT",null).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var document=Loader.loadPDF(pdf)){assertThat(document.getNumberOfPages()).isGreaterThan(1);assertThat(new PDFTextStripper().getText(document)).contains("明细 3","领用人签字","经办人签字","test_student");}
  Files.write(Path.of("..",".runtime","sprint04-issue-long.pdf"),pdf);
 }
 @Test void ledgerCannotBeUsedToReadAnotherPersonsPrivateIssue()throws Exception{
  long b=batch(item(),"800").get("id").asLong();issue(input(line(b,"20")),"STUDENT");
  for(String role:List.of("TEACHER","PURCHASER"))assertThat(data(call("GET","/api/stock-transactions?txnType=OUT",role,null)).get("total").asInt()).isZero();
  var own=issue(input(line(b,"1")),"TEACHER");var visible=data(call("GET","/api/stock-transactions?txnType=OUT","TEACHER",null));assertThat(visible.get("total").asInt()).isEqualTo(1);assertThat(visible.at("/records/0/referenceNo")).isEqualTo(own.at("/order/issueNo"));
  assertThat(data(call("GET","/api/stock-transactions?txnType=OUT","ADMIN",null)).get("total").asInt()).isEqualTo(2);
 }
}
