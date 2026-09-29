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
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatheringNotifierTest extends IntegrationTest {

    @Autowired
    GatheringNotifier notifier;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("시작 1시간 전이 되면 참여자 모두에게 리마인더를 한 번만 보낸다")
    void reminderIsSentOnce() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        long gatheringId = createGathering(host);
        join(guest, gatheringId);
        jdbcTemplate.update("UPDATE gatherings SET starts_at = now() + interval '30 minutes' WHERE id = ?", gatheringId);

        // 서버 여러 대가 동시에 실행한 상황: 한 번만 보내야 한다
        List<Integer> sent = runConcurrently(4, index -> notifier.sendReminders());
        assertThat(sent.stream().mapToInt(Integer::intValue).sum()).isGreaterThanOrEqualTo(1);
        notifier.sendReminders();

        for (TestUser user : List.of(host, guest)) {
            String body = call(HttpMethod.GET, "/api/notifications", null, user.accessToken())
                    .andReturn().getResponse().getContentAsString();
            List<String> types = JsonPath.read(body, "$.items[*].type");
            assertThat(types).filteredOn("GATHERING_REMINDER"::equals).hasSize(1);
        }
    }

    @Test
    @DisplayName("아직 1시간 넘게 남은 모임과 취소된 모임에는 리마인더를 보내지 않는다")
    void noReminderTooEarlyOrCanceled() throws Exception {
        TestUser host = signupAndLogin();
        long later = createGathering(host);
        long canceled = createGathering(host);
        jdbcTemplate.update("UPDATE gatherings SET starts_at = now() + interval '30 minutes' WHERE id = ?", canceled);
        call(HttpMethod.DELETE, "/api/gatherings/" + canceled, null, host.accessToken())
                .andExpect(status().isNoContent());

        notifier.sendReminders();

        call(HttpMethod.GET, "/api/notifications", null, host.accessToken())
                .andExpect(jsonPath("$.items[*].type").value(not(hasItem("GATHERING_REMINDER"))));
        assertThat(jdbcTemplate.queryForObject("SELECT reminder_sent_at IS NULL FROM gatherings WHERE id = ?",
                Boolean.class, later)).isTrue();
    }

    @Test
    @DisplayName("모임이 끝나면 함께한 참여자에게 매너 평가를 요청한다 (혼자였으면 보내지 않음)")
    void reviewPrompt() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        TestUser lonelyHost = signupAndLogin();
        long together = createGathering(host);
        join(guest, together);
        long alone = createGathering(lonelyHost);
        jdbcTemplate.update("UPDATE gatherings SET starts_at = now() - interval '3 hours' WHERE id IN (?, ?)",
                together, alone);

        notifier.sendReviewPrompts();
        notifier.sendReviewPrompts();

        call(HttpMethod.GET, "/api/notifications", null, guest.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("REVIEW_REQUESTED"))
                .andExpect(jsonPath("$.items[0].link").value("/gatherings/" + together));
        String body = call(HttpMethod.GET, "/api/notifications", null, host.accessToken())
                .andReturn().getResponse().getContentAsString();
        List<String> types = JsonPath.read(body, "$.items[*].type");
        assertThat(types).filteredOn("REVIEW_REQUESTED"::equals).hasSize(1);
        call(HttpMethod.GET, "/api/notifications", null, lonelyHost.accessToken())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @DisplayName("모임 알림을 꺼 두면 리마인더를 받지 않는다")
    void mutedCategoryIsSkipped() throws Exception {
        TestUser host = signupAndLogin();
        TestUser guest = signupAndLogin();
        long gatheringId = createGathering(host);
        join(guest, gatheringId);
        call(HttpMethod.PUT, "/api/notifications/settings", """
                {"muted": ["GATHERING"]}
                """, guest.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted[0]").value("GATHERING"));
        jdbcTemplate.update("UPDATE gatherings SET starts_at = now() + interval '30 minutes' WHERE id = ?", gatheringId);

        notifier.sendReminders();

        call(HttpMethod.GET, "/api/notifications", null, guest.accessToken())
                .andExpect(jsonPath("$.items.length()").value(0));
        call(HttpMethod.GET, "/api/notifications", null, host.accessToken())
                .andExpect(jsonPath("$.items[*].type").value(hasItem("GATHERING_REMINDER")));
        call(HttpMethod.GET, "/api/notifications/settings", null, guest.accessToken())
                .andExpect(jsonPath("$.muted.length()").value(1));
    }

    private long createGathering(TestUser host) throws Exception {
        String json = String.format(Locale.ROOT, """
                {"sportId": %d, "title": "알림 테스트 모임", "placeName": "체육관",
                 "location": {"latitude": 37.5, "longitude": 127.0},
                 "startsAt": "%s", "capacity": 4}
                """, GYM, Instant.now().plus(1, ChronoUnit.DAYS));
        String body = call(HttpMethod.POST, "/api/gatherings", json, host.accessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.summary.id")).longValue();
    }

    private void join(TestUser user, long gatheringId) throws Exception {
        call(HttpMethod.POST, "/api/gatherings/" + gatheringId + "/participants", null, user.accessToken())
                .andExpect(status().isOk());
    }
}
