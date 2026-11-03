package cn.caizhitong.mapper;
import cn.caizhitong.entity.MaterialCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
public interface CategoryMapper extends BaseMapper<MaterialCategory> {
    @Select("SELECT c.*, (SELECT count(*) FROM material_item m WHERE m.category_id=c.id) AS material_count FROM material_category c ORDER BY sort_order, id")
    List<MaterialCategory> treeRows();
    // Serialize tree structural mutations, including concurrent reparent operations.
    @Select("SELECT 1 FROM pg_advisory_xact_lock(260203, 1)")
    Integer lockTree();
}

