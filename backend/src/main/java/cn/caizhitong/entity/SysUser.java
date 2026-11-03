package cn.caizhitong.entity;
import com.baomidou.mybatisplus.annotation.*;
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authVersion;
    public Long getAuthVersion() { return authVersion; }
    public void setAuthVersion(Long value) { this.authVersion=value; }
    private String username;
    private String passwordHash;
    private String displayName;
    private Boolean enabled;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}

