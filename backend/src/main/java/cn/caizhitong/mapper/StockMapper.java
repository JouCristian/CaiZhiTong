package cn.caizhitong.mapper;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.*;
import org.apache.ibatis.annotations.*;
import java.util.*;
public interface StockMapper {
 @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(#{key},2603))") Integer lockRequest(String key);
 @Select("SELECT * FROM inbound_order WHERE idempotency_key=CAST(#{key} AS UUID)") InboundOrder byKey(String key);
 @Insert("INSERT INTO inbound_order(idempotency_key,request_hash,created_by,remark,received_at) VALUES (CAST(#{idempotencyKey} AS UUID),#{requestHash},#{createdBy},#{remark},#{receivedAt})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertOrder(InboundOrder order);
 @Select("SELECT order_no FROM inbound_order WHERE id=#{id}") String orderNo(Long id);
 @Insert("INSERT INTO inbound_order_line(order_id,line_no,item_id,quantity,unit_price_snapshot,currency_snapshot,item_code_snapshot,item_name_snapshot,spec_snapshot,unit_snapshot,location,expire_date,remark) VALUES (#{orderId},#{lineNo},#{itemId},#{quantity},#{unitPriceSnapshot},#{currencySnapshot},#{itemCodeSnapshot},#{itemNameSnapshot},#{specSnapshot},#{unitSnapshot},#{location},#{expireDate},#{remark})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertLine(InboundLine line);
 @Insert("INSERT INTO stock_batch(item_id,inbound_line_id,initial_quantity,quantity,unit_price_snapshot,received_at,expire_date,location,remark,created_by) VALUES (#{itemId},#{inboundLineId},#{initialQuantity},#{quantity},#{unitPriceSnapshot},#{receivedAt},#{expireDate},#{location},#{remark},#{createdBy})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertBatch(StockBatch batch);
 @Insert("INSERT INTO stock_transaction(batch_id,txn_type,qty,before_quantity,after_quantity,user_id,inbound_order_id,reference_no,remark) VALUES (#{batchId},'IN',#{qty},#{beforeQuantity},#{afterQuantity},#{userId},#{inboundOrderId},#{referenceNo},#{remark})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertTransaction(StockTransaction transaction);
 List<StockRow> stocks(@Param("q") StockQuery query);
 long countStocks(@Param("q") StockQuery query);
 StockRow stock(Long id);
 StockCounts counts();
 List<StockSummary.Money> stockValues();
 List<BatchRow> batches(@Param("itemId") Long itemId,@Param("q") StockQuery query);
 long countBatches(Long itemId);
 BatchRow batch(String code);
 List<InboundRow> orders(@Param("q") StockQuery query,@Param("fromTime") java.time.OffsetDateTime fromTime,@Param("toTime") java.time.OffsetDateTime toTime);
 long countOrders(@Param("q") StockQuery query,@Param("fromTime") java.time.OffsetDateTime fromTime,@Param("toTime") java.time.OffsetDateTime toTime);
 InboundRow order(Long id);
 List<BatchRow> orderLines(Long orderId);
 List<TransactionRow> transactions(@Param("q") StockQuery query,@Param("viewer") Long viewer,@Param("fromTime") java.time.OffsetDateTime fromTime,@Param("toTime") java.time.OffsetDateTime toTime);
 long countTransactions(@Param("q") StockQuery query,@Param("viewer") Long viewer,@Param("fromTime") java.time.OffsetDateTime fromTime,@Param("toTime") java.time.OffsetDateTime toTime);
 List<TransactionRow> recentTransactions();
}

