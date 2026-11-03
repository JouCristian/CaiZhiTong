package cn.caizhitong.entity;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("sys_user_role")
public record SysUserRole(Long userId, Long roleId) {}

