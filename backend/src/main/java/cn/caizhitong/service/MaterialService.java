package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.entity.MaterialItem;
import cn.caizhitong.mapper.MaterialMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.*;
@Service
public class MaterialService {
    private final MaterialMapper mapper;
    private final CategoryService categories;private final AlertService alerts;
    public MaterialService(MaterialMapper mapper,CategoryService categories,AlertService alerts) { this.mapper=mapper; this.categories=categories;this.alerts=alerts; }
    @Transactional(readOnly=true)
    public PageResult<MaterialItem> query(MaterialQuery query) {
        return new PageResult<>(mapper.query(query),mapper.countQuery(query),query.getPageNumber(),query.getPageSize());
    }
    public List<MaterialFacet> facets() { return mapper.facets(); }
    public long total() { return mapper.selectCount(null); }
    public MaterialItem detail(Long id) {
        MaterialItem row=mapper.detail(id);
        if (row==null) throw new BusinessException(404,"材料档案不存在");
        return row;
    }
    @Transactional
    public MaterialItem create(MaterialInput input) {
        checkCode(input.materialCode(),null);
        MaterialItem row=new MaterialItem(); apply(row,input); mapper.insert(row);alerts.reconcile(row.getId());
        return detail(row.getId());
    }
    @Transactional
    public MaterialItem update(Long id,MaterialInput input) {
        mapper.lockItem(id);
        MaterialItem row=detail(id); requireEditable(row); checkCode(input.materialCode(),row.getMaterialCode());
        if (!row.getUnit().equals(input.unit().trim()) && mapper.hasBatches(id))
            throw new BusinessException(409,"材料已有入库批次，不能修改计量单位；请建立新的材料档案");
        apply(row,input);
        // Explicit nullable assignments allow clearing brand/model/spec/remarks.
        mapper.update(null,Wrappers.<MaterialItem>lambdaUpdate().eq(MaterialItem::getId,id)
            .set(MaterialItem::getCategoryId,row.getCategoryId()).set(MaterialItem::getName,row.getName())
            .set(MaterialItem::getBrand,row.getBrand()).set(MaterialItem::getModel,row.getModel())
            .set(MaterialItem::getSpec,row.getSpec()).set(MaterialItem::getUnit,row.getUnit())
            .set(MaterialItem::getUnitPrice,row.getUnitPrice()).set(MaterialItem::getCurrency,row.getCurrency())
            .set(MaterialItem::getSafeStock,row.getSafeStock()).set(MaterialItem::getDescription,row.getDescription())
            .set(MaterialItem::getStatus,row.getStatus()).set(MaterialItem::getUpdatedAt,row.getUpdatedAt()));
        alerts.reconcile(id);return detail(id);
    }
    @Transactional
    public MaterialItem status(Long id,String status) {
        mapper.lockItem(id);
        MaterialItem row=detail(id);
        if ("ARCHIVED".equals(row.getStatus()) && "ARCHIVED".equals(status)) return row;
        requireEditable(row);
        mapper.update(null,Wrappers.<MaterialItem>lambdaUpdate().eq(MaterialItem::getId,id)
            .set(MaterialItem::getStatus,status).set(MaterialItem::getUpdatedAt,OffsetDateTime.now()));
        alerts.reconcile(id);return detail(id);
    }
    private void requireEditable(MaterialItem row) {
        if ("ARCHIVED".equals(row.getStatus())) throw new BusinessException(409,"材料已归档，历史档案仅供查看");
    }
    private void checkCode(String requested,String current) {
        if (requested==null || requested.equals(current)) return;
        if (mapper.selectCount(Wrappers.<MaterialItem>lambdaQuery().eq(MaterialItem::getMaterialCode,requested))>0)
            throw new BusinessException(409,"材料编码已存在");
        throw new BusinessException(400,"材料编码由系统自动生成，创建后不可修改");
    }
    private String optional(String value) { return value==null || value.isBlank()?null:value.trim(); }
    private void apply(MaterialItem row,MaterialInput input) {
        categories.require(input.categoryId());
        row.setCategoryId(input.categoryId()); row.setName(input.name().trim());
        row.setBrand(optional(input.brand())); row.setModel(optional(input.model())); row.setSpec(optional(input.spec()));
        row.setUnit(input.unit().trim()); row.setUnitPrice(input.unitPrice());
        row.setCurrency(input.currency()==null?"CNY":input.currency());
        row.setSafeStock(input.safeStock()); row.setStatus(input.status()==null?"ACTIVE":input.status());
        row.setDescription(optional(input.description())); row.setUpdatedAt(OffsetDateTime.now());
    }
}


