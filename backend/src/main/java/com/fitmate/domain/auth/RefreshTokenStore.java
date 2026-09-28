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
import java.util.Set;

/**
 * 리프레시 토큰은 JWT가 아닌 랜덤 문자열로 발급하고 Redis에 저장한다.
 * - 원문 대신 SHA-256 해시를 키로 저장해 Redis가 유출돼도 토큰을 재사용할 수 없다.
 * - 사용 시 GETDEL로 원자적으로 꺼내고 지워서, 같은 토큰으로 동시에 재발급을 요청해도 한 번만 성공한다(Rotation).
 * - 사용자별 토큰 목록(Set)도 함께 관리해서, 비밀번호 변경 시 모든 기기를 로그아웃시킬 수 있다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";
    private static final String USER_KEY_PREFIX = "auth:refresh-user:";
    private static final int TOKEN_BYTES = 32;

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(token);

        redisTemplate.opsForValue().set(KEY_PREFIX + hash, String.valueOf(userId), properties.refreshTokenTtl());
        String userKey = USER_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(userKey, hash);
        redisTemplate.expire(userKey, properties.refreshTokenTtl());
        return token;
    }

    /** 토큰을 소비하고 주인 userId를 반환한다. 없거나 이미 사용된 토큰이면 empty. */
    public Optional<Long> consume(String token) {
        String hash = sha256(token);
        Optional<Long> userId = Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + hash))
                .map(Long::valueOf);
        userId.ifPresent(id -> redisTemplate.opsForSet().remove(USER_KEY_PREFIX + id, hash));
        return userId;
    }

    public void revoke(String token) {
        consume(token);
    }

    /** 이 사용자의 모든 리프레시 토큰을 폐기한다 (모든 기기 로그아웃). */
    public void revokeAll(Long userId) {
        String userKey = USER_KEY_PREFIX + userId;
        Set<String> hashes = redisTemplate.opsForSet().members(userKey);
        if (hashes != null && !hashes.isEmpty()) {
            redisTemplate.delete(hashes.stream().map(hash -> KEY_PREFIX + hash).toList());
        }
        redisTemplate.delete(userKey);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
