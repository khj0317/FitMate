package com.fitmate.domain.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속 상태: 실시간 연결(WebSocket)이 하나라도 살아 있으면 "현재 접속중".
 *
 * Redis 해시 presence:sessions:{userId} 에 연결마다 만료 시각을 기록한다.
 * - 여러 탭·기기로 접속해도 연결 하나가 끊기면 그 항목만 지우므로, 남은 연결이 있으면 계속 접속중이다.
 * - 각 서버가 자기 연결의 만료 시각을 30초마다 늘린다(heartbeat). 서버가 갑자기 죽어 끊김 이벤트를 못 받아도
 *   90초가 지나면 만료로 보고 오프라인 처리하므로 "영원히 접속중"이 되지 않는다.
 * - 연결이 끊기면 presence:last-seen:{userId} 에 마지막 접속 시각을 남긴다.
 */
@Service
@RequiredArgsConstructor
public class PresenceService {

    static final Duration SESSION_TTL = Duration.ofSeconds(90);
    static final int MAX_QUERY_SIZE = 100;
    private static final Duration KEY_TTL = Duration.ofDays(1);
    private static final Duration LAST_SEEN_TTL = Duration.ofDays(180);
    static final String SESSIONS_PREFIX = "presence:sessions:";
    private static final String LAST_SEEN_PREFIX = "presence:last-seen:";

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    /** 서버마다 WebSocket 세션 ID가 겹칠 수 있어서 서버 ID를 붙여 구분한다 */
    private final String serverId = UUID.randomUUID().toString().substring(0, 8);
    private final Map<String, Long> localSessions = new ConcurrentHashMap<>();

    public void connected(String sessionId, Long userId) {
        localSessions.put(sessionId, userId);
        touch(sessionId, userId);
    }

    public void disconnected(String sessionId) {
        Long userId = localSessions.remove(sessionId);
        if (userId == null) {
            return;
        }
        // 마지막 접속 시각을 먼저 남기고 연결을 지운다. 순서가 반대면 그 사이 조회에서
        // "오프라인인데 마지막 접속 기록도 없음"이 잠깐 보일 수 있다
        saveLastSeen(userId, clock.millis());
        redisTemplate.opsForHash().delete(SESSIONS_PREFIX + userId, field(sessionId));
    }

    /** 이 서버에 연결된 세션들의 만료 시각을 늘린다 */
    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        localSessions.forEach(this::touch);
    }

    public List<Presence> find(Collection<Long> userIds) {
        long now = clock.millis();
        return userIds.stream().distinct().limit(MAX_QUERY_SIZE).map(userId -> find(userId, now)).toList();
    }

    private Presence find(Long userId, long now) {
        String key = SESSIONS_PREFIX + userId;
        Map<Object, Object> sessions = redisTemplate.opsForHash().entries(key);

        boolean online = false;
        long lastHeartbeatOfStale = 0;
        List<Object> staleFields = new ArrayList<>();
        for (Map.Entry<Object, Object> session : sessions.entrySet()) {
            long expiresAt = Long.parseLong(session.getValue().toString());
            if (expiresAt > now) {
                online = true;
            } else {
                staleFields.add(session.getKey());
                lastHeartbeatOfStale = Math.max(lastHeartbeatOfStale, expiresAt - SESSION_TTL.toMillis());
            }
        }
        // 죽은 서버가 남긴 연결은 정리하고, 마지막 heartbeat 시각을 마지막 접속으로 남긴다
        if (!staleFields.isEmpty()) {
            redisTemplate.opsForHash().delete(key, staleFields.toArray());
            saveLastSeen(userId, lastHeartbeatOfStale);
        }
        if (online) {
            return new Presence(userId, true, null);
        }
        String lastSeen = redisTemplate.opsForValue().get(LAST_SEEN_PREFIX + userId);
        return new Presence(userId, false, lastSeen == null ? null : Instant.ofEpochMilli(Long.parseLong(lastSeen)));
    }

    private void touch(String sessionId, Long userId) {
        String key = SESSIONS_PREFIX + userId;
        redisTemplate.opsForHash().put(key, field(sessionId), String.valueOf(clock.millis() + SESSION_TTL.toMillis()));
        redisTemplate.expire(key, KEY_TTL);
    }

    /** 더 최근 값만 남긴다 (늦게 처리된 오래된 시각이 덮어쓰지 않도록) */
    private void saveLastSeen(Long userId, long millis) {
        String key = LAST_SEEN_PREFIX + userId;
        String current = redisTemplate.opsForValue().get(key);
        if (current == null || Long.parseLong(current) < millis) {
            redisTemplate.opsForValue().set(key, String.valueOf(millis), LAST_SEEN_TTL);
        }
    }

    private String field(String sessionId) {
        return serverId + ":" + sessionId;
    }

    public record Presence(Long userId, boolean online, Instant lastSeenAt) {
    }
}
