package com.pei.zfile.common.security;

import com.pei.zfile.user.entity.User;
import com.pei.zfile.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private StringRedisTemplate redisTemplate;
    private UserMapper userMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(
                "0123456789012345678901234567890123456789012345678901234567890123",
                60_000,
                120_000);
        redisTemplate = mock(StringRedisTemplate.class);
        userMapper = mock(UserMapper.class);
        filter = new JwtAuthenticationFilter(tokenProvider, redisTemplate, userMapper);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyActiveUserWithAccessToken() throws Exception {
        String token = tokenProvider.generateAccessToken(7L, "alice", "USER");
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(userMapper.selectById(7L)).thenReturn(new User()
                .setId(7L)
                .setStatus("ACTIVE")
                .setRole("ADMIN"));

        invoke(token);

        assertEquals("7", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void refreshTokenCannotAuthenticateAsAccessToken() throws Exception {
        invoke(tokenProvider.generateRefreshToken(7L));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userMapper, never()).selectById(7L);
    }

    @Test
    void disabledUserOldAccessTokenIsRejected() throws Exception {
        String token = tokenProvider.generateAccessToken(7L, "alice", "USER");
        when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(userMapper.selectById(7L)).thenReturn(new User()
                .setId(7L)
                .setStatus("DISABLED")
                .setRole("USER"));

        invoke(token);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void invoke(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
