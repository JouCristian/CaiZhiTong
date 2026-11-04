package cn.caizhitong.dto;
import java.util.List;
public record UserView(Long id, String username, String displayName, List<String> roles, List<String> modules, String githubLogin, String avatarUrl) {}

