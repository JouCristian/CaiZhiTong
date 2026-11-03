package cn.caizhitong.mapper;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.StockBatch;
import org.apache.ibatis.annotations.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
public interface IssueMapper {
 @Select("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(#{key},2604))") Integer lockRequest(String key);
 @Select("SELECT *,username_snapshot AS account_name,user_name_snapshot AS recipient_name FROM issue_order WHERE idempotency_key=CAST(#{key} AS UUID)") IssueOrderRow byKey(String key);
 @Select("SELECT item_id FROM stock_batch WHERE id=#{id}") Long batchItem(Long id);
 @Select("SELECT * FROM stock_batch WHERE id=#{id} FOR UPDATE") StockBatch lockBatch(Long id);
 @Update("UPDATE stock_batch SET quantity=quantity-#{qty} WHERE id=#{id} AND quantity>=#{qty}")
 int deduct(@Param("id")Long id,@Param("qty")BigDecimal qty);
 @Insert("INSERT INTO issue_monthly_usage(user_id,material_id,month_start) VALUES(#{userId},#{materialId},#{month}) ON CONFLICT DO NOTHING")
 void ensureUsage(@Param("userId")Long userId,@Param("materialId")Long materialId,@Param("month")LocalDate month);
 @Select("SELECT used_quantity FROM issue_monthly_usage WHERE user_id=#{userId} AND material_id=#{materialId} AND month_start=#{month} FOR UPDATE")
 BigDecimal lockUsage(@Param("userId")Long userId,@Param("materialId")Long materialId,@Param("month")LocalDate month);
 @Select("SELECT COALESCE((SELECT used_quantity FROM issue_monthly_usage WHERE user_id=#{userId} AND material_id=#{materialId} AND month_start=#{month}),0)")
 BigDecimal usage(@Param("userId")Long userId,@Param("materialId")Long materialId,@Param("month")LocalDate month);
 @Update("UPDATE issue_monthly_usage SET used_quantity=used_quantity+#{qty},updated_at=#{now} WHERE user_id=#{userId} AND material_id=#{materialId} AND month_start=#{month} AND (CAST(#{limit} AS NUMERIC) IS NULL OR used_quantity+#{qty}<=CAST(#{limit} AS NUMERIC))")
 int consume(@Param("userId")Long userId,@Param("materialId")Long materialId,@Param("month")LocalDate month,@Param("qty")BigDecimal qty,@Param("limit")BigDecimal limit,@Param("now")OffsetDateTime now);
 @Update("UPDATE material_item SET monthly_issue_limit=#{limit},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
 void setLimit(@Param("id")Long id,@Param("limit")BigDecimal limit);
 @Insert("INSERT INTO issue_order(user_id,username_snapshot,user_name_snapshot,project_no,usage,remark,currency,total_amount,idempotency_key,request_hash,month_start,business_zone,created_at,course) VALUES(#{userId},#{accountName},#{recipientName},#{projectNo},#{usage},#{remark},#{currency},#{totalAmount},CAST(#{idempotencyKey} AS UUID),#{requestHash},#{monthStart},#{businessZone},#{createdAt},#{course})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertOrder(IssueOrderRow row);
 @Select("SELECT issue_no FROM issue_order WHERE id=#{id}") String issueNo(Long id);
 @Insert("INSERT INTO issue_order_line(issue_order_id,line_no,material_id,batch_id,material_code,material_name,spec,unit,batch_code,quantity,unit_price_snapshot,amount,created_at) VALUES(#{issueOrderId},#{lineNo},#{materialId},#{batchId},#{materialCode},#{materialName},#{spec},#{unit},#{batchCode},#{quantity},#{unitPriceSnapshot},#{amount},#{createdAt})")
 @Options(useGeneratedKeys=true,keyProperty="id",keyColumn="id") void insertLine(IssueLineRow row);
 @Insert("INSERT INTO stock_transaction(batch_id,txn_type,qty,before_quantity,after_quantity,user_id,reference_no,project_no,usage,remark,issue_order_id,issue_line_id,created_at) VALUES(#{line.batchId},'OUT',#{line.quantity},#{line.beforeQuantity},#{line.afterQuantity},#{order.userId},#{order.issueNo},#{order.projectNo},#{order.usage},#{order.remark},#{order.id},#{line.id},#{order.createdAt})")
 void insertOut(@Param("line")IssueLineRow line,@Param("order")IssueOrderRow order);
 IssueOrderRow order(Long id);
 List<IssueLineRow> lines(Long id);
 List<IssueOrderRow> query(@Param("q")IssueQuery q,@Param("owner")Long owner,@Param("fromTime")OffsetDateTime fromTime,@Param("toTime")OffsetDateTime toTime);
 long count(@Param("q")IssueQuery q,@Param("owner")Long owner,@Param("fromTime")OffsetDateTime fromTime,@Param("toTime")OffsetDateTime toTime);
 List<BatchRow> available(@Param("id")Long id,@Param("size")int size,@Param("offset")long offset);
 @Select("SELECT count(*) FROM stock_batch WHERE item_id=#{id} AND quantity>0") long countAvailable(Long id);
}



