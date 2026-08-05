package com.pei.zfile.common.security;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRequestRateLimiterTest {

    @Test
    void rejectsAttemptAboveLimitWithoutPuttingIdentityInKey() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(6L);
        RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(redisTemplate);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> limiter.check("login", "127.0.0.1|alice@example.com", 5, Duration.ofMinutes(5)));

        assertEquals(ResultCode.TOO_MANY_REQUESTS, exception.getResultCode());
    }

    @Test
    void returnedRedisKeyContainsOnlyFingerprint() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        RedisRequestRateLimiter limiter = new RedisRequestRateLimiter(redisTemplate);

        String key = limiter.check("login", "127.0.0.1|alice@example.com", 5, Duration.ofMinutes(5));

        assertFalse(key.contains("alice@example.com"));
    }
}
