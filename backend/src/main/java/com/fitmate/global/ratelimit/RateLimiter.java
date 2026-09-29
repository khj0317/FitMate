package com.fitmate.global.ratelimit;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 고정 창(fixed window) 방식의 요청 횟수 제한. 서버가 여러 대여도 Redis 하나에서 세므로 합산된다.
 * INCR과 첫 요청의 EXPIRE를 Lua 스크립트로 한 번에 실행해서, 둘 사이에 서버가 죽어도
 * 만료 없는 키가 남아 영원히 막히는 일이 없게 한다.
 */
@Component
public class RateLimiter {

    private static final String PREFIX = "rate:";

    /** 반환: {현재 횟수, 남은 초} */
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return {count, redis.call('TTL', KEYS[1])}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;

    public RateLimiter(StringRedisTemplate redisTemplate,
                       @Value("${fitmate.rate-limit.enabled:true}") boolean enabled) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    /** 허용 횟수를 넘으면 몇 초 뒤에 다시 할 수 있는지 담아 429 예외를 던진다 */
    public void check(String name, String subject, int limit, int windowSeconds) {
        if (!enabled) {
            return;
        }
        List<?> result = redisTemplate.execute(SCRIPT, List.of(PREFIX + name + ":" + subject),
                String.valueOf(windowSeconds));
        long count = Long.parseLong(String.valueOf(result.get(0)));
        if (count > limit) {
            long retryAfter = Math.max(1, Long.parseLong(String.valueOf(result.get(1))));
            throw new RateLimitExceededException(retryAfter);
        }
    }

    /** Retry-After 헤더를 붙이기 위해 남은 시간을 함께 담는다 */
    public static class RateLimitExceededException extends BusinessException {

        private final long retryAfterSeconds;

        RateLimitExceededException(long retryAfterSeconds) {
            super(ErrorCode.TOO_MANY_REQUESTS, retryAfterSeconds < 60
                    ? "요청이 너무 많아요. %d초 뒤에 다시 시도해 주세요.".formatted(retryAfterSeconds)
                    : "요청이 너무 많아요. %d분 뒤에 다시 시도해 주세요.".formatted((retryAfterSeconds + 59) / 60));
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
