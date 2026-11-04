package cn.caizhitong.service;

import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;

@Service
public class GithubIdentityService {
    private final OperationsMapper db;
    private final UserService users;
    private final PasswordEncoder passwords;
    public GithubIdentityService(OperationsMapper db,UserService users,PasswordEncoder passwords){this.db=db;this.users=users;this.passwords=passwords;}

    @Transactional
    public UserPrincipal login(String providerId,String login,String name,String avatar){
        if(providerId==null || !providerId.matches("[1-9][0-9]{0,31}") || login==null || !login.matches("[A-Za-z0-9-]{1,39}"))
            throw new IllegalArgumentException("Invalid GitHub identity");
        // Serialize the stable identity before selecting/inserting. Unique constraints remain the final guard.
        db.one("SELECT pg_advisory_xact_lock(hashtextextended(?,2707))","github:"+providerId);
        var identity=db.one("SELECT i.user_id,u.enabled FROM oauth_identity i JOIN sys_user u ON u.id=i.user_id WHERE i.provider='github' AND i.provider_user_id=? FOR UPDATE OF u",providerId);
        long userId;
        if(identity==null){
            String display=name==null||name.isBlank()?login:name.replaceAll("\\p{Cntrl}","").strip();
            if(display.isBlank())display=login;
            display=display.substring(0,Math.min(64,display.length()));
            String username="github_"+UUID.randomUUID().toString().replace("-","");
            var user=db.one("INSERT INTO sys_user(username,display_name,password_hash,password_login_enabled,enabled) VALUES(?,?,?,false,true) RETURNING id",username,display,passwords.encode(UUID.randomUUID().toString()));
            userId=id(user,"id");
            if(db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='GUEST'",userId)!=1)throw new IllegalStateException("Guest role missing");
            db.update("INSERT INTO oauth_identity(provider,provider_user_id,user_id,provider_login,avatar_url) VALUES('github',?,?,?,?)",providerId,userId,login,safeAvatar(avatar));
        }else{
            if(!Boolean.TRUE.equals(identity.get("enabled")))throw new DisabledException("Account disabled");
            userId=id(identity,"user_id");
            // Never replace local roles, password, username, email or administrator-maintained name.
            db.update("UPDATE oauth_identity SET provider_login=?,avatar_url=?,updated_at=CURRENT_TIMESTAMP WHERE provider='github' AND provider_user_id=?",login,safeAvatar(avatar),providerId);
        }
        db.update("UPDATE sys_user SET last_login_at=CURRENT_TIMESTAMP WHERE id=?",userId);
        return users.findActive(userId);
    }
    static String safeAvatar(String avatar){
        if(avatar==null)return null;
        try{
            var u=java.net.URI.create(avatar);
            if("https".equals(u.getScheme()) && "avatars.githubusercontent.com".equals(u.getHost()) && (u.getPort()==-1||u.getPort()==443) && u.getUserInfo()==null && u.getFragment()==null && avatar.length()<=300 && u.getPath().matches("/u/[1-9][0-9]*"))return avatar;
        }catch(IllegalArgumentException ignored){}
        return null;
    }
}
