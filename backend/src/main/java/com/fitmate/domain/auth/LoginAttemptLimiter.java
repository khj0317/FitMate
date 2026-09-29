package com.fitmate.domain.auth;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 비밀번호 무차별 대입 방지.
 * - 같은 아이디로 5번 틀리면 15분 잠금
 * - 아이디만 제한하면 공격자가 아이디를 바꿔 가며 시도하거나, 남의 아이디를 일부러 잠글 수 있어서
 *   같은 IP에서 20번 틀리는 경우도 따로 막는다
 * 첫 실패부터 15분이 지나면 기록이 사라진다. 실패 횟수는 INCR로 세서 동시에 여러 번 시도해도 정확하다.
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    static final int MAX_FAILURES_PER_LOGIN_ID = 5;
    static final int MAX_FAILURES_PER_IP = 20;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String ID_PREFIX = "auth:login-fail:id:";
    private static final String IP_PREFIX = "auth:login-fail:ip:";

    private final StringRedisTemplate redisTemplate;

    /** 잠겨 있으면 남은 시간을 담아 예외를 던진다. 비밀번호 확인보다 먼저 호출해서, 맞는 비밀번호로도 잠금을 풀 수 없게 한다 */
    public void checkAllowed(String loginId, String ip) {
        checkKey(ID_PREFIX + normalize(loginId), MAX_FAILURES_PER_LOGIN_ID);
        checkKey(IP_PREFIX + ip, MAX_FAILURES_PER_IP);
    }

    public void recordFailure(String loginId, String ip) {
        increment(ID_PREFIX + normalize(loginId));
        increment(IP_PREFIX + ip);
    }

    /** 로그인에 성공하면 그 아이디의 실패 기록은 지운다 (IP 기록은 다른 아이디 시도가 섞여 있어 유지) */
    public void reset(String loginId) {
        redisTemplate.delete(ID_PREFIX + normalize(loginId));
    }

    private void checkKey(String key, int max) {
        String count = redisTemplate.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= max) {
            Long seconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            long minutes = Math.max(1, (long) Math.ceil((seconds == null || seconds < 0 ? WINDOW.toSeconds() : seconds) / 60.0));
            throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                    "로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해 주세요.");
        }
    }

    private void increment(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, WINDOW);
        }
    }

    private static String normalize(String loginId) {
        return loginId == null ? "" : loginId.strip().toLowerCase(Locale.ROOT);
    }
}
