package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.*;
import cn.caizhitong.mapper.*;
import cn.caizhitong.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
@Service
public class IssueService {
 private final IssueMapper mapper; private final MaterialMapper materials; private final StockMapper stocks;
 private final ObjectMapper json; private final Clock clock; private final SystemSettingsService settings;private final AlertService alerts;
 public IssueService(IssueMapper mapper,MaterialMapper materials,StockMapper stocks,ObjectMapper json,Clock clock,SystemSettingsService settings,AlertService alerts){this.mapper=mapper;this.materials=materials;this.stocks=stocks;this.json=json;this.clock=clock;this.settings=settings;this.alerts=alerts;}
 @Transactional
 public IssueDetail create(IssueInput input,UserPrincipal user) {
  var zone=settings.lockZone();
  mapper.lockRequest(input.idempotencyKey().toString());
  String hash=fingerprint(input);var previous=mapper.byKey(input.idempotencyKey().toString());
  if(previous!=null){
   if(!previous.userId.equals(user.user().getId())||!previous.requestHash.equals(hash))throw new BusinessException(409,"该请求标识已用于不同领用内容，请核对领用历史");
   return detail(previous.id,user);
  }
  // Match inbound / catalog lock order. All material locks precede sorted batch locks.
  Map<Long,Long> batchItems=new TreeMap<>();
  for(var line:input.lines()){
   if(batchItems.containsKey(line.batchId()))throw new BusinessException(400,"同一批次请合并为一条明细");
   Long id=mapper.batchItem(line.batchId());if(id==null)throw new BusinessException(404,"库存批次不存在");batchItems.put(line.batchId(),id);
  }
  Map<Long,MaterialItem> lockedItems=new TreeMap<>();
  for(Long id:new TreeSet<>(batchItems.values())){
   if(materials.lockItem(id)==null)throw new BusinessException(404,"材料档案不存在");
   var item=materials.selectById(id);if(!"ACTIVE".equals(item.getStatus()))throw new BusinessException(409,"材料「"+item.getName()+"」已停用或归档，不能领用");lockedItems.put(id,item);
  }
  Map<Long,StockBatch> lockedBatches=new TreeMap<>();
  for(Long id:batchItems.keySet()){var b=mapper.lockBatch(id);if(b==null)throw new BusinessException(404,"库存批次不存在");lockedBatches.put(id,b);}
  var now=OffsetDateTime.ofInstant(clock.instant(),zone);var month=now.toLocalDate().withDayOfMonth(1);
  var order=new IssueOrderRow();order.userId=user.user().getId();order.accountName=user.getName();order.recipientName=user.user().getDisplayName();
  order.projectNo=input.projectNo().trim();order.usage=input.usage().trim();order.remark=optional(input.remark());order.course=optional(input.course());
  order.idempotencyKey=input.idempotencyKey().toString();order.requestHash=hash;order.createdAt=now;order.monthStart=month;order.businessZone=zone.getId();order.totalAmount=BigDecimal.ZERO;
  List<IssueLineRow> rows=new ArrayList<>();Map<Long,BigDecimal> amounts=new TreeMap<>();
  int index=0;
  for(var line:input.lines()){
   var b=lockedBatches.get(line.batchId());if(b.quantity.compareTo(line.quantity())<0)throw new BusinessException(409,"批次「"+b.batchCode+"」库存不足，当前余额 "+b.quantity.toPlainString());
   BatchRow snapshot=stocks.batch(b.batchCode);
   if(order.currency!=null&&!order.currency.equals(snapshot.currency))throw new BusinessException(400,"不同币种的材料请分单领用");order.currency=snapshot.currency;
   var row=new IssueLineRow();row.lineNo=++index;row.materialId=b.itemId;row.batchId=b.id;row.materialCode=snapshot.itemCode;row.materialName=snapshot.itemName;row.spec=snapshot.spec;row.unit=snapshot.unit;row.batchCode=b.batchCode;
   row.quantity=line.quantity();row.unitPriceSnapshot=b.unitPriceSnapshot;row.amount=row.quantity.multiply(row.unitPriceSnapshot);row.createdAt=now;row.beforeQuantity=b.quantity;row.afterQuantity=b.quantity.subtract(row.quantity);
   order.totalAmount=order.totalAmount.add(row.amount);rows.add(row);amounts.merge(b.itemId,row.quantity,BigDecimal::add);
  }
  for(var entry:amounts.entrySet()){
   Long id=entry.getKey();BigDecimal limit=lockedItems.get(id).getMonthlyIssueLimit();
   mapper.ensureUsage(order.userId,id,month);mapper.lockUsage(order.userId,id,month);
   if(mapper.consume(order.userId,id,month,entry.getValue(),limit,now)!=1)throw new BusinessException(409,"材料「"+lockedItems.get(id).getName()+"」个人本月领用额度不足");
  }
  mapper.insertOrder(order);order.issueNo=mapper.issueNo(order.id);
  for(var row:rows){
   if(mapper.deduct(row.batchId,row.quantity)!=1)throw new BusinessException(409,"库存余额已变化，请重新查询");
   row.issueOrderId=order.id;mapper.insertLine(row);mapper.insertOut(row,order);
  }
  lockedItems.keySet().forEach(alerts::reconcile);
  return detail(order.id,user);
 }
 public boolean manages(UserPrincipal user){return user.roles().stream().anyMatch(r->r.equals("ADMIN")||r.equals("LAB_TECH"));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResult<BatchRow> available(Long id,StockQuery q){
  var item=materials.selectById(id);if(item==null)throw new BusinessException(404,"材料档案不存在");
  if(!"ACTIVE".equals(item.getStatus()))throw new BusinessException(409,"材料已停用或归档，不能领用");
  return new PageResult<>(mapper.available(id,q.getPageSize(),q.getOffset()),mapper.countAvailable(id),q.getPageNumber(),q.getPageSize());
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public IssueDetail detail(Long id,UserPrincipal user){
  var order=mapper.order(id);if(order==null)throw new BusinessException(404,"领用单不存在");
  if(!order.userId.equals(user.user().getId())&&!manages(user))throw new BusinessException(403,"只能查看本人的领用单和凭证");
  return new IssueDetail(order,mapper.lines(id));
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResult<IssueOrderRow> query(IssueQuery q,Long owner){
  var zone=settings.zone();
  OffsetDateTime from=q.from()==null?null:q.from().atStartOfDay(zone).toOffsetDateTime();
  OffsetDateTime to=q.to()==null?null:q.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime();
  return new PageResult<>(mapper.query(q,owner,from,to),mapper.count(q,owner,from,to),q.getPageNumber(),q.getPageSize());
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public IssueQuota quota(Long id,Long userId){
  var item=materials.selectById(id);if(item==null)throw new BusinessException(404,"材料档案不存在");
  var zone=settings.zone();var month=LocalDate.now(clock.withZone(zone)).withDayOfMonth(1);var used=mapper.usage(userId,id,month);var limit=item.getMonthlyIssueLimit();
  return new IssueQuota(id,month,zone.getId(),limit,used,limit==null?null:limit.subtract(used).max(BigDecimal.ZERO));
 }
 @Transactional public MaterialItem setLimit(Long id,IssueLimitInput input){
  if(materials.lockItem(id)==null)throw new BusinessException(404,"材料档案不存在");
  if("ARCHIVED".equals(materials.selectById(id).getStatus()))throw new BusinessException(409,"归档材料仅供查看");
  mapper.setLimit(id,input.monthlyIssueLimit());return materials.detail(id);
 }
 private String optional(String s){return s==null||s.isBlank()?null:s.trim();}
 private String fingerprint(IssueInput input){
  try{
   var lines=input.lines().stream().map(l->List.of(l.batchId(),l.quantity().stripTrailingZeros().toPlainString())).toList();
   var normalized=new ArrayList<Object>(Arrays.asList(input.projectNo().trim(),input.usage().trim(),optional(input.remark()),lines));if(optional(input.course())!=null)normalized.add(optional(input.course()));byte[] body=json.writeValueAsBytes(normalized);
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
  }catch(Exception e){throw new IllegalStateException("Cannot fingerprint issue",e);}
 }
}


