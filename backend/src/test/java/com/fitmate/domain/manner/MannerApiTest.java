package com.fitmate.domain.manner;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
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

class MannerApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("끝난 모임의 참가자끼리 서로 평가할 수 있고, 평가하면 목록에서 빠진다")
    void reviewGatheringParticipant() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = finishedGathering(host, List.of(guest));

        call(HttpMethod.GET, "/api/manner/pending", null, guest.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.gatheringId == %d)].targetId".formatted(gatheringId))
                        .value(hasItem(host.id().intValue())));

        review(guest, host, gatheringId, null, "GOOD", "\"PUNCTUAL\", \"KIND\"").andExpect(status().isCreated());

        assertThat(mannerScore(host.id())).isEqualByComparingTo("37.0");
        call(HttpMethod.GET, "/api/manner/pending", null, guest.accessToken())
                .andExpect(jsonPath("$[*].gatheringId").value(not(hasItem(gatheringId.intValue()))));
        call(HttpMethod.GET, "/api/users/" + host.id() + "/manner", null, guest.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.tags.length()").value(2));
        call(HttpMethod.GET, "/api/notifications", null, host.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("MANNER_REVIEW_RECEIVED"));
    }

    @Test
    @DisplayName("같은 모임에서 같은 사람을 두 번 평가할 수 없다")
    void rejectDuplicateReview() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = finishedGathering(host, List.of(guest));

        review(guest, host, gatheringId, null, "GOOD", "").andExpect(status().isCreated());
        review(guest, host, gatheringId, null, "BAD", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REVIEW"));
        assertThat(mannerScore(host.id())).isEqualByComparingTo("37.0");
    }

    @Test
    @DisplayName("함께 운동하지 않았거나 아직 시작 전인 모임은 평가할 수 없다")
    void rejectIneligibleReview() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        TestUser stranger = signupAndLogin();
        Long finished = finishedGathering(host, List.of(guest));

        review(stranger, host, finished, null, "GOOD", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        review(host, host, finished, null, "GOOD", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

        Long upcoming = createGathering(host, 4);
        join(guest, upcoming);
        review(guest, host, upcoming, null, "GOOD", "").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("평가와 맞지 않는 태그는 거절한다")
    void rejectMismatchedTags() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = finishedGathering(host, List.of(guest));

        review(guest, host, gatheringId, null, "GOOD", "\"RUDE\"").andExpect(status().isBadRequest());
        review(guest, host, gatheringId, null, "NORMAL", "\"KIND\"").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("노쇼는 기본 감점에 추가 감점이 붙고, 아쉬운 태그는 공개되지 않는다")
    void noShowPenalty() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        Long gatheringId = finishedGathering(host, List.of(guest));

        review(host, guest, gatheringId, null, "BAD", "\"NO_SHOW\"").andExpect(status().isCreated());

        assertThat(mannerScore(guest.id())).isEqualByComparingTo("35.0");
        call(HttpMethod.GET, "/api/users/" + guest.id() + "/manner", null, guest.accessToken())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.tags.length()").value(0));
    }

    @Test
    @DisplayName("수락된 1:1 매칭 상대도 평가할 수 있다")
    void reviewMatchPartner() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        matchedRoomId(requester, receiver);

        String pending = call(HttpMethod.GET, "/api/manner/pending", null, requester.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(pending, "$[?(@.targetId == %d)].matchRequestId".formatted(receiver.id()));
        assertThat(ids).hasSize(1);

        review(requester, receiver, null, ids.get(0).longValue(), "GOOD", "\"FUN\"").andExpect(status().isCreated());
        review(requester, receiver, null, null, "GOOD", "").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("여러 명이 동시에 평가해도 점수가 빠짐없이 반영된다")
    void concurrentReviews() throws Exception {
        TestUser host = signupAndLogin();
        List<TestUser> guests = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            guests.add(signupAndLogin());
        }
        Long gatheringId = finishedGathering(host, guests);

        List<Integer> statuses = runConcurrently(8, index ->
                review(guests.get(index), host, gatheringId, null, "GOOD", "").andReturn().getResponse().getStatus());

        assertThat(statuses).containsOnly(201);
        assertThat(mannerScore(host.id())).isEqualByComparingTo("40.5");
    }

    /** 모임을 만들어 참가시킨 뒤, 시작 시각을 한 시간 전으로 옮겨 "끝난 모임"으로 만든다 */
    private Long finishedGathering(TestUser host, List<TestUser> guests) throws Exception {
        Long gatheringId = createGathering(host, guests.size() + 1);
        for (TestUser guest : guests) {
            join(guest, gatheringId);
        }
        jdbcTemplate.update("UPDATE gatherings SET starts_at = now() - interval '1 hour' WHERE id = ?", gatheringId);
        return gatheringId;
    }

    private Long createGathering(TestUser host, int capacity) throws Exception {
        String json = String.format(Locale.ROOT, """
                {"sportId": %d, "title": "평가 테스트 모임", "placeName": "체육관",
                 "location": {"latitude": 37.5, "longitude": 127.0},
                 "startsAt": "%s", "capacity": %d}
                """, GYM, Instant.now().plus(1, ChronoUnit.DAYS), Math.max(2, capacity));
        String body = call(HttpMethod.POST, "/api/gatherings", json, host.accessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.summary.id")).longValue();
    }

    private void join(TestUser user, Long gatheringId) throws Exception {
        call(HttpMethod.POST, "/api/gatherings/" + gatheringId + "/participants", null, user.accessToken())
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions review(TestUser reviewer, TestUser target,
                                                                      Long gatheringId, Long matchRequestId,
                                                                      String rating, String tags) throws Exception {
        return call(HttpMethod.POST, "/api/manner/reviews", """
                {"targetId": %d, "gatheringId": %s, "matchRequestId": %s, "rating": "%s", "tags": [%s]}
                """.formatted(target.id(), gatheringId, matchRequestId, rating, tags), reviewer.accessToken());
    }

    private BigDecimal mannerScore(Long userId) {
        return jdbcTemplate.queryForObject("SELECT manner_score FROM users WHERE id = ?", BigDecimal.class, userId);
    }
}
