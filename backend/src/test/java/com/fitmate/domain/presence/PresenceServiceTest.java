package com.fitmate.domain.presence;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 실제 Redis 컨테이너로 접속 상태 계산을 검증한다. 사용자 ID는 다른 테스트와 겹치지 않게 임의로 만든다. */
class PresenceServiceTest extends IntegrationTest {

    @Autowired
    private PresenceService presenceService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("접속한 적이 없으면 오프라인이고 마지막 접속 기록도 없다")
    void neverConnected() {
        long userId = randomUserId();

        assertThat(presence(userId)).isEqualTo(new PresenceService.Presence(userId, false, null));
    }

    @Test
    @DisplayName("탭 두 개로 접속하면 하나를 닫아도 접속중이고, 둘 다 닫으면 마지막 접속 시각이 남는다")
    void multipleSessions() {
        long userId = randomUserId();
        String tab1 = "tab1-" + userId;
        String tab2 = "tab2-" + userId;

        presenceService.connected(tab1, userId);
        presenceService.connected(tab2, userId);
        assertThat(presence(userId).online()).isTrue();

        presenceService.disconnected(tab1);
        assertThat(presence(userId).online()).isTrue();

        presenceService.disconnected(tab2);
        PresenceService.Presence offline = presence(userId);
        assertThat(offline.online()).isFalse();
        assertThat(offline.lastSeenAt()).isCloseTo(Instant.now(), within(Duration.ofSeconds(5)));
    }

    @Test
    @DisplayName("서버가 죽어 끊김 이벤트가 없어도, heartbeat가 끊긴 연결은 만료로 보고 정리한다")
    void staleSessionFromDeadServer() {
        long userId = randomUserId();
        Instant lastHeartbeat = Instant.now().minus(Duration.ofMinutes(10));
        long expiredAt = lastHeartbeat.plus(PresenceService.SESSION_TTL).toEpochMilli();
        redisTemplate.opsForHash().put(PresenceService.SESSIONS_PREFIX + userId, "dead-server:session", String.valueOf(expiredAt));

        PresenceService.Presence result = presence(userId);

        assertThat(result.online()).isFalse();
        assertThat(result.lastSeenAt()).isCloseTo(lastHeartbeat, within(Duration.ofSeconds(1)));
        assertThat(redisTemplate.opsForHash().size(PresenceService.SESSIONS_PREFIX + userId)).isZero();
    }

    @Test
    @DisplayName("여러 명을 한 번에 조회한다")
    void findMany() {
        long online = randomUserId();
        long offline = randomUserId();
        presenceService.connected("s-" + online, online);

        List<PresenceService.Presence> result = presenceService.find(List.of(online, offline, online));

        assertThat(result).extracting(PresenceService.Presence::userId).containsExactly(online, offline);
        assertThat(result).extracting(PresenceService.Presence::online).containsExactly(true, false);
        presenceService.disconnected("s-" + online);
    }

    private PresenceService.Presence presence(long userId) {
        return presenceService.find(List.of(userId)).get(0);
    }

    private static long randomUserId() {
        return ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_000_000_000L);
    }
}
