package cn.caizhitong.config;
import cn.caizhitong.entity.SysUser;
import cn.caizhitong.mapper.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
@Profile("dev")
@ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class DevAccountInitializer implements ApplicationRunner {
    private final UserMapper users; private final PasswordEncoder passwords;
    private final String admin; private final String lab; private final String student;
    public DevAccountInitializer(UserMapper users, PasswordEncoder passwords,
        @Value("${app.demo.admin-password}") String admin, @Value("${app.demo.lab-password}") String lab,
        @Value("${app.demo.student-password}") String student) {
        this.users=users; this.passwords=passwords; this.admin=admin; this.lab=lab; this.student=student;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        seed("admin","系统管理员","ADMIN",admin);
        seed("lab","林实验员","LAB_TECH",lab);
        seed("student","陈同学","STUDENT",student);
    }
    private void seed(String username,String name,String role,String password) {
        if (password.length()<12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Development demo passwords must be 12..72 bytes");
        SysUser user=users.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername,username));
        if (user==null) {
            user=new SysUser(); user.setUsername(username); user.setDisplayName(name);
            user.setPasswordHash(passwords.encode(password)); user.setEnabled(true); users.insert(user);
            users.assignRole(user.getId(),role);
        }
    }
}

