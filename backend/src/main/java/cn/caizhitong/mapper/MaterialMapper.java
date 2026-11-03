package cn.caizhitong.mapper;
import cn.caizhitong.entity.MaterialItem;
import cn.caizhitong.dto.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
public interface MaterialMapper extends BaseMapper<MaterialItem> {
    List<MaterialItem> query(@Param("q") MaterialQuery query);
    long countQuery(@Param("q") MaterialQuery query);
    MaterialItem detail(@Param("id") Long id);
    @Select("SELECT id FROM material_item WHERE id=#{id} FOR UPDATE")
    Long lockItem(Long id);
    @Select("SELECT EXISTS(SELECT 1 FROM stock_batch WHERE item_id=#{id})")
    boolean hasBatches(Long id);
    @Select("SELECT category_id, brand, model, count(*) AS material_count FROM material_item GROUP BY category_id, brand, model ORDER BY category_id, brand NULLS FIRST, model NULLS FIRST")
    List<MaterialFacet> facets();
}

