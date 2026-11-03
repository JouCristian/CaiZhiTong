package cn.caizhitong.mapper;
import cn.caizhitong.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
public interface UserMapper extends BaseMapper<SysUser> {
    @Update("UPDATE sys_user SET last_login_at=CURRENT_TIMESTAMP WHERE id=#{id}") void recordLogin(Long id);
    @Select("SELECT r.code FROM sys_role r JOIN sys_user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} ORDER BY r.id")
    List<String> findRoles(Long userId);
    @Insert("INSERT INTO sys_user_role(user_id,role_id) SELECT #{userId},id FROM sys_role WHERE code=#{role} ON CONFLICT DO NOTHING")
    void assignRole(@Param("userId") Long userId, @Param("role") String role);
}

