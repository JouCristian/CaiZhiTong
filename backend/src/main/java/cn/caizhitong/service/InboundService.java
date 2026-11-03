package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.*;
import cn.caizhitong.mapper.*;
import cn.caizhitong.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.security.MessageDigest;
import java.util.*;
@Service
public class InboundService {
 private final StockMapper stock;private final MaterialMapper materials;private final StockService reads;private final ObjectMapper json;private final AlertService alerts;private final SystemSettingsService settings;
 public InboundService(StockMapper stock,MaterialMapper materials,StockService reads,ObjectMapper json,AlertService alerts,SystemSettingsService settings){this.stock=stock;this.materials=materials;this.reads=reads;this.json=json;this.alerts=alerts;this.settings=settings;}
 @Transactional
 public InboundDetail create(InboundInput input,UserPrincipal user) {
  var zone=settings.lockZone();
  stock.lockRequest(input.idempotencyKey().toString());
  String fingerprint=fingerprint(input);
  InboundOrder previous=stock.byKey(input.idempotencyKey().toString());
  if(previous!=null) {
   if(!previous.createdBy.equals(user.user().getId())||!previous.requestHash.equals(fingerprint))
    throw new BusinessException(409,"该请求标识已用于不同入库内容，请先核对入库历史");
   return reads.order(previous.id);
  }
  var itemIds=input.lines().stream().map(InboundInput.Line::itemId).distinct().sorted().toList();
  Map<Long,MaterialItem> locked=new HashMap<>();
  for(Long id:itemIds){
   if(materials.lockItem(id)==null)throw new BusinessException(404,"入库材料不存在："+id);
   var item=materials.selectById(id);
   if(!"ACTIVE".equals(item.getStatus()))throw new BusinessException(409,"材料「"+item.getName()+"」已停用或归档，不能入库");
   locked.put(id,item);
  }
  InboundOrder order=new InboundOrder();order.idempotencyKey=input.idempotencyKey().toString();order.requestHash=fingerprint;order.createdBy=user.user().getId();order.remark=trim(input.remark());
  order.receivedAt=input.receivedAt()==null?OffsetDateTime.now():input.receivedAt();
  stock.insertOrder(order);order.orderNo=stock.orderNo(order.id);
  int index=0;
  for(var inputLine:input.lines()) {
   var item=locked.get(inputLine.itemId());
   if(inputLine.currency()!=null&&!inputLine.currency().equals(item.getCurrency()))throw new BusinessException(409,"材料币种已变更，请重新选择材料");
   if(inputLine.unit()!=null&&!inputLine.unit().equals(item.getUnit()))throw new BusinessException(409,"材料单位已变更，请重新选择材料");
   if(inputLine.expireDate()!=null&&inputLine.expireDate().isBefore(order.receivedAt.atZoneSameInstant(zone).toLocalDate()))
    throw new BusinessException(400,"有效期不能早于本次入库日期");
   InboundLine line=new InboundLine();line.orderId=order.id;line.lineNo=++index;line.itemId=item.getId();line.quantity=inputLine.quantity();
   line.unitPriceSnapshot=inputLine.unitPrice()==null?item.getUnitPrice():inputLine.unitPrice();line.currencySnapshot=item.getCurrency();
   line.itemCodeSnapshot=item.getMaterialCode();line.itemNameSnapshot=item.getName();line.specSnapshot=item.getSpec();line.unitSnapshot=item.getUnit();
   line.location=trim(inputLine.location());line.expireDate=inputLine.expireDate();line.remark=trim(inputLine.remark());
   stock.insertLine(line);
   StockBatch batch=new StockBatch();batch.itemId=item.getId();batch.inboundLineId=line.id;batch.initialQuantity=line.quantity;batch.quantity=line.quantity;batch.unitPriceSnapshot=line.unitPriceSnapshot;
   batch.receivedAt=order.receivedAt;batch.expireDate=line.expireDate;batch.location=line.location;batch.remark=line.remark;batch.createdBy=user.user().getId();
   stock.insertBatch(batch);
   StockTransaction txn=new StockTransaction();txn.batchId=batch.id;txn.qty=line.quantity;txn.beforeQuantity=BigDecimal.ZERO;txn.afterQuantity=line.quantity;txn.userId=user.user().getId();txn.inboundOrderId=order.id;txn.referenceNo=order.orderNo;txn.remark=line.remark;
   stock.insertTransaction(txn);
  }
  itemIds.forEach(alerts::reconcile);
  return reads.order(order.id);
 }
 private String trim(String s){return s==null||s.isBlank()?null:s.trim();}
 private String decimal(BigDecimal n){return n==null?"<default>":n.stripTrailingZeros().toPlainString();}
 private String fingerprint(InboundInput input) {
  try{
   List<Object> normalized=new ArrayList<>();
   normalized.add(input.receivedAt()==null?null:input.receivedAt().toInstant().toString());normalized.add(trim(input.remark()));
   for(var l:input.lines())normalized.add(Arrays.asList(l.itemId(),decimal(l.quantity()),decimal(l.unitPrice()),l.currency(),l.unit(),trim(l.location()),l.expireDate()==null?null:l.expireDate().toString(),trim(l.remark())));
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(normalized)));
  }catch(Exception e){throw new IllegalStateException("Cannot fingerprint inbound request",e);}
 }
}



