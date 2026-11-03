package cn.caizhitong.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
@Service
public class JwtService {
    public static final String ISSUER="caizhitong";
    private final JwtEncoder encoder;
    private final long ttl;
    public JwtService(JwtEncoder encoder, @Value("${app.jwt.ttl-seconds}") long ttl) {
        if (ttl < 60 || ttl > 86400) throw new IllegalArgumentException("JWT TTL must be 60..86400 seconds");
        this.encoder=encoder; this.ttl=ttl;
    }
    public Jwt issue(UserPrincipal user) {
        Instant now=Instant.now();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer(ISSUER).subject(user.user().getId().toString())
                .claim("av",user.user().getAuthVersion()==null?0L:user.user().getAuthVersion()).issuedAt(now).expiresAt(now.plusSeconds(ttl)).id(java.util.UUID.randomUUID().toString()).build()));
    }
}

