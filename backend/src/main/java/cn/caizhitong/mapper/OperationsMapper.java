package cn.caizhitong.mapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.temporal.TemporalAccessor;
import java.util.*;
/** Prepared SQL projections share Spring's existing datasource / transaction. */
@Repository
public class OperationsMapper {
 public final JdbcTemplate jdbc;
 public OperationsMapper(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Map<String,Object> one(String sql,Object... args){var rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.get(0);}
 public List<Map<String,Object>> rows(String sql,Object... args){return jdbc.queryForList(sql,args);}
 public int update(String sql,Object... args){return jdbc.update(sql,args);}
 public long count(String sql,Object... args){Long n=jdbc.queryForObject(sql,Long.class,args);return n==null?0:n;}
 public static long id(Map<String,Object> row,String field){return ((Number)row.get(field)).longValue();}
 public static BigDecimal num(Map<String,Object> row,String field){Object n=row.get(field);return n==null?BigDecimal.ZERO:new BigDecimal(n.toString());}
 public static String text(Map<String,Object> row,String field){Object s=row.get(field);return s==null?null:s.toString();}
 public static Map<String,Object> api(Map<String,Object> row){
  var result=new LinkedHashMap<String,Object>();if(row==null)return result;
  row.forEach((k,v)->{if(v==null||Set.of("request_hash","idempotency_key","storage_key","sha256").contains(k))return;StringBuilder name=new StringBuilder();boolean upper=false;for(char c:k.toCharArray()){if(c=='_'){upper=true;continue;}name.append(upper?Character.toUpperCase(c):c);upper=false;}result.put(name.toString(),value(v));});return result;
 }
 public static Object value(Object v){if(v instanceof BigDecimal n)return n.toPlainString();if(v instanceof Timestamp t)return t.toInstant().toString();if(v instanceof TemporalAccessor||v instanceof UUID)return v.toString();if(v instanceof Map<?,?> m){var r=new LinkedHashMap<String,Object>();m.forEach((k,x)->r.put(k.toString(),x));return api(r);}if(v instanceof Collection<?> xs)return xs.stream().map(OperationsMapper::value).toList();return v;}
}
