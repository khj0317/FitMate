package com.fitmate.domain.account;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * 메일로 보내는 6자리 인증 코드를 Redis에 보관한다 (비밀번호 재설정, 가입 이메일 인증).
 * - 6자리 코드는 경우의 수가 100만 개뿐이라 시도 횟수를 5번으로 제한하고, 넘으면 코드를 폐기한다.
 *   시도 횟수는 HINCRBY로 원자적으로 올려서 동시에 여러 번 찔러도 제한을 넘을 수 없다.
 * - 코드 원문 대신 해시를 저장하고, 비교는 시간이 일정한 MessageDigest.isEqual로 한다.
 * - 한 번 성공하면 바로 지워서 재사용할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    private static final String KEY_PREFIX = "auth:code:";
    private static final String CODE_FIELD = "code";
    private static final String ATTEMPTS_FIELD = "attempts";

    /** 같은 대상(아이디·이메일)이라도 용도가 다르면 코드를 따로 관리한다 */
    public enum Purpose {
        PASSWORD_RESET, SIGNUP_EMAIL
    }

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 새 코드를 발급한다. 이전에 발급한 코드는 무효가 된다. */
    public String issue(Purpose purpose, String subject) {
        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        String key = key(purpose, subject);
        redisTemplate.delete(key);
        redisTemplate.opsForHash().putAll(key, Map.of(CODE_FIELD, sha256(code), ATTEMPTS_FIELD, "0"));
        redisTemplate.expire(key, TTL);
        return code;
    }

    /** 코드가 맞으면 true를 반환하고 코드를 폐기한다. */
    public boolean verifyAndConsume(Purpose purpose, String subject, String code) {
        String key = key(purpose, subject);
        Object stored = redisTemplate.opsForHash().get(key, CODE_FIELD);
        if (stored == null) {
            return false;
        }
        Long attempts = redisTemplate.opsForHash().increment(key, ATTEMPTS_FIELD, 1);
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            return false;
        }
        boolean matches = MessageDigest.isEqual(
                stored.toString().getBytes(StandardCharsets.UTF_8),
                sha256(code).getBytes(StandardCharsets.UTF_8));
        if (matches || attempts == MAX_ATTEMPTS) {
            redisTemplate.delete(key);
        }
        return matches;
    }

    private static String key(Purpose purpose, String subject) {
        return KEY_PREFIX + purpose.name().toLowerCase() + ":" + subject;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
