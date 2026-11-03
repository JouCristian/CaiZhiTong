package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.StockMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
@Service
public class StockService {
 private final StockMapper mapper; private final QrCodeService qr;private final SystemSettingsService settings;
 public StockService(StockMapper mapper,QrCodeService qr,SystemSettingsService settings){this.mapper=mapper;this.qr=qr;this.settings=settings;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResult<StockRow> stocks(StockQuery q){return new PageResult<>(mapper.stocks(q),mapper.countStocks(q),q.getPageNumber(),q.getPageSize());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public StockSummary summary(){var c=mapper.counts();return new StockSummary(c.materialCount(),c.stockedMaterialCount(),c.lowStockCount(),mapper.stockValues());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public ItemBatches batches(Long id,StockQuery q) {
  StockRow item=mapper.stock(id);if(item==null)throw new BusinessException(404,"材料档案不存在");
  var rows=mapper.batches(id,q);rows.forEach(this::decorate);
  return new ItemBatches(item,new PageResult<>(rows,mapper.countBatches(id),q.getPageNumber(),q.getPageSize()));
 }
 public BatchRow batch(String code){BatchRow b=mapper.batch(code);if(b==null)throw new BusinessException(404,"库存批次不存在");decorate(b);return b;}
 private void decorate(BatchRow b){b.qrContent=qr.content(b.batchCode);}
 public byte[] png(String code){batch(code);return qr.png(code);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResult<InboundRow> orders(StockQuery q){return new PageResult<>(mapper.orders(q,from(q),to(q)),mapper.countOrders(q,from(q),to(q)),q.getPageNumber(),q.getPageSize());}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public InboundDetail order(Long id){
  var row=mapper.order(id);if(row==null)throw new BusinessException(404,"入库单不存在");
  var lines=mapper.orderLines(id);lines.forEach(this::decorate);
  Map<String,BigDecimal> totals=new TreeMap<>();
  for(var l:lines)totals.merge(l.currency,l.initialQuantity.multiply(l.unitPriceSnapshot),BigDecimal::add);
  return new InboundDetail(row,lines,totals.entrySet().stream().map(e->new StockSummary.Money(e.getKey(),e.getValue())).toList());
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public PageResult<TransactionRow> transactions(StockQuery q,UserPrincipal user){
  boolean manager=user.roles().stream().anyMatch(r->r.equals("ADMIN")||r.equals("LAB_TECH"));
  Long viewer=manager?null:user.user().getId();
  return new PageResult<>(mapper.transactions(q,viewer,from(q),to(q)),mapper.countTransactions(q,viewer,from(q),to(q)),q.getPageNumber(),q.getPageSize());
 }
 private java.time.OffsetDateTime from(StockQuery q){return q.from()==null?null:q.from().atStartOfDay(settings.zone()).toOffsetDateTime();}
 private java.time.OffsetDateTime to(StockQuery q){return q.to()==null?null:q.to().plusDays(1).atStartOfDay(settings.zone()).toOffsetDateTime();}
 public List<Map<String,Object>> activities(){
  return mapper.recentTransactions().stream().map(t->Map.<String,Object>of("id",t.transactionNo,"title",t.itemName+" 入库","detail",t.qty.toPlainString()+" "+t.unit+" · "+t.referenceNo,"createdAt",t.createdAt)).toList();
 }
}

