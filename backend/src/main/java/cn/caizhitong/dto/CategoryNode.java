package cn.caizhitong.dto;
import java.time.OffsetDateTime;
import java.util.List;
public record CategoryNode(Long id, Long parentId, String name, String description, int sortOrder,
    OffsetDateTime createdAt, OffsetDateTime updatedAt, long materialCount, List<CategoryNode> children) {}

