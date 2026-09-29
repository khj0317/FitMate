package com.fitmate.domain.gathering;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatheringApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("모임을 만들면 모임장이 첫 참가자가 되고 단체 채팅방이 생긴다")
    void createGathering() throws Exception {
        TestUser host = signupAndLogin();
        Long gatheringId = createGathering(host, 4);

        String body = call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.currentCount").value(1))
                .andExpect(jsonPath("$.summary.capacity").value(4))
                .andExpect(jsonPath("$.summary.status").value("RECRUITING"))
                .andExpect(jsonPath("$.isHost").value(true))
                .andExpect(jsonPath("$.participants.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        Long roomId = ((Number) JsonPath.read(body, "$.chatRoomId")).longValue();

        call(HttpMethod.GET, "/api/chat-rooms", null, host.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roomId == %d)].title".formatted(roomId)).value(hasItem("주말 러닝 모임")))
                .andExpect(jsonPath("$[?(@.roomId == %d)].memberCount".formatted(roomId)).value(hasItem(1)));
    }

    @Test
    @DisplayName("모임 시간은 10분 뒤 ~ 60일 안이어야 한다")
    void rejectInvalidTime() throws Exception {
        TestUser host = signupAndLogin();
        call(HttpMethod.POST, "/api/gatherings", gatheringJson(Instant.now().plus(5, ChronoUnit.MINUTES), 4, 0, 0),
                host.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GATHERING_TIME"));
    }

    @Test
    @DisplayName("참여하면 단체 채팅방에 들어가고, 모임장에게 알림이 간다")
    void joinGathering() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 4);

        String body = join(guest, gatheringId).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long roomId = ((Number) JsonPath.read(body, "$.chatRoomId")).longValue();

        call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, guest.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readCursors.length()").value(1));
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, guest.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(2))
                .andExpect(jsonPath("$.summary.joined").value(true));
        call(HttpMethod.GET, "/api/notifications", null, host.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("GATHERING_JOINED"))
                .andExpect(jsonPath("$.items[0].link").value("/gatherings/" + gatheringId));
    }

    @Test
    @DisplayName("이미 참여한 모임에 다시 참여하면 409")
    void rejectDoubleJoin() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 4);

        join(guest, gatheringId).andExpect(status().isOk());
        join(guest, gatheringId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
        join(host, gatheringId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_JOINED"));
    }

    @Test
    @DisplayName("정원 5명 모임에 20명이 동시에 참여하면 정확히 4명만 성공하고 모집이 마감된다")
    void concurrentJoinRespectsCapacity() throws Exception {
        TestUser host = signupAndLogin();
        Long gatheringId = createGathering(host, 5);
        List<TestUser> guests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            guests.add(signupAndLogin());
        }

        List<Integer> statuses = runConcurrently(20, index ->
                join(guests.get(index), gatheringId).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(code -> code == 200).hasSize(4);
        assertThat(statuses).filteredOn(code -> code == 409).hasSize(16);
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(5))
                .andExpect(jsonPath("$.summary.status").value("CLOSED"))
                .andExpect(jsonPath("$.participants.length()").value(5));
        Integer members = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chat_room_members m JOIN chat_rooms r ON r.id = m.room_id
                WHERE r.gathering_id = ?""", Integer.class, gatheringId);
        assertThat(members).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 사람이 동시에 여러 번 눌러도 한 자리만 차지한다")
    void concurrentDoubleJoinBySameUser() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 10);

        List<Integer> statuses = runConcurrently(5, index ->
                join(guest, gatheringId).andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(2));
    }

    @Test
    @DisplayName("같은 사람이 동시에 여러 번 나가도 자리는 한 번만 돌려준다")
    void concurrentLeaveBySameUser() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        TestUser other = signupAndLogin();
        Long gatheringId = createGathering(host, 10);
        join(guest, gatheringId).andExpect(status().isOk());
        join(other, gatheringId).andExpect(status().isOk());

        List<Integer> statuses = runConcurrently(5, index -> call(HttpMethod.DELETE,
                "/api/gatherings/" + gatheringId + "/participants/me", null, guest.accessToken())
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(code -> code == 204).hasSize(1);
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(2));
    }

    @Test
    @DisplayName("마감된 모임에서 한 명이 나가면 다시 모집 중이 되고, 다시 참여할 수 있다")
    void leaveReopensGathering() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        TestUser another = signupAndLogin();
        Long gatheringId = createGathering(host, 2);

        join(guest, gatheringId).andExpect(status().isOk());
        join(another, gatheringId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GATHERING_FULL"));

        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId + "/participants/me", null, guest.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(1))
                .andExpect(jsonPath("$.summary.status").value("RECRUITING"));

        join(guest, gatheringId).andExpect(status().isOk());
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(jsonPath("$.summary.currentCount").value(2))
                .andExpect(jsonPath("$.summary.status").value("CLOSED"));
    }

    @Test
    @DisplayName("단체방에 들어오고 나가면 안내가 남고, 나간 사람은 읽음 위치 목록(안 읽은 사람 수)에서 빠진다")
    void joinAndLeaveSystemMessages() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 4);
        String body = join(guest, gatheringId).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long roomId = ((Number) JsonPath.read(body, "$.chatRoomId")).longValue();

        call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, host.accessToken())
                .andExpect(jsonPath("$.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[0].content").value(guest.nickname() + "님이 들어왔어요"))
                .andExpect(jsonPath("$.readCursors[*].userId").value(hasItem(guest.id().intValue())));
        // 시스템 안내는 안 읽은 수에 세지 않는다
        call(HttpMethod.GET, "/api/chat-rooms", null, host.accessToken())
                .andExpect(jsonPath("$[?(@.roomId == %d)].unreadCount".formatted(roomId)).value(hasItem(0)));

        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId + "/participants/me", null, guest.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, host.accessToken())
                .andExpect(jsonPath("$.messages[0].content").value(guest.nickname() + "님이 나갔어요"))
                .andExpect(jsonPath("$.readCursors[*].userId").value(not(hasItem(guest.id().intValue()))));
    }

    @Test
    @DisplayName("모임장은 나갈 수 없고, 참여하지 않은 사람도 나갈 수 없다")
    void leaveRules() throws Exception {
        TestUser host = signupAndLogin();
        TestUser stranger = signupAndLogin();
        Long gatheringId = createGathering(host, 4);

        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId + "/participants/me", null, host.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HOST_CANNOT_LEAVE"));
        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId + "/participants/me", null, stranger.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_JOINED"));
    }

    @Test
    @DisplayName("모임장이 취소하면 참가자에게 알림이 가고, 모임은 더 이상 보이지 않는다")
    void cancelNotifiesParticipants() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 4);
        join(guest, gatheringId).andExpect(status().isOk());

        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId, null, guest.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_GATHERING_HOST"));
        call(HttpMethod.DELETE, "/api/gatherings/" + gatheringId, null, host.accessToken())
                .andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/notifications", null, guest.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("GATHERING_CANCELED"));
        call(HttpMethod.GET, "/api/gatherings/" + gatheringId, null, guest.accessToken())
                .andExpect(status().isNotFound());
        join(signupAndLogin(), gatheringId).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("근처 모임 목록에는 반경 안의 모집 중인 모임만 보인다")
    void nearbyGatherings() throws Exception {
        TestUser host = signupAndLogin();
        TestUser neighbor = signupAndLogin();
        double[] location = location(neighbor.id());
        Long nearId = createGathering(host, 4, location[0] + 0.01, location[1]);
        Long farId = createGathering(host, 4, location[0] + 1.0, location[1]);

        call(HttpMethod.GET, "/api/gatherings?radiusKm=5", null, neighbor.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(hasItem(nearId.intValue())))
                .andExpect(jsonPath("$[*].id").value(not(hasItem(farId.intValue()))));
    }

    @Test
    @DisplayName("차단한 사람의 모임에는 참여할 수 없다")
    void blockedUserCannotJoin() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = createGathering(host, 4);
        call(HttpMethod.PUT, "/api/users/" + guest.id() + "/block", null, host.accessToken())
                .andExpect(status().isNoContent());

        join(guest, gatheringId).andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions join(TestUser user, Long gatheringId) throws Exception {
        return call(HttpMethod.POST, "/api/gatherings/" + gatheringId + "/participants", null, user.accessToken());
    }

    private Long createGathering(TestUser host, int capacity) throws Exception {
        double[] location = location(host.id());
        return createGathering(host, capacity, location[0], location[1]);
    }

    private Long createGathering(TestUser host, int capacity, double latitude, double longitude) throws Exception {
        String body = call(HttpMethod.POST, "/api/gatherings",
                gatheringJson(Instant.now().plus(1, ChronoUnit.DAYS), capacity, latitude, longitude), host.accessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.summary.id")).longValue();
    }

    private double[] location(Long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT ST_Y(activity_location::geometry) AS lat, ST_X(activity_location::geometry) AS lng
                FROM users WHERE id = ?""",
                (rs, rowNum) -> new double[]{rs.getDouble("lat"), rs.getDouble("lng")}, userId);
    }

    private static String gatheringJson(Instant startsAt, int capacity, double latitude, double longitude) {
        return String.format(Locale.ROOT, """
                {"sportId": %d, "title": "주말 러닝 모임", "description": "천천히 5km 뛰어요",
                 "placeName": "한강공원", "location": {"latitude": %.6f, "longitude": %.6f},
                 "startsAt": "%s", "capacity": %d}
                """, GYM, latitude, longitude, startsAt, capacity);
    }
}
