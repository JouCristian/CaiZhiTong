package cn.caizhitong;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.*;
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
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.cors.allowed-origins=http://localhost:5173","app.demo.enabled=false"})
@ActiveProfiles("test") @AutoConfigureMockMvc @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StockIntegrationTest {
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){AuthIntegrationTest.properties(r);}
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired UserMapper users;@Autowired PasswordEncoder passwords;
 private final Map<String,String> tokens=new HashMap<>();private final List<Long> itemIds=new ArrayList<>();private Long categoryId;
 @BeforeAll void seed() throws Exception {
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).endsWith("_test");
  for(String role:List.of("ADMIN","LAB_TECH","STUDENT","TEACHER","PURCHASER","GUEST")){
   String name="test_"+role.toLowerCase(Locale.ROOT);
   SysUser user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,name));
   if(user==null){user=new SysUser();user.setUsername(name);user.setDisplayName("库存测试账户");user.setPasswordHash(passwords.encode("test-only-password-2026"));user.setEnabled(true);users.insert(user);}
   users.assignRole(user.getId(),role);
   var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("username",name,"password","test-only-password-2026")))).andExpect(status().isOk()).andReturn();
   tokens.put(role,json.readTree(response.getResponse().getContentAsString()).at("/data/accessToken").asText());
  }
 }
 void clearStock(){jdbc.execute("TRUNCATE mail_notification,inventory_alert,return_order_line,return_order,scrap_order,stock_attachment,stock_transaction,issue_order_line,issue_order,issue_monthly_usage,stock_batch,inbound_order_line,inbound_order RESTART IDENTITY");}
 @BeforeEach void setup(){
  clearStock();itemIds.clear();
  categoryId=jdbc.queryForObject("INSERT INTO material_category(name) VALUES (?) RETURNING id",Long.class,"stock_test_"+UUID.randomUUID());
 }
 @AfterEach void cleanup(){
  jdbc.execute("DROP TRIGGER IF EXISTS test_fail_stock_txn ON stock_transaction");jdbc.execute("DROP FUNCTION IF EXISTS test_fail_stock_txn()");
  clearStock();for(Long id:itemIds)jdbc.update("DELETE FROM material_item WHERE id=?",id);
  jdbc.update("DELETE FROM material_category WHERE id=?",categoryId);
 }
 ResultActions call(String method,String path,String role,Object body) throws Exception {
  var b=switch(method){case"POST"->post(path);case"PUT"->put(path);case"PATCH"->patch(path);case"DELETE"->delete(path);default->get(path);};
  b.header("Authorization","Bearer "+tokens.get(role));
  if(body!=null)b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  return mvc.perform(b);
 }
 JsonNode data(ResultActions result)throws Exception{return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");}
 long item(String name,String currency)throws Exception{
  var m=data(call("POST","/api/materials","ADMIN",Map.of("categoryId",categoryId,"name",name,"unit","kg","unitPrice","2.3456","safeStock","10","currency",currency,"status","ACTIVE")));
  long id=m.get("id").asLong();itemIds.add(id);return id;
 }
 Map<String,Object> line(long id,String qty){return new LinkedHashMap<>(Map.of("itemId",id,"quantity",qty));}
 Map<String,Object> order(Map<String,Object>... lines){return new LinkedHashMap<>(Map.of("idempotencyKey",UUID.randomUUID().toString(),"lines",List.of(lines)));}
 JsonNode inbound(Map<String,Object> input,String role)throws Exception{return data(call("POST","/api/inbound-orders",role,input));}
 JsonNode stock(long id)throws Exception{return data(call("GET","/api/stocks/"+id+"/batches","ADMIN",null)).get("material");}
 @Test void singleInboundCommitsAllFourTablesAndExactSnapshot()throws Exception{
  long id=item("单项入库","CNY");var result=inbound(order(line(id,"1.250")),"ADMIN");
  assertThat(result.at("/order/orderNo").asText()).matches("IN-\\d{10}");
  assertThat(result.at("/lines/0/batchCode").asText()).matches("BAT-\\d{10}");
  assertThat(result.at("/lines/0/unitPriceSnapshot").asText()).isEqualTo("2.3456");
  assertThat(result.at("/totals/0/amount").asText()).isEqualTo("2.9320000");
  assertThat(stock(id).get("quantity").asText()).isEqualTo("1.250");
  for(String table:List.of("inbound_order","inbound_order_line","stock_batch","stock_transaction"))
   assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).isEqualTo(1);
 }
 @Test void multipleMaterialsAndRepeatedItemLinesCreateDistinctBatches()throws Exception{
  long a=item("两项入库 A","CNY"),b=item("两项入库 B","CNY");
  var result=inbound(order(line(a,"0.125"),line(b,"2.5"),line(a,"0.375")),"LAB_TECH");
  assertThat(result.get("lines").size()).isEqualTo(3);
  assertThat(stock(a).get("quantity").asText()).isEqualTo("0.500");
  assertThat(stock(a).get("batchCount").asLong()).isEqualTo(2);
  assertThat(stock(b).get("quantity").asText()).isEqualTo("2.500");
 }
 @ParameterizedTest @ValueSource(strings={"0","-1","0.0001","9999999999999999","1.1234"})
 void invalidQuantitiesRejected(String qty)throws Exception{
  long id=item("非法数量","CNY");
  call("POST","/api/inbound-orders","ADMIN",order(line(id,qty))).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("SELECT count(*) FROM inbound_order",Long.class)).isZero();
 }
 @Test void missingDisabledAndArchivedMaterialsCannotReceive()throws Exception{
  call("POST","/api/inbound-orders","ADMIN",order(line(99999999L,"1"))).andExpect(status().isNotFound());
  for(String status:List.of("DISABLED","ARCHIVED")){
   long id=item(status,"CNY");data(call("PATCH","/api/materials/"+id+"/status","ADMIN",Map.of("status",status)));
   call("POST","/api/inbound-orders","ADMIN",order(line(id,"1"))).andExpect(status().isConflict());
  }
 }
 @Test void identicalRetryIsIdempotentEvenAfterCatalogPriceChanges()throws Exception{
  long id=item("幂等材料","CNY");var input=order(line(id,"3"));
  var first=inbound(input,"ADMIN");
  jdbc.update("UPDATE material_item SET unit_price=99 WHERE id=?",id);
  var second=inbound(input,"ADMIN");
  assertThat(second.at("/order/id")).isEqualTo(first.at("/order/id"));
  assertThat(stock(id).get("quantity").asText()).isEqualTo("3.000");
  assertThat(second.at("/lines/0/unitPriceSnapshot").asText()).isEqualTo("2.3456");
 }
 @Test void sameKeyWithChangedPayloadOrDifferentOperatorIsRejected()throws Exception{
  long id=item("请求校验","CNY");var input=order(line(id,"2"));inbound(input,"ADMIN");
  call("POST","/api/inbound-orders","LAB_TECH",input).andExpect(status().isConflict());
  input.put("lines",List.of(line(id,"3")));
  call("POST","/api/inbound-orders","ADMIN",input).andExpect(status().isConflict());
  assertThat(stock(id).get("quantity").asText()).isEqualTo("2.000");
 }
 @Test void concurrentIdenticalRequestsCommitOnlyOnce()throws Exception{
  long id=item("并发材料","CNY");var input=order(line(id,"0.125"));
  var pool=Executors.newFixedThreadPool(2);
  try{
   Callable<JsonNode> task=()->inbound(input,"ADMIN");
   var a=pool.submit(task);var b=pool.submit(task);
   assertThat(a.get(15,TimeUnit.SECONDS).at("/order/id")).isEqualTo(b.get(15,TimeUnit.SECONDS).at("/order/id"));
   assertThat(stock(id).get("quantity").asText()).isEqualTo("0.125");
   assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_transaction",Long.class)).isEqualTo(1);
  }finally{pool.shutdownNow();}
 }
 @Test void aLateLedgerInsertFailureRollsBackTheEntireOrder()throws Exception{
  long a=item("回滚 A","CNY"),b=item("回滚 B","CNY");
  jdbc.execute("CREATE FUNCTION test_fail_stock_txn() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.remark='__FAIL_LEDGER__' THEN RAISE EXCEPTION 'Test ledger failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER test_fail_stock_txn BEFORE INSERT ON stock_transaction FOR EACH ROW EXECUTE FUNCTION test_fail_stock_txn()");
  var second=line(b,"2");second.put("remark","__FAIL_LEDGER__");
  call("POST","/api/inbound-orders","ADMIN",order(line(a,"1"),second)).andExpect(status().isConflict());
  for(String table:List.of("inbound_order","inbound_order_line","stock_batch","stock_transaction"))
   assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
  assertThat(stock(a).get("quantity").asText()).isEqualTo("0");
 }
 @Test void ledgerHasCorrectBatchBalancesOperatorAndOrderTrace()throws Exception{
  long id=item("流水材料","CNY");var result=inbound(order(line(id,"2.125")),"LAB_TECH");
  var tx=data(call("GET","/api/stock-transactions?itemId="+id+"&txnType=IN","ADMIN",null)).at("/records/0");
  assertThat(tx.get("beforeQuantity").asText()).isEqualTo("0.000");
  assertThat(tx.get("afterQuantity").asText()).isEqualTo("2.125");
  assertThat(tx.get("qty").asText()).isEqualTo("2.125");
  assertThat(tx.get("referenceNo")).isEqualTo(result.at("/order/orderNo"));
  assertThat(tx.get("batchCode")).isEqualTo(result.at("/lines/0/batchCode"));
  assertThat(tx.get("operatorName").asText()).isNotBlank();
 }
 @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","PURCHASER","GUEST"})
 void readonlyRolesCannotCreateInboundButCanReadAuthorizedBatches(String role)throws Exception{
  long id=item("只读库存","CNY");var input=order(line(id,"1"));
  call("POST","/api/inbound-orders",role,input).andExpect(status().isForbidden());
  String code=inbound(input,"ADMIN").at("/lines/0/batchCode").asText();
  call("GET","/api/stock-batches/"+code,role,null).andExpect(status().is(role.equals("GUEST")?403:200));
  call("GET","/api/stocks?itemId="+id,role,null).andExpect(status().is(role.equals("GUEST")?403:200));
 }
 @Test void qrPngDecodesToConfiguredProtectedBatchRoute()throws Exception{
  long id=item("二维码材料","CNY");String code=inbound(order(line(id,"1")),"ADMIN").at("/lines/0/batchCode").asText();
  byte[] png=call("GET","/api/stock-batches/"+code+"/qrcode","STUDENT",null).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
  var image=ImageIO.read(new ByteArrayInputStream(png));
  String text=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)))).getText();
  assertThat(text).isEqualTo("http://localhost:5173/stock/batches/"+code);
  assertThat(text).doesNotContain("Bearer","jwt","password");
  mvc.perform(get("/api/stock-batches/"+code)).andExpect(status().isUnauthorized());
  call("GET","/api/stock-batches/BAT-9999999999","ADMIN",null).andExpect(status().isNotFound());
  call("GET","/api/stock-batches/BAT-9999999999/qrcode","ADMIN",null).andExpect(status().isNotFound());
 }
 @Test void historyPricesAndNamesRemainStableAfterCatalogEditing()throws Exception{
  long id=item("原始档案名","CNY");var result=inbound(order(line(id,"2")),"ADMIN");
  jdbc.update("UPDATE material_item SET name='新名称',unit_price=99,currency='USD' WHERE id=?",id);
  var order=data(call("GET","/api/inbound-orders/"+result.at("/order/id").asLong(),"ADMIN",null));
  assertThat(order.at("/lines/0/itemName").asText()).isEqualTo("原始档案名");
  assertThat(order.at("/lines/0/unitPriceSnapshot").asText()).isEqualTo("2.3456");
  assertThat(order.at("/lines/0/currency").asText()).isEqualTo("CNY");
 }
 @Test void summaryGroupsCurrenciesAndHandlesZeroAndLowStock()throws Exception{
  long a=item("CNY 材料","CNY"),b=item("USD 材料","USD");item("零库存","CNY");
  inbound(order(line(a,"1.25"),line(b,"2")),"ADMIN");
  var s=data(call("GET","/api/stocks/summary","ADMIN",null));
  assertThat(s.get("materialCount").asInt()).isEqualTo(3);
  assertThat(s.get("stockedMaterialCount").asInt()).isEqualTo(2);
  assertThat(s.get("lowStockCount").asInt()).isEqualTo(3);
  assertThat(s.get("values").size()).isEqualTo(2);
 }
 @Test void stockFilteringPaginationAndHistoryDatesWorkOnServer()throws Exception{
  long a=item("筛选 A","CNY");item("筛选 B","CNY");inbound(order(line(a,"20")),"ADMIN");
  assertThat(data(call("GET","/api/stocks?stockStatus=ZERO&size=1&page=1","ADMIN",null)).get("total").asInt()).isEqualTo(1);
  assertThat(data(call("GET","/api/stocks?categoryId="+categoryId+"&size=1&page=2","ADMIN",null)).get("records").size()).isEqualTo(1);
  assertThat(data(call("GET","/api/stocks?keyword=筛选&stockStatus=NORMAL","ADMIN",null)).get("total").asInt()).isEqualTo(1);
  assertThat(data(call("GET","/api/inbound-orders?from=2099-01-01","ADMIN",null)).get("total").asInt()).isZero();
  call("GET","/api/stocks?size=101","ADMIN",null).andExpect(status().isBadRequest());
  call("GET","/api/inbound-orders?from=2026-12-01&to=2026-01-01","ADMIN",null).andExpect(status().isBadRequest());
 }
 @Test void databaseProtectsAppendOnlyHistoryAndImmutableOrigin()throws Exception{
  long id=item("不可变材料","CNY");var result=inbound(order(line(id,"1")),"ADMIN");long batch=result.at("/lines/0/id").asLong();
  assertThatThrownBy(()->jdbc.update("UPDATE stock_batch SET initial_quantity=2 WHERE id=?",batch)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE stock_transaction SET qty=9")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("DELETE FROM inbound_order")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThatThrownBy(()->jdbc.update("UPDATE stock_batch SET quantity=-1 WHERE id=?",batch)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
 }
 @Test void unitCannotChangeOnceThereIsStockButPriceCanChange()throws Exception{
  long id=item("单位保护","CNY");inbound(order(line(id,"1")),"ADMIN");
  var body=new LinkedHashMap<String,Object>(Map.of("categoryId",categoryId,"name","单位保护","unit","个","unitPrice","9.9999","safeStock","10","status","ACTIVE"));
  call("PUT","/api/materials/"+id,"ADMIN",body).andExpect(status().isConflict());
  body.put("unit","kg");call("PUT","/api/materials/"+id,"ADMIN",body).andExpect(status().isOk());
  assertThat(stock(id).get("unit").asText()).isEqualTo("kg");
 }
}


