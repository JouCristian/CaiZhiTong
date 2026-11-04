package cn.caizhitong.service;
import cn.caizhitong.dto.*;
import cn.caizhitong.mapper.OperationsMapper;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static cn.caizhitong.mapper.OperationsMapper.*;
@Service
public class AdminUserService {
 private final OperationsMapper db;private final PasswordEncoder passwords;
 public AdminUserService(OperationsMapper db,PasswordEncoder passwords){this.db=db;this.passwords=passwords;}
 private static final String SELECT="SELECT u.id,u.username,u.display_name,u.email,u.enabled,u.created_at,u.updated_at,u.last_login_at,COALESCE((SELECT string_agg(r.code,',' ORDER BY r.id) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id),'') AS roles FROM sys_user u";
 private Map<String,Object> view(Map<String,Object> row){var result=new LinkedHashMap<>(row);String roles=text(row,"roles");result.put("roles",roles.isEmpty()?List.of():Arrays.asList(roles.split(",")));return api(result);}
 public Map<String,Object> detail(long id){var row=db.one(SELECT+" WHERE u.id=?",id);if(row==null)throw new BusinessException(404,"用户不存在");return view(row);}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)public PageResult<Map<String,Object>> list(UserQuery q){
  var args=new ArrayList<Object>();String where=" WHERE 1=1";
  if(q.keyword()!=null&&!q.keyword().isBlank()){where+=" AND (u.username ILIKE ? ESCAPE '\\' OR u.display_name ILIKE ? ESCAPE '\\' OR u.email ILIKE ? ESCAPE '\\')";for(int i=0;i<3;i++)args.add(OperationQuery.pattern(q.keyword()));}
  if(q.enabled()!=null){where+=" AND u.enabled=?";args.add(q.enabled());}long total=db.count("SELECT count(*) FROM sys_user u"+where,args.toArray());args.add(q.s());args.add(q.offset());
  return new PageResult<>(db.rows(SELECT+where+" ORDER BY u.created_at DESC,u.id DESC LIMIT ? OFFSET ?",args.toArray()).stream().map(this::view).toList(),total,q.p(),q.s());
 }
 private void lock(UserPrincipal actor){db.one("SELECT pg_advisory_xact_lock(2606,1)");if(db.count("SELECT count(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.id=? AND u.enabled AND r.code='ADMIN'",actor.user().getId())!=1)throw new BusinessException(403,"管理员权限已变更，请刷新登录状态");}
 private void roles(long id,Set<String> roles){db.update("DELETE FROM sys_user_role WHERE user_id=?",id);for(String role:roles)if(db.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code=?",id,role)!=1)throw new BusinessException(400,"角色不存在");}
 private String email(String value){return value==null||value.isBlank()?null:value.trim();}
 @Transactional public Map<String,Object> create(CreateUserInput in,UserPrincipal actor){lock(actor);
  if(db.count("SELECT count(*) FROM sys_user WHERE username=?",in.username())>0)throw new BusinessException(409,"用户名已存在");
  var p=in.profile();var row=db.one("INSERT INTO sys_user(username,display_name,email,enabled,password_hash) VALUES(?,?,?,?,?) RETURNING id",in.username(),p.displayName().trim(),email(p.email()),p.enabled(),passwords.encode(in.credentials().password()));long id=id(row,"id");roles(id,p.roles());return detail(id);
 }
 private void guard(long id,boolean enabled,Set<String> roles,UserPrincipal actor){
  var row=db.one("SELECT id,enabled FROM sys_user WHERE id=? FOR UPDATE",id);if(row==null)throw new BusinessException(404,"用户不存在");boolean isAdmin=db.count("SELECT count(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='ADMIN'",id)>0;
  if(id==actor.user().getId()&&(!enabled||!roles.contains("ADMIN")))throw new BusinessException(409,"不能停用当前管理员自身或移除自己的管理员角色");
  if(Boolean.TRUE.equals(row.get("enabled"))&&isAdmin&&(!enabled||!roles.contains("ADMIN"))&&db.count("SELECT count(*) FROM sys_user u WHERE u.enabled AND u.id<>? AND EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.code='ADMIN')",id)==0)throw new BusinessException(409,"必须保留至少一个启用的系统管理员");
 }
 @Transactional public Map<String,Object> update(long id,AdminUserInput in,UserPrincipal actor){lock(actor);guard(id,in.enabled(),in.roles(),actor);
  db.update("UPDATE sys_user SET display_name=?,email=?,auth_version=auth_version+CASE WHEN enabled AND NOT ? THEN 1 ELSE 0 END,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",in.displayName().trim(),email(in.email()),in.enabled(),in.enabled(),id);roles(id,in.roles());return detail(id);
 }
 @Transactional public Map<String,Object> status(long id,boolean enabled,UserPrincipal actor){lock(actor);var old=detail(id);@SuppressWarnings("unchecked")var roles=new HashSet<>((List<String>)old.get("roles"));guard(id,enabled,roles,actor);db.update("UPDATE sys_user SET auth_version=auth_version+CASE WHEN enabled AND NOT ? THEN 1 ELSE 0 END,enabled=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",enabled,enabled,id);return detail(id);}
 @Transactional public Map<String,Object> reset(long id,PasswordInput in,UserPrincipal actor){lock(actor);detail(id);db.update("UPDATE sys_user SET password_hash=?,password_login_enabled=true,auth_version=auth_version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",passwords.encode(in.password()),id);return Map.of("id",id,"sessionsRevoked",true);}
}
