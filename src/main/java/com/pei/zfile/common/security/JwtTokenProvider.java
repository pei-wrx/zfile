package com.pei.zfile.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    public static final String ACCESS_TOKEN_TYPE = "ACCESS";
    public static final String REFRESH_TOKEN_TYPE = "REFRESH";
    public static final String SHARE_TOKEN_TYPE = "SHARE";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.access-token-expiration:1800000}") long accessTokenExpiration,
                            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }
    //生成访问令牌
    public String generateAccessToken(Long userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }
    //生成刷新令牌
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }
    //生成分享令牌（30分钟有效）
    public String generateShareToken(String shareCode) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(shareCode)
                .claim(TOKEN_TYPE_CLAIM, SHARE_TOKEN_TYPE)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 1800_000))
                .signWith(secretKey)
                .compact();
    }

    //验证令牌
    public TokenValidationResult validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return TokenValidationResult.VALID;
        } catch (ExpiredJwtException e) {
            return TokenValidationResult.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return TokenValidationResult.INVALID;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
    }

    public boolean hasTokenType(Claims claims, String expectedType) {
        return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public long getAccessTokenExpirationSeconds() {
        return accessTokenExpiration / 1000;
    }

    public long getRefreshTokenExpirationMillis() {
        return refreshTokenExpiration;
    }

    public enum TokenValidationResult {
        VALID,
        EXPIRED,
        INVALID
    }
}
