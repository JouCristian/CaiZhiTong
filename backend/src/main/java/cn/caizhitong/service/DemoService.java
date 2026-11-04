package cn.caizhitong.service;

import cn.caizhitong.dto.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Deliberately separate SQL surface: all joins are exclusively between demo_* tables. */
@Service @Transactional(readOnly=true)
public class DemoService {
    private final NamedParameterJdbcTemplate db;
    public DemoService(NamedParameterJdbcTemplate db){this.db=db;}
    private static final String CATALOG="""
        WITH catalog AS (
          SELECT m.id,m.category_id,m.material_code,m.name,m.brand,m.model,m.spec,m.unit,
            m.unit_price,m.currency,m.safe_stock,m.description,c.name category_name,
            COALESCE(s.quantity,0) quantity,COALESCE(s.value,0) stock_value,
            CASE WHEN COALESCE(s.quantity,0)=0 THEN 'ZERO'
                 WHEN s.quantity<m.safe_stock THEN 'LOW' ELSE 'NORMAL' END stock_status
          FROM demo_material m JOIN demo_category c ON c.id=m.category_id
          LEFT JOIN (SELECT material_id,SUM(quantity) quantity,SUM(quantity*unit_price) value
            FROM demo_stock_batch GROUP BY material_id) s ON s.material_id=m.id
        )
        """;
    private static final String FIELDS="""
        id,material_code "materialCode",name,brand,model,spec,unit,
        category_id "categoryId",category_name "categoryName",unit_price::TEXT "unitPrice",
        currency,safe_stock::TEXT "safeStock",description,quantity::TEXT quantity,
        stock_value::TEXT "stockValue",stock_status "stockStatus"
        """;
    public List<Map<String,Object>> categories(){return db.queryForList("SELECT id,name FROM demo_category ORDER BY id",Map.of());}
    public PageResult<Map<String,Object>> materials(DemoQuery q){
        var params=new MapSqlParameterSource().addValue("keyword",q.pattern()).addValue("category",q.categoryId())
            .addValue("status",q.stockStatus()).addValue("limit",q.pageSize()).addValue("offset",q.offset());
        String where=" FROM catalog WHERE (CAST(:keyword AS TEXT) IS NULL OR name ILIKE :keyword OR material_code ILIKE :keyword OR model ILIKE :keyword) AND (CAST(:category AS BIGINT) IS NULL OR category_id=:category) AND (CAST(:status AS TEXT) IS NULL OR stock_status=:status)";
        Long total=db.queryForObject(CATALOG+"SELECT COUNT(*)"+where,params,Long.class);
        var rows=db.queryForList(CATALOG+"SELECT "+FIELDS+where+" ORDER BY id LIMIT :limit OFFSET :offset",params);
        return new PageResult<>(rows,total,q.pageNumber(),q.pageSize());
    }
    public Map<String,Object> detail(long id){
        var rows=db.queryForList(CATALOG+"SELECT "+FIELDS+" FROM catalog WHERE id=:id",Map.of("id",id));
        if(rows.isEmpty())throw new BusinessException(404,"演示材料不存在");
        var result=new LinkedHashMap<>(rows.get(0));
        result.put("batches",db.queryForList("SELECT id,batch_code \"batchCode\",quantity::TEXT quantity,initial_quantity::TEXT \"initialQuantity\",unit_price::TEXT \"unitPrice\",location,received_on::TEXT \"receivedOn\" FROM demo_stock_batch WHERE material_id=:id ORDER BY id",Map.of("id",id)));
        return result;
    }
    public Map<String,Object> batch(long id){
        var rows=db.queryForList("""
            SELECT b.id,b.batch_code "batchCode",b.material_id "materialId",m.name "materialName",
              m.unit,m.currency,b.quantity::TEXT quantity,b.initial_quantity::TEXT "initialQuantity",
              b.unit_price::TEXT "unitPrice",b.location,b.received_on::TEXT "receivedOn"
            FROM demo_stock_batch b JOIN demo_material m ON m.id=b.material_id WHERE b.id=:id
            """,Map.of("id",id));
        if(rows.isEmpty())throw new BusinessException(404,"演示批次不存在");
        var result=new LinkedHashMap<>(rows.get(0));
        result.put("events",db.queryForList("SELECT id,event_type \"eventType\",quantity::TEXT quantity,occurred_on::TEXT \"occurredOn\",description FROM demo_stock_event WHERE batch_id=:id ORDER BY occurred_on,id",Map.of("id",id)));
        return result;
    }
    public Map<String,Object> dashboard(){
        var metrics=db.queryForMap(CATALOG+"SELECT COUNT(*) materials,COUNT(*) FILTER(WHERE quantity>0) stocked,COUNT(*) FILTER(WHERE stock_status='LOW') low,COUNT(*) FILTER(WHERE stock_status='ZERO') zero FROM catalog",Map.of());
        var values=db.queryForList(CATALOG+"SELECT currency,SUM(stock_value)::TEXT value FROM catalog GROUP BY currency ORDER BY currency",Map.of());
        var distribution=db.queryForList(CATALOG+"SELECT category_name name,COUNT(*) count FROM catalog GROUP BY category_id,category_name ORDER BY category_id",Map.of());
        return Map.of("scope","SYNTHETIC_DEMO","readOnly",true,"metrics",metrics,"values",values,"categories",distribution,"batchCount",db.queryForObject("SELECT COUNT(*) FROM demo_stock_batch",Map.of(),Long.class));
    }
}
