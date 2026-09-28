package com.fitmate.domain.auth;

import com.fitmate.global.security.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 리프레시 토큰은 JWT가 아닌 랜덤 문자열로 발급하고 Redis에 저장한다.
 * - 원문 대신 SHA-256 해시를 키로 저장해 Redis가 유출돼도 토큰을 재사용할 수 없다.
 * - 사용 시 GETDEL로 원자적으로 꺼내고 지워서, 같은 토큰으로 동시에 재발급을 요청해도 한 번만 성공한다(Rotation).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(key(token), String.valueOf(userId), properties.refreshTokenTtl());
        return token;
    }

    /** 토큰을 소비하고 주인 userId를 반환한다. 없거나 이미 사용된 토큰이면 empty. */
    public Optional<Long> consume(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(key(token)))
                .map(Long::valueOf);
    }

    public void revoke(String token) {
        redisTemplate.delete(key(token));
    }

    private static String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
