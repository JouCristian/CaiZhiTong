package cn.caizhitong.security;
import cn.caizhitong.entity.SysUser;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;
public record UserPrincipal(SysUser user, List<String> roles) implements java.security.Principal {
    public String getName() { return user.getUsername(); }
    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }
}

