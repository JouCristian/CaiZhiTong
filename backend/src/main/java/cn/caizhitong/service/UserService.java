package cn.caizhitong.service;
import cn.caizhitong.dto.UserView;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import cn.caizhitong.security.UserPrincipal;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class UserService {
    private final UserMapper mapper;
    private final PasswordEncoder passwords;
    private final ModuleAccess access;
    private final String dummyHash;
    public UserService(UserMapper mapper, PasswordEncoder passwords, ModuleAccess access) {
        this.mapper=mapper; this.passwords=passwords; this.access=access;
        this.dummyHash=passwords.encode("timing-equalization-only");
    }
    public UserPrincipal login(String username, String password) {
        SysUser user=mapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        boolean matches=passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches || !Boolean.TRUE.equals(user.getEnabled()))
            throw new BadCredentialsException("用户名或密码错误");
        mapper.recordLogin(user.getId());
        return principal(user);
    }
    public UserPrincipal findActive(Long id) {
        SysUser user=mapper.selectById(id);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) throw new BadCredentialsException("登录状态已失效");
        return principal(user);
    }
    private UserPrincipal principal(SysUser user) { return new UserPrincipal(user, mapper.findRoles(user.getId())); }
    public UserView view(UserPrincipal p) {
        return new UserView(p.user().getId(), p.getName(), p.user().getDisplayName(), p.roles(), access.modules(p.roles()));
    }
}

