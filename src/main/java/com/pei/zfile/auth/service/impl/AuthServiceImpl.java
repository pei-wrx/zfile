package com.pei.zfile.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.audit.constant.AuditEnum;
import com.pei.zfile.audit.service.AuditService;
import com.pei.zfile.auth.dto.LoginDTO;
import com.pei.zfile.auth.dto.RefreshDTO;
import com.pei.zfile.auth.dto.RegisterDTO;
import com.pei.zfile.auth.dto.TokenResponse;
import com.pei.zfile.auth.service.AuthService;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.common.security.JwtTokenProvider;
import com.pei.zfile.common.security.RedisRequestRateLimiter;
import com.pei.zfile.user.dto.UserResponse;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.dao.DuplicateKeyException;
import java.util.List;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static com.pei.zfile.common.util.RedisConstant.*;
import static org.flywaydb.core.internal.util.JsonUtils.toJson;


@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final DefaultRedisScript<Long> ROTATE_REFRESH_TOKEN_SCRIPT;
    static{
        ROTATE_REFRESH_TOKEN_SCRIPT = new DefaultRedisScript<>();
        ROTATE_REFRESH_TOKEN_SCRIPT.setLocation(new ClassPathResource("refreshJWT.lua"));
        ROTATE_REFRESH_TOKEN_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisRequestRateLimiter rateLimiter;
    @Autowired
    private AuditService auditService;

    private String dummyPasswordHash;

    @PostConstruct
    void initializeDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode("dummy-password-never-used");
    }


    @Override
    public TokenResponse login(LoginDTO loginDTO, String clientIp) {
        if (loginDTO == null || loginDTO.getUsername() == null) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "登录信息不完整");
        }
        String rateLimitKey = rateLimiter.check(
                "login", clientIp + "|" + loginDTO.getUsername().toLowerCase(Locale.ROOT),
                5, Duration.ofMinutes(5));
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, loginDTO.getUsername())
                        .or()
                        .eq(User::getEmail, loginDTO.getUsername())
        );
        if (user == null) {
            passwordEncoder.matches(loginDTO.getPassword(), dummyPasswordHash);
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户已被禁用");
        }
        rateLimiter.reset(rateLimitKey);
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        String key = REFRESH_TOKEN_KEY + user.getId().toString();
        stringRedisTemplate.opsForValue().set(
                key, refreshToken, jwtTokenProvider.getRefreshTokenExpirationMillis(), TimeUnit.MILLISECONDS);
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshToken);
        tokenResponse.setExpiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds());
        tokenResponse.setUserInfo(new TokenResponse.UserInfo(user.getId(), user.getUsername(), user.getRole()));

        auditService.record(user.getId(), AuditEnum.LOGIN, "USER", user.getId(),toJson(Map.of("role", user.getRole())), clientIp);

        return tokenResponse;
    }

    @Override
    public UserResponse register(RegisterDTO registerDTO) {
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, registerDTO.getUsername())) != 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, registerDTO.getEmail())) != 0) {
            throw new BusinessException(ResultCode.EMAIL_EXISTS);
        }
        String encodedPassword = passwordEncoder.encode(registerDTO.getPassword());
        User user = new User()
                .setUsername(registerDTO.getUsername())
                .setEmail(registerDTO.getEmail())
                .setPasswordHash(encodedPassword)
                .setRole("USER")
                .setStatus("ACTIVE")
                .setQuotaBytes(1024 * 1024 * 1024L)
                .setUsedBytes(0L);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS, "用户名或邮箱已存在", e);
        }
        User persisted = userMapper.selectById(user.getId());
        return toUserResponse(persisted != null ? persisted : user);
    }

    @Override
    public TokenResponse refresh(RefreshDTO refreshDTO) {
        String refreshToken = refreshDTO.getRefreshToken();
        JwtTokenProvider.TokenValidationResult result = jwtTokenProvider.validateToken(refreshToken);
        if (result != JwtTokenProvider.TokenValidationResult.VALID) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        }
        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        if (!jwtTokenProvider.hasTokenType(claims, JwtTokenProvider.REFRESH_TOKEN_TYPE)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 类型不正确");
        }
        Long userId = Long.parseLong(claims.getSubject());
        String key = REFRESH_TOKEN_KEY + userId;
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            stringRedisTemplate.delete(key);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        Long rotated = stringRedisTemplate.execute(
                ROTATE_REFRESH_TOKEN_SCRIPT,
                List.of(key),
                refreshToken,
                newRefreshToken,
                String.valueOf(jwtTokenProvider.getRefreshTokenExpirationMillis()));
        if (!Long.valueOf(1L).equals(rotated)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效或已被使用");
        }
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(newAccessToken);
        tokenResponse.setRefreshToken(newRefreshToken);
        tokenResponse.setExpiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds());
        tokenResponse.setUserInfo(new TokenResponse.UserInfo(
                user.getId(), user.getUsername(), user.getRole()));
        return tokenResponse;
    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        Claims accessClaims;
        Claims refreshClaims;
        try {
            accessClaims = jwtTokenProvider.parseClaims(accessToken);
            refreshClaims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌无效");
        }
        if (!jwtTokenProvider.hasTokenType(accessClaims, JwtTokenProvider.ACCESS_TOKEN_TYPE)
                || !jwtTokenProvider.hasTokenType(refreshClaims, JwtTokenProvider.REFRESH_TOKEN_TYPE)
                || !accessClaims.getSubject().equals(refreshClaims.getSubject())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "令牌类型或用户不匹配");
        }

        String userId = accessClaims.getSubject();
        String refreshKey = REFRESH_TOKEN_KEY + userId;
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        if (!refreshToken.equals(storedRefreshToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已失效");
        }

        String jti = accessClaims.getId();
        long remainingMs = accessClaims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            stringRedisTemplate.opsForValue()
                    .set(TOKEN_BLACKLIST_KEY + jti, "1", remainingMs, TimeUnit.MILLISECONDS);
        }
        stringRedisTemplate.delete(refreshKey);
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .quotaBytes(user.getQuotaBytes())
                .usedBytes(user.getUsedBytes())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
