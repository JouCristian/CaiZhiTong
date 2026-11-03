package cn.caizhitong.dto;
import java.time.Instant;
public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {}

