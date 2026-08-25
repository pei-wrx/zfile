package com.pei.zfile.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import java.io.IOException;
import java.util.Collections;
import static com.pei.zfile.common.util.RedisConstant.TOKEN_BLACKLIST_KEY;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                   StringRedisTemplate stringRedisTemplate,
                                   UserMapper userMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
    }
    // 过滤请求
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validateToken(token) == JwtTokenProvider.TokenValidationResult.VALID) {
            Claims claims = jwtTokenProvider.parseClaims(token);
            if (!jwtTokenProvider.hasTokenType(claims, JwtTokenProvider.ACCESS_TOKEN_TYPE)) {
                filterChain.doFilter(request, response);
                return;
            }
            String jti = claims.getId();
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(TOKEN_BLACKLIST_KEY + jti))) {
                filterChain.doFilter(request, response);
                return;
            }

            Long userId;
            try {
                userId = Long.valueOf(claims.getSubject());
            } catch (NumberFormatException e) {
                filterChain.doFilter(request, response);
                return;
            }
            User user = userMapper.selectById(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId.toString(),
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
