package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class ReportService {
 public record Bundle(Map<String,Object> meta,List<Map<String,Object>> rows,List<Map<String,Object>> totals,long total){}
 private final OperationsMapper db;private final Clock clock;private final SystemSettingsService settings;
 public ReportService(OperationsMapper db,Clock clock,SystemSettingsService settings){this.db=db;this.clock=clock;this.settings=settings;}
 public static String type(String type){if(!Set.of("semester","funding","personal").contains(type))throw new BusinessException(404,"报表不存在");return type;}
 private static final String LINE="l.id AS line_id,l.material_id,l.material_code,l.material_name,l.spec,l.unit,o.currency,l.unit_price_snapshot,o.project_no,o.usage,COALESCE(o.course,'未关联课程') AS course,o.user_id,o.username_snapshot AS account_name,o.user_name_snapshot AS recipient_name,o.issue_no,l.batch_code,o.created_at AS issued_at,l.quantity AS original_quantity,COALESCE((SELECT sum(x.quantity) FROM return_order_line x WHERE x.issue_order_line_id=l.id),0) AS returned_total";
 private static final String EVENTS="WITH events AS (SELECT "+LINE+",o.created_at AS event_at,'OUT' AS kind,l.quantity AS issue_quantity,0::numeric AS return_quantity,0::numeric AS scrap_quantity,l.amount AS issue_amount,0::numeric AS return_amount,0::numeric AS scrap_amount FROM issue_order_line l JOIN issue_order o ON o.id=l.issue_order_id UNION ALL SELECT "+LINE+",r.created_at,'RETURN',0::numeric,rl.quantity,0::numeric,0::numeric,rl.quantity*l.unit_price_snapshot,0::numeric FROM return_order_line rl JOIN return_order r ON r.id=rl.return_order_id JOIN issue_order_line l ON l.id=rl.issue_order_line_id JOIN issue_order o ON o.id=l.issue_order_id UNION ALL SELECT NULL::bigint,b.item_id,i.item_code_snapshot,i.item_name_snapshot,i.spec_snapshot,i.unit_snapshot,i.currency_snapshot,b.unit_price_snapshot,NULL::varchar,NULL::varchar,'未关联课程',NULL::bigint,NULL::varchar,NULL::varchar,NULL::varchar,b.batch_code,NULL::timestamptz,0::numeric,0::numeric,s.created_at,'SCRAP',0::numeric,0::numeric,s.quantity,0::numeric,0::numeric,s.quantity*b.unit_price_snapshot FROM scrap_order s JOIN stock_batch b ON b.id=s.batch_id JOIN inbound_order_line i ON i.id=b.inbound_line_id),filtered AS (SELECT e.*,COALESCE(p.name||' / '||c.name,c.name) AS category_path FROM events e JOIN material_item m ON m.id=e.material_id JOIN material_category c ON c.id=m.category_id LEFT JOIN material_category p ON p.id=c.parent_id WHERE ";
 private static final String SUMS="sum(issue_quantity) AS issue_quantity,sum(return_quantity) AS return_quantity,sum(issue_quantity)-sum(return_quantity) AS net_quantity,sum(issue_amount) AS issue_amount,sum(return_amount) AS return_amount,sum(issue_amount)-sum(return_amount) AS net_amount,sum(scrap_quantity) AS scrap_quantity,sum(scrap_amount) AS scrap_amount";
 private Bundle query(String type,ReportQuery q,UserPrincipal user,boolean export){
  type(type);boolean manager=StockLifecycleService.manages(user);boolean aggregate=user.roles().stream().anyMatch(r->Set.of("ADMIN","LAB_TECH","TEACHER","PURCHASER").contains(r));
  if(type.equals("funding")&&!manager)throw new BusinessException(403,"经费对应明细仅管理员和实验员可查看");if(type.equals("semester")&&!aggregate)throw new BusinessException(403,"当前角色仅可查看个人领用报表");
  Long owner=null;if(type.equals("personal")){owner=q.userId()==null?user.user().getId():q.userId();if(!manager&&!owner.equals(user.user().getId()))throw new BusinessException(403,"只能查看和导出本人的个人报表");}else if(q.userId()!=null){if(!manager)throw new BusinessException(403,"不能按其他用户查询报表");owner=q.userId();}
  var zone=settings.zone();var period=settings.period(q.academicYear(),q.term());int year=period.year();String term=period.term();
  LocalDate from=q.from()==null?period.from():q.from();LocalDate to=q.to()==null?period.to():q.to();
  var where=new StringBuilder("e.event_at>=? AND e.event_at<?");var args=new ArrayList<Object>();args.add(from.atStartOfDay(zone).toOffsetDateTime());args.add(to.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
  if(!type.equals("semester"))where.append(" AND e.kind<>'SCRAP'");if(owner!=null){where.append(" AND e.user_id=?");args.add(owner);}
  if(q.categoryId()!=null){where.append(" AND m.category_id IN (SELECT id FROM material_category WHERE id=? OR parent_id=?)");args.add(q.categoryId());args.add(q.categoryId());}
  if(q.keyword()!=null&&!q.keyword().isBlank()){where.append(" AND (e.material_name ILIKE ? ESCAPE '\\' OR e.material_code ILIKE ? ESCAPE '\\')");args.add(OperationQuery.pattern(q.keyword()));args.add(OperationQuery.pattern(q.keyword()));}
  if(q.course()!=null&&!q.course().isBlank()){where.append(" AND e.course ILIKE ? ESCAPE '\\'");args.add(OperationQuery.pattern(q.course()));}
  if(q.projectNo()!=null&&!q.projectNo().isBlank()){where.append(" AND e.project_no=?");args.add(q.projectNo().trim());}
  String group="material_id,material_code,material_name,spec,unit,currency,category_path";
  if(!type.equals("semester"))group+=",line_id,unit_price_snapshot,project_no,usage,course,user_id,account_name,recipient_name,issue_no,batch_code,issued_at,original_quantity,returned_total";
  String sql=EVENTS+where+") SELECT "+group+","+SUMS+",max(event_at) AS last_event_at FROM filtered GROUP BY "+group;
  long total=db.count("SELECT count(*) FROM ("+sql+") report",args.toArray());if(export&&total>10000)throw new BusinessException(400,"导出最多 10000 行，请缩小统计时间或筛选范围");
  var totals=db.rows("SELECT currency,unit,"+SUMS+" FROM ("+sql+") report GROUP BY currency,unit ORDER BY currency,unit",args.toArray());
  String order=type.equals("semester")?" ORDER BY material_code,currency,material_name,spec":" ORDER BY issued_at DESC,line_id DESC";var pageArgs=new ArrayList<>(args);pageArgs.add(export?10000:q.s());pageArgs.add(export?0:q.offset());var rows=db.rows(sql+order+" LIMIT ? OFFSET ?",pageArgs.toArray());
  String title=switch(type){case"semester"->"学期耗材汇总表";case"funding"->"经费对应表";default->"个人领用明细";};String label=q.from()!=null?from+"至"+to:(term.equals("AUTUMN")?year+"秋":(year+1)+"春");
  var meta=new LinkedHashMap<String,Object>();meta.put("type",type);meta.put("title",title);meta.put("academicYear",year);meta.put("term",term);meta.put("semesterConfigured",period.configured());meta.put("from",from.toString());meta.put("to",to.toString());meta.put("businessZone",zone.getId());meta.put("generatedAt",clock.instant().toString());meta.put("fileStem",title+"_"+label);meta.put("notes",List.of("净消耗=本期领用−本期归还，按实际归还日冲减原项目与单价，允许跨期负数。","仓库报废独立统计，不重复计入用户净消耗；课程/项目筛选不分摊无归属报废。","币种与计量单位分别汇总；分类按当前归类，材料描述和价格使用业务快照。"));if(owner!=null)meta.put("userId",owner);
  return new Bundle(meta,rows,totals,total);
 }
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public Map<String,Object> preview(String type,ReportQuery q,UserPrincipal user){var b=query(type,q,user,false);return api(Map.of("meta",b.meta(),"records",b.rows(),"totals",b.totals(),"total",b.total(),"page",q.p(),"size",q.s()));}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public Bundle export(String type,ReportQuery q,UserPrincipal user){return query(type,q,user,true);}
 public List<Map<String,Object>> users(String keyword){String pattern=OperationQuery.pattern(keyword);return db.rows("SELECT id,username,display_name FROM sys_user WHERE (?::text IS NULL OR username ILIKE ? ESCAPE '\\' OR display_name ILIKE ? ESCAPE '\\') ORDER BY id LIMIT 50",pattern,pattern,pattern).stream().map(OperationsMapper::api).toList();}

 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> dashboard(UserPrincipal user){
  var zone=settings.zone();var today=LocalDate.now(clock.withZone(zone));var from=today.withDayOfMonth(1);
  boolean manager=StockLifecycleService.manages(user),aggregate=user.roles().stream().anyMatch(r->Set.of("ADMIN","LAB_TECH","TEACHER","PURCHASER").contains(r));boolean personal=!aggregate&&user.roles().contains("STUDENT");
  String scope=manager?"ALL":aggregate?"AGGREGATE":personal?"PERSONAL":"PUBLIC";
  var result=new LinkedHashMap<String,Object>();result.put("scope",scope);result.put("from",from);result.put("to",today);result.put("businessZone",zone.getId());result.put("canViewPeople",manager);result.put("fundingDimension",manager||personal?"PROJECT":"CATEGORY");
  result.put("stockValues",db.rows("SELECT l.currency_snapshot AS currency,sum(b.quantity*b.unit_price_snapshot) AS amount FROM stock_batch b JOIN inbound_order_line l ON l.id=b.inbound_line_id GROUP BY l.currency_snapshot ORDER BY l.currency_snapshot"));
  result.put("lowStockCount",db.count("SELECT count(*) FROM material_item m WHERE m.safe_stock>0 AND COALESCE((SELECT sum(quantity) FROM stock_batch b WHERE b.item_id=m.id),0)<m.safe_stock"));
  result.put("notes",List.of("领用与归还使用原明细单价，按实际业务日期进入本月；净消耗允许为负。","数量按单位与币种分别展示；库存价值及资金占比按币种分组。","资金占比为领用金额占比（未扣归还）；仓库报废单独统计。"));
  if(!aggregate&&!personal){for(String key:List.of("monthTotals","topMaterials","trend","funding","ranking"))result.put(key,List.of());return api(result);}
  var args=new ArrayList<Object>();args.add(from.atStartOfDay(zone).toOffsetDateTime());args.add(today.plusDays(1).atStartOfDay(zone).toOffsetDateTime());
  String filtered=EVENTS+"e.event_at>=? AND e.event_at<?";if(personal){filtered+=" AND e.user_id=?";args.add(user.user().getId());}filtered+=") ";
  result.put("monthTotals",db.rows(filtered+"SELECT currency,unit,"+SUMS+" FROM filtered GROUP BY currency,unit ORDER BY currency,unit",args.toArray()));
  result.put("topMaterials",db.rows(filtered+",ranked AS (SELECT material_id,material_code,material_name,currency,unit,"+SUMS+",row_number() OVER(PARTITION BY currency,unit ORDER BY sum(issue_quantity)-sum(return_quantity) DESC,material_code) AS position FROM filtered WHERE kind<>'SCRAP' GROUP BY material_id,material_code,material_name,currency,unit) SELECT * FROM ranked WHERE position<=10 ORDER BY currency,unit,position",args.toArray()));
  var dailyArgs=new ArrayList<>(args);dailyArgs.add(zone.getId());result.put("trend",db.rows(filtered+"SELECT timezone(?,event_at)::date AS day,currency,unit,"+SUMS+" FROM filtered GROUP BY day,currency,unit ORDER BY day,currency,unit",dailyArgs.toArray()));
  String fundingKey=manager||personal?"project_no":"category_path";
  result.put("funding",db.rows(filtered+",funds AS(SELECT "+fundingKey+" AS label,currency,sum(issue_amount) AS amount FROM filtered GROUP BY "+fundingKey+",currency HAVING sum(issue_amount)>0) SELECT *,round(amount*100/sum(amount) OVER(PARTITION BY currency),2) AS percentage FROM funds ORDER BY currency,amount DESC,label",args.toArray()));
  result.put("ranking",manager?db.rows(filtered+",ranked AS(SELECT u.id AS user_id,u.display_name AS user_name,f.currency,f.unit,sum(f.issue_quantity) AS issue_quantity,sum(f.issue_amount) AS issue_amount,row_number() OVER(PARTITION BY f.currency,f.unit ORDER BY sum(f.issue_quantity) DESC,u.id) AS position FROM filtered f JOIN sys_user u ON u.id=f.user_id GROUP BY u.id,u.display_name,f.currency,f.unit HAVING sum(f.issue_quantity)>0) SELECT * FROM ranked WHERE position<=10 ORDER BY currency,unit,position",args.toArray()):List.of());
  return api(result);
 }
}
