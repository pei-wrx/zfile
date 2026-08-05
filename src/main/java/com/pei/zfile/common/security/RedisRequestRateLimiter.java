package com.pei.zfile.common.security;

import com.pei.zfile.common.exception.BusinessException;
import com.pei.zfile.common.response.ResultCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class RedisRequestRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('incr', KEYS[1]); "
                    + "if current == 1 then redis.call('pexpire', KEYS[1], ARGV[1]); end; "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRequestRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String check(String namespace, String identity, long maxAttempts, Duration window) {
        String key = "rate-limit:" + namespace + ":" + fingerprint(identity);
        Long attempts = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key),
                String.valueOf(window.toMillis()));
        if (attempts == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "限流状态不可用");
        }
        if (attempts > maxAttempts) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return key;
    }

    public void reset(String key) {
        redisTemplate.delete(key);
    }

    private String fingerprint(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
