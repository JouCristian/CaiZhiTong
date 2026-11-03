package cn.caizhitong.config;

import cn.caizhitong.mapper.OperationsMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

/** No public registration endpoint. Enable only for the initial controlled boot. */
@Component
@ConditionalOnProperty(name="app.bootstrap.enabled", havingValue="true")
public class ProductionAdminInitializer implements ApplicationRunner {
    private final OperationsMapper db;
    private final PasswordEncoder passwords;
    private final String username;
    private final String password;
    public ProductionAdminInitializer(OperationsMapper db, PasswordEncoder passwords,
        @Value("${app.bootstrap.username:}") String username,
        @Value("${app.bootstrap.password:}") String password) {
        this.db=db; this.passwords=passwords; this.username=username; this.password=password;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) {
        db.one("SELECT pg_advisory_xact_lock(2606,1)");
        if(db.count("SELECT count(*) FROM sys_user u WHERE u.enabled AND EXISTS (SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='ADMIN')")>0) return;
        if(!username.matches("[A-Za-z0-9_.-]{3,64}") || password.length()<16 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalStateException("Initial administrator credentials are missing or invalid; provide a unique username and a 16..72 byte password in secure environment variables.");
        if(db.count("SELECT count(*) FROM sys_user WHERE username=?",username)>0)
            throw new IllegalStateException("Initial administrator username already exists; no account or password was changed.");
        if(db.count("SELECT count(*) FROM sys_role WHERE code='ADMIN'")!=1)
            throw new IllegalStateException("Administrator role missing; verify Flyway migration before initialization.");
        var created=db.one("INSERT INTO sys_user(username,display_name,password_hash,enabled) VALUES(?,?,?,true) RETURNING id",username,"系统管理员",passwords.encode(password));
        db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='ADMIN'",created.get("id"));
    }
}
