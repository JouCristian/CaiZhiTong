package cn.caizhitong.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("sys_role")
public record SysRole(@TableId(type = IdType.AUTO) Long id, String code, String name) {}

