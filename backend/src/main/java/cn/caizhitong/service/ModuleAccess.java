package cn.caizhitong.service;
import cn.caizhitong.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.*;
@Component("moduleAccess")
public class ModuleAccess {
    private static final Map<String, Set<String>> RULES = new LinkedHashMap<>();
    static {
        RULES.put("dashboard", Set.of("STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN"));
        RULES.put("materials", Set.of("STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN"));
        RULES.put("inventory", Set.of("LAB_TECH","TEACHER","PURCHASER","ADMIN"));
        RULES.put("inbound", Set.of("LAB_TECH","PURCHASER","ADMIN"));
        RULES.put("requisitions", Set.of("STUDENT","LAB_TECH","TEACHER","ADMIN"));
        RULES.put("returns", Set.of("STUDENT","LAB_TECH","TEACHER","ADMIN"));
        RULES.put("alerts", Set.of("LAB_TECH","TEACHER","PURCHASER","ADMIN"));
        RULES.put("reports", Set.of("STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN"));
        RULES.put("users", Set.of("ADMIN"));
        RULES.put("settings", Set.of("ADMIN"));
    }
    public List<String> modules(List<String> roles) {
        if (roles.contains("GUEST") && roles.stream().noneMatch(Set.of("STUDENT","LAB_TECH","TEACHER","PURCHASER","ADMIN")::contains))
            return List.of("demo/dashboard","demo/materials","demo/stocks");
        return RULES.entrySet().stream().filter(e -> roles.stream().anyMatch(e.getValue()::contains)).map(Map.Entry::getKey).toList();
    }
    public boolean canAccess(Authentication auth, String module) {
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p && modules(p.roles()).contains(module);
    }
}

