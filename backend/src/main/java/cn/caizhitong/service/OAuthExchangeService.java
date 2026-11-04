package cn.caizhitong.service;

import cn.caizhitong.dto.LoginResponse;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;

@Service
public class OAuthExchangeService {
    private final OperationsMapper db; private final UserService users; private final JwtService tokens;
    private static final SecureRandom RANDOM=new SecureRandom();
    public OAuthExchangeService(OperationsMapper db,UserService users,JwtService tokens){this.db=db;this.users=users;this.tokens=tokens;}
    public static String challenge(String verifier){return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));}
    private static byte[] digest(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String hash(String code){return HexFormat.of().formatHex(digest(code));}
    @Transactional public String create(UserPrincipal user,String challenge){
        if(challenge==null||!challenge.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid browser challenge");
        var active=users.findActive(user.user().getId());
        byte[] random=new byte[32];RANDOM.nextBytes(random);String code=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        db.update("DELETE FROM oauth_login_exchange WHERE expires_at<CURRENT_TIMESTAMP");
        db.update("INSERT INTO oauth_login_exchange(code_hash,user_id,verifier_challenge,auth_version,expires_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '60 seconds')",hash(code),active.user().getId(),challenge,active.user().getAuthVersion());
        return code;
    }
    @Transactional public LoginResponse exchange(String code,String verifier){
        var ticket=db.one("DELETE FROM oauth_login_exchange WHERE code_hash=? AND verifier_challenge=? AND expires_at>CURRENT_TIMESTAMP RETURNING user_id,auth_version",hash(code),challenge(verifier));
        if(ticket==null)throw new BusinessException(401,"授权已失效，请重新使用 GitHub 登录");
        // Lock the account against disable/reset until the JWT has been constructed.
        db.one("SELECT id FROM sys_user WHERE id=? FOR UPDATE",id(ticket,"user_id"));
        UserPrincipal user;
        try{user=users.findActive(id(ticket,"user_id"));}
        catch(org.springframework.security.core.AuthenticationException e){throw new BusinessException(401,"账户已停用或授权已撤销，请联系系统管理员");}
        if(id(ticket,"auth_version")!=user.user().getAuthVersion())throw new BusinessException(401,"授权已撤销，请重新登录");
        var jwt=tokens.issue(user);
        return new LoginResponse(jwt.getTokenValue(),"Bearer",jwt.getExpiresAt(),users.view(user));
    }
}
