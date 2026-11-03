package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.*;
import cn.caizhitong.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class StockLifecycleService {
 private final OperationsMapper db;private final MaterialMapper materials;private final AttachmentService attachments;private final AlertService alerts;private final ObjectMapper json;private final Clock clock;private final SystemSettingsService settings;
 public StockLifecycleService(OperationsMapper db,MaterialMapper materials,AttachmentService attachments,AlertService alerts,ObjectMapper json,Clock clock,SystemSettingsService settings){this.db=db;this.materials=materials;this.attachments=attachments;this.alerts=alerts;this.json=json;this.clock=clock;this.settings=settings;}
 public static boolean manages(UserPrincipal user){return user.roles().stream().anyMatch(r->r.equals("ADMIN")||r.equals("LAB_TECH"));}
 private void lock(String kind,UUID key){db.one("SELECT pg_advisory_xact_lock(hashtextextended(?,2605))",kind+key);}
 private String optional(String s){return s==null||s.isBlank()?null:s.trim();}
 private String hash(Object body){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(body)));}catch(Exception e){throw new IllegalStateException(e);}}
 private void replay(Map<String,Object> old,UserPrincipal user,String hash){if(id(old,"operator_id")!=user.user().getId()||!hash.equals(text(old,"request_hash")))throw new BusinessException(409,"该请求标识已用于不同内容或操作人，请核对历史记录");}
 @Transactional public Map<String,Object> createReturn(ReturnInput input,UserPrincipal user){
  var zone=settings.lockZone();
  lock("return:",input.idempotencyKey());String hash=hash(Arrays.asList(input.issueOrderId(),optional(input.remark()),input.lines().stream().map(l->List.of(l.issueOrderLineId(),l.quantity().stripTrailingZeros().toPlainString())).toList()));
  var previous=db.one("SELECT * FROM return_order WHERE idempotency_key=?",input.idempotencyKey());if(previous!=null){replay(previous,user,hash);return returnDetail(id(previous,"id"),user);}
  var issue=db.one("SELECT * FROM issue_order WHERE id=?",input.issueOrderId());if(issue==null)throw new BusinessException(404,"原领用单不存在");
  Map<Long,Map<String,Object>> sources=new TreeMap<>();Map<Long,BigDecimal> quantities=new HashMap<>();
  for(var line:input.lines()){
   if(sources.containsKey(line.issueOrderLineId()))throw new BusinessException(400,"同一领用明细请合并数量");
   var source=db.one("SELECT * FROM issue_order_line WHERE id=? AND issue_order_id=?",line.issueOrderLineId(),input.issueOrderId());if(source==null)throw new BusinessException(404,"领用明细不属于所选领用单");sources.put(line.issueOrderLineId(),source);quantities.put(line.issueOrderLineId(),line.quantity());
  }
  var itemIds=new TreeSet<Long>();var batchIds=new TreeSet<Long>();sources.values().forEach(s->{itemIds.add(id(s,"material_id"));batchIds.add(id(s,"batch_id"));});itemIds.forEach(materials::lockItem);
  Map<Long,Map<String,Object>> batches=new HashMap<>();for(Long b:batchIds)batches.put(b,db.one("SELECT * FROM stock_batch WHERE id=? FOR UPDATE",b));
  for(Long line:sources.keySet()){
   db.one("SELECT id FROM issue_order_line WHERE id=? FOR UPDATE",line);
   var used=db.one("SELECT COALESCE(sum(quantity),0) AS quantity FROM return_order_line WHERE issue_order_line_id=?",line);
   if(num(used,"quantity").add(quantities.get(line)).compareTo(num(sources.get(line),"quantity"))>0)throw new BusinessException(409,"累计归还不能超过原领用数量");
  }
  var now=OffsetDateTime.ofInstant(clock.instant(),zone);
  var order=db.one("INSERT INTO return_order(issue_order_id,user_id,operator_id,idempotency_key,request_hash,remark,created_at) VALUES(?,?,?,?,?,?,?) RETURNING *",input.issueOrderId(),id(issue,"user_id"),user.user().getId(),input.idempotencyKey(),hash,optional(input.remark()),now);
  for(var inputLine:input.lines()){
   var source=sources.get(inputLine.issueOrderLineId());long batchId=id(source,"batch_id");var batch=batches.get(batchId);var before=num(batch,"quantity");var after=before.add(inputLine.quantity());
   if(db.update("UPDATE stock_batch SET quantity=quantity+? WHERE id=? AND quantity+?<=initial_quantity",inputLine.quantity(),batchId,inputLine.quantity())!=1)throw new BusinessException(409,"归还后余额超出原始入库数量，请核对批次");
   var line=db.one("INSERT INTO return_order_line(return_order_id,issue_order_id,issue_order_line_id,batch_id,quantity,created_at) VALUES(?,?,?,?,?,?) RETURNING id",id(order,"id"),input.issueOrderId(),inputLine.issueOrderLineId(),batchId,inputLine.quantity(),now);
   db.update("INSERT INTO stock_transaction(batch_id,txn_type,qty,before_quantity,after_quantity,user_id,reference_no,project_no,usage,remark,return_order_id,return_line_id,created_at) VALUES(?,'RETURN',?,?,?,?,?,?,?,?,?,?,?)",batchId,inputLine.quantity(),before,after,user.user().getId(),text(order,"return_no"),text(issue,"project_no"),text(issue,"usage"),optional(input.remark()),id(order,"id"),id(line,"id"),now);
   batch.put("quantity",after);
  }
  itemIds.forEach(alerts::reconcile);return returnDetail(id(order,"id"),user);
 }
 @Transactional public Map<String,Object> createScrap(ScrapInput input,UserPrincipal user){
  var zone=settings.lockZone();
  lock("scrap:",input.idempotencyKey());String hash=hash(Arrays.asList(input.batchId(),input.quantity().stripTrailingZeros().toPlainString(),input.reason().trim(),optional(input.remark()),input.attachmentId()));
  var previous=db.one("SELECT * FROM scrap_order WHERE idempotency_key=?",input.idempotencyKey());if(previous!=null){replay(previous,user,hash);return scrapDetail(id(previous,"id"));}
  var origin=db.one("SELECT item_id FROM stock_batch WHERE id=?",input.batchId());if(origin==null)throw new BusinessException(404,"库存批次不存在");long itemId=id(origin,"item_id");materials.lockItem(itemId);
  var batch=db.one("SELECT * FROM stock_batch WHERE id=? FOR UPDATE",input.batchId());var before=num(batch,"quantity");if(before.compareTo(input.quantity())<0)throw new BusinessException(409,"仓库当前库存不足，不能报废已领出的材料");
  if(input.attachmentId()!=null)attachments.validateForScrap(input.attachmentId(),user);
  var now=OffsetDateTime.ofInstant(clock.instant(),zone);
  var order=db.one("INSERT INTO scrap_order(batch_id,quantity,reason,remark,attachment_id,operator_id,idempotency_key,request_hash,created_at) VALUES(?,?,?,?,?,?,?,?,?) RETURNING *",input.batchId(),input.quantity(),input.reason().trim(),optional(input.remark()),input.attachmentId(),user.user().getId(),input.idempotencyKey(),hash,now);
  if(db.update("UPDATE stock_batch SET quantity=quantity-? WHERE id=? AND quantity>=?",input.quantity(),input.batchId(),input.quantity())!=1)throw new BusinessException(409,"仓库库存不足");
  db.update("INSERT INTO stock_transaction(batch_id,txn_type,qty,before_quantity,after_quantity,user_id,reference_no,remark,scrap_order_id,created_at) VALUES(?,'SCRAP',?,?,?,?,?,?,?,?)",input.batchId(),input.quantity(),before,before.subtract(input.quantity()),user.user().getId(),text(order,"scrap_no"),input.reason().trim(),id(order,"id"),now);
  alerts.reconcile(itemId);return scrapDetail(id(order,"id"));
 }
 private static final String RETURNS="SELECT r.*,o.issue_no,o.username_snapshot AS account_name,o.user_name_snapshot AS recipient_name,o.project_no,o.course,u.display_name AS operator_name FROM return_order r JOIN issue_order o ON o.id=r.issue_order_id JOIN sys_user u ON u.id=r.operator_id";
 private static final String SCRAPS="SELECT s.*,b.item_id,b.batch_code,l.item_name_snapshot AS material_name,l.item_code_snapshot AS material_code,l.spec_snapshot AS spec,l.unit_snapshot AS unit,l.currency_snapshot AS currency,b.unit_price_snapshot,s.quantity*b.unit_price_snapshot AS amount,u.display_name AS operator_name,t.transaction_no,t.before_quantity,t.after_quantity FROM scrap_order s JOIN stock_batch b ON b.id=s.batch_id JOIN inbound_order_line l ON l.id=b.inbound_line_id JOIN sys_user u ON u.id=s.operator_id JOIN stock_transaction t ON t.scrap_order_id=s.id";
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public Map<String,Object> returnDetail(Long id,UserPrincipal user){
  var order=db.one(RETURNS+" WHERE r.id=?",id);if(order==null)throw new BusinessException(404,"归还记录不存在");if(!manages(user)&&id(order,"user_id")!=user.user().getId())throw new BusinessException(403,"只能查看本人的归还记录");
  var lines=db.rows("SELECT r.*,l.material_id,l.material_name,l.material_code,l.spec,l.unit,l.batch_code,l.unit_price_snapshot,l.quantity AS issued_quantity,COALESCE((SELECT sum(x.quantity) FROM return_order_line x WHERE x.issue_order_line_id=l.id),0) AS returned_quantity,t.transaction_no,t.before_quantity,t.after_quantity FROM return_order_line r JOIN issue_order_line l ON l.id=r.issue_order_line_id JOIN stock_transaction t ON t.return_line_id=r.id WHERE r.return_order_id=? ORDER BY r.id",id);
  return api(Map.of("order",order,"lines",lines));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public Map<String,Object> scrapDetail(Long id){var r=db.one(SCRAPS+" WHERE s.id=?",id);if(r==null)throw new BusinessException(404,"报废记录不存在");return api(r);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public PageResult<Map<String,Object>> list(OperationQuery q,UserPrincipal user,boolean scrap){
  var zone=settings.zone();
  String prefix=scrap?SCRAPS:RETURNS;String alias=scrap?"s":"r";var where=new StringBuilder(" WHERE 1=1");var args=new ArrayList<Object>();
  if(!scrap&&!manages(user)){where.append(" AND r.user_id=?");args.add(user.user().getId());}
  if(q.keyword()!=null&&!q.keyword().isBlank()){
   where.append(scrap?" AND (s.scrap_no ILIKE ? ESCAPE '\\' OR l.item_name_snapshot ILIKE ? ESCAPE '\\' OR l.item_code_snapshot ILIKE ? ESCAPE '\\' OR b.batch_code ILIKE ? ESCAPE '\\')":" AND (r.return_no ILIKE ? ESCAPE '\\' OR o.issue_no ILIKE ? ESCAPE '\\' OR EXISTS(SELECT 1 FROM return_order_line x JOIN issue_order_line y ON y.id=x.issue_order_line_id WHERE x.return_order_id=r.id AND (y.material_name ILIKE ? ESCAPE '\\' OR y.material_code ILIKE ? ESCAPE '\\')))");for(int i=0;i<4;i++)args.add(OperationQuery.pattern(q.keyword()));
  }
  if(!scrap&&q.user()!=null&&!q.user().isBlank()){where.append(" AND (o.username_snapshot ILIKE ? ESCAPE '\\' OR o.user_name_snapshot ILIKE ? ESCAPE '\\')");args.add(OperationQuery.pattern(q.user()));args.add(OperationQuery.pattern(q.user()));}
  if(q.from()!=null){where.append(" AND "+alias+".created_at>=?");args.add(q.from().atStartOfDay(zone).toOffsetDateTime());}if(q.to()!=null){where.append(" AND "+alias+".created_at<?");args.add(q.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime());}
  long total=db.count("SELECT count(*) FROM ("+prefix+where+") z",args.toArray());args.add(q.s());args.add(q.offset());return new PageResult<>(db.rows(prefix+where+" ORDER BY "+alias+".created_at DESC,"+alias+".id DESC LIMIT ? OFFSET ?",args.toArray()).stream().map(OperationsMapper::api).toList(),total,q.p(),q.s());
 }
}
