package com.pei.zfile.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pei.zfile.auth.dto.LoginDTO;
import com.pei.zfile.auth.dto.RefreshDTO;
import com.pei.zfile.auth.dto.RegisterDTO;
import com.pei.zfile.auth.dto.TokenResponse;
import com.pei.zfile.auth.service.AuthService;
import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import com.pei.zfile.common.security.JwtTokenProvider;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
import static com.pei.zfile.common.util.RedisConstant.*;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public TokenResponse login(LoginDTO loginDTO) {
        if (loginDTO == null || loginDTO.getUsername() == null) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS, "登录信息不完整");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, loginDTO.getUsername())
        );
        if (user == null) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.INVALID_CREDENTIALS);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "用户已被禁用");
        }
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        String key = REFRESH_TOKEN_KEY + user.getId().toString();
        stringRedisTemplate.opsForValue().set(key, refreshToken, REFRESH_TOKEN_TTL, TimeUnit.DAYS);
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(accessToken);
        tokenResponse.setRefreshToken(refreshToken);
        tokenResponse.setExpiresIn(ACCESS_TOKEN_TTL * 60);
        tokenResponse.setUserInfo(new TokenResponse.UserInfo(user.getId(), user.getUsername(), user.getRole()));
        return tokenResponse;
    }

    @Override
    public void register(RegisterDTO registerDTO) {
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
        userMapper.insert(user);
    }

    @Override
    public TokenResponse refresh(RefreshDTO refreshDTO) {
        String refreshToken = refreshDTO.getRefreshToken();
        JwtTokenProvider.TokenValidationResult result = jwtTokenProvider.validateToken(refreshToken);
        if (result != JwtTokenProvider.TokenValidationResult.VALID) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 无效或已过期");
        }
        Claims claims = jwtTokenProvider.parseClaims(refreshToken);
        Long userId = Long.parseLong(claims.getSubject());
        String key = REFRESH_TOKEN_KEY + userId;
        String storedToken = stringRedisTemplate.opsForValue().get(key);
        if (storedToken == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已被注销");
        }
        if (!storedToken.equals(refreshToken)) {
            stringRedisTemplate.delete(key);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "refreshToken 已被使用，请重新登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在或已被禁用");
        }
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        stringRedisTemplate.opsForValue()
                .set(key, newRefreshToken, REFRESH_TOKEN_TTL, TimeUnit.DAYS);
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(newAccessToken);
        tokenResponse.setRefreshToken(newRefreshToken);
        tokenResponse.setExpiresIn(ACCESS_TOKEN_TTL * 60);
        tokenResponse.setUserInfo(new TokenResponse.UserInfo(
                user.getId(), user.getUsername(), user.getRole()));
        return tokenResponse;
    }

    @Override
    public void logout(String token) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseClaims(token);
        } catch (Exception e) {
            // token 签名无效，无需处理
            return;
        }
        String userId = claims.getSubject();
        String jti = claims.getId();
        long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remainingMs > 0) {
            stringRedisTemplate.opsForValue()
                    .set(TOKEN_BLACKLIST_KEY + jti, "1", remainingMs, TimeUnit.MILLISECONDS);
        }
        //stringRedisTemplate.delete(REFRESH_TOKEN_KEY + userId);
        Boolean deleted = stringRedisTemplate.delete(REFRESH_TOKEN_KEY + userId);
        log.info("删除 refreshToken, key={}, result={}", REFRESH_TOKEN_KEY + userId, deleted);
    }
}