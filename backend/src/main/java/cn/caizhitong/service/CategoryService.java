package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.MaterialCategory;
import cn.caizhitong.mapper.CategoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service
public class CategoryService {
    private final CategoryMapper mapper;
    public CategoryService(CategoryMapper mapper) { this.mapper=mapper; }
    @Transactional(readOnly=true)
    public List<CategoryNode> tree() {
        List<MaterialCategory> rows=mapper.treeRows();
        return children(rows,null);
    }
    private List<CategoryNode> children(List<MaterialCategory> rows,Long parent) {
        return rows.stream().filter(c -> Objects.equals(c.getParentId(),parent)).map(c ->
            new CategoryNode(c.getId(),c.getParentId(),c.getName(),c.getDescription(),c.getSortOrder(),
                c.getCreatedAt(),c.getUpdatedAt(),c.getMaterialCount(),children(rows,c.getId()))).toList();
    }
    public MaterialCategory require(Long id) {
        MaterialCategory category=mapper.selectById(id);
        if (category==null) throw new BusinessException(404,"所属类目不存在，请重新选择");
        return category;
    }
    @Transactional
    public MaterialCategory create(CategoryInput input) {
        mapper.lockTree(); validateParent(null,input.parentId()); validateName(null,input);
        MaterialCategory row=new MaterialCategory();
        apply(row,input); mapper.insert(row); return require(row.getId());
    }
    @Transactional
    public MaterialCategory update(Long id,CategoryInput input) {
        mapper.lockTree(); MaterialCategory row=require(id);
        validateParent(id,input.parentId());
        validateName(id,input);
        if (input.parentId()!=null && mapper.selectCount(Wrappers.<MaterialCategory>lambdaQuery().eq(MaterialCategory::getParentId,id))>0)
            throw new BusinessException(400,"该类目有子类，移动后将超过两层基础分类");
        apply(row,input);
        // updateById normally skips null; explicitly clear parent when moved to root.
        mapper.update(null,Wrappers.<MaterialCategory>lambdaUpdate().eq(MaterialCategory::getId,id)
            .set(MaterialCategory::getParentId,row.getParentId()).set(MaterialCategory::getName,row.getName())
            .set(MaterialCategory::getDescription,row.getDescription()).set(MaterialCategory::getSortOrder,row.getSortOrder())
            .set(MaterialCategory::getUpdatedAt,row.getUpdatedAt()));
        return require(id);
    }
    private void validateParent(Long id,Long parentId) {
        if (parentId==null) return;
        MaterialCategory parent=require(parentId);
        Set<Long> visited=new HashSet<>();
        MaterialCategory cursor=parent;
        while (cursor!=null) {
            if (Objects.equals(cursor.getId(),id) || !visited.add(cursor.getId()))
                throw new BusinessException(400,"类目不能引用自身或其子节点，禁止循环引用");
            cursor=cursor.getParentId()==null?null:require(cursor.getParentId());
        }
        if (parent.getParentId()!=null)
            throw new BusinessException(400,"基础分类最多两层；品牌、型号由材料属性生成第三、第四层");
    }
    private void apply(MaterialCategory row,CategoryInput input) {
        row.setParentId(input.parentId()); row.setName(input.name().trim());
        row.setDescription(input.description()==null?null:input.description().trim());
        row.setSortOrder(input.sortOrder()); row.setUpdatedAt(OffsetDateTime.now());
    }
    private void validateName(Long id,CategoryInput input) {
        long count=mapper.selectCount(Wrappers.<MaterialCategory>lambdaQuery()
            .isNull(input.parentId()==null,MaterialCategory::getParentId)
            .eq(input.parentId()!=null,MaterialCategory::getParentId,input.parentId())
            .ne(id!=null,MaterialCategory::getId,id).apply("lower(name)=lower({0})",input.name().trim()));
        if (count>0) throw new BusinessException(409,"同一父级下已存在同名类目");
    }
    @Transactional
    public void delete(Long id) {
        mapper.lockTree(); require(id);
        if (mapper.selectCount(Wrappers.<MaterialCategory>lambdaQuery().eq(MaterialCategory::getParentId,id))>0)
            throw new BusinessException(409,"该类目仍有子类，不能删除");
        MaterialCategory row=mapper.treeRows().stream().filter(c -> c.getId().equals(id)).findFirst().orElseThrow();
        if (row.getMaterialCount()>0) throw new BusinessException(409,"该类目仍有关联材料（含归档记录），不能删除");
        mapper.deleteById(id);
    }
}

