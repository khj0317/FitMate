package com.fitmate.domain.user;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileApiTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("운동 종목 목록은 로그인 없이 조회할 수 있다")
    void getSportsWithoutLogin() throws Exception {
        call(HttpMethod.GET, "/api/sports", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].code").value("GYM"));
    }

    @Test
    @DisplayName("프로필은 보낸 필드만 수정된다")
    void patchProfile() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PATCH, "/api/users/me", """
                {"bio": "주 3회 헬스합니다", "gender": "FEMALE", "birthDate": "2000-01-15", "searchRadiusKm": 10}
                """, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(user.nickname()))
                .andExpect(jsonPath("$.bio").value("주 3회 헬스합니다"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value("2000-01-15"))
                .andExpect(jsonPath("$.searchRadiusKm").value(10));

        call(HttpMethod.PATCH, "/api/users/me", """
                {"searchRadiusKm": 3}
                """, user.accessToken())
                .andExpect(jsonPath("$.bio").value("주 3회 헬스합니다"))
                .andExpect(jsonPath("$.searchRadiusKm").value(3));
    }

    @Test
    @DisplayName("다른 사람이 쓰는 닉네임으로는 바꿀 수 없다")
    void changeToDuplicateNickname() throws Exception {
        TestUser me = signupAndLogin();
        TestUser other = signupAndLogin();

        call(HttpMethod.PATCH, "/api/users/me", """
                {"nickname": "%s"}
                """.formatted(other.nickname()), me.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("활동 지역을 저장하면 PostGIS 좌표로 저장되고 다시 위도/경도로 조회된다")
    void updateLocation() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/location", """
                {"latitude": 37.5445, "longitude": 127.0557, "areaName": "서울 성동구 성수동"}
                """, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location.latitude").value(37.5445))
                .andExpect(jsonPath("$.location.longitude").value(127.0557))
                .andExpect(jsonPath("$.location.areaName").value("서울 성동구 성수동"));

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.location.latitude").value(37.5445));
    }

    @Test
    @DisplayName("운동 종목은 전체 교체되며 기존 종목의 실력만 바뀌어도 PK 충돌 없이 반영된다")
    void replaceSports() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/sports", """
                {"sports": [{"sportId": 1, "skillLevel": "BEGINNER"}, {"sportId": 2, "skillLevel": "ADVANCED"}]}
                """, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sports", hasSize(2)));

        // 1번 유지(실력 변경), 2번 삭제, 3번 추가
        call(HttpMethod.PUT, "/api/users/me/sports", """
                {"sports": [{"sportId": 1, "skillLevel": "INTERMEDIATE"}, {"sportId": 3, "skillLevel": "BEGINNER"}]}
                """, user.accessToken())
                .andExpect(status().isOk());

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.sports", hasSize(2)))
                .andExpect(jsonPath("$.sports[0].sportId").value(1))
                .andExpect(jsonPath("$.sports[0].skillLevel").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.sports[1].code").value("CLIMBING"));
    }

    @Test
    @DisplayName("없는 종목이나 중복 종목은 등록할 수 없다")
    void invalidSports() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/sports", """
                {"sports": [{"sportId": 999, "skillLevel": "BEGINNER"}]}
                """, user.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPORT_NOT_FOUND"));

        call(HttpMethod.PUT, "/api/users/me/sports", """
                {"sports": [{"sportId": 1, "skillLevel": "BEGINNER"}, {"sportId": 1, "skillLevel": "ADVANCED"}]}
                """, user.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SPORT"));
    }

    @Test
    @DisplayName("운동 가능 시간대는 요일·시간순으로 정렬되어 저장된다")
    void replaceAvailableTimes() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/available-times", """
                {"availableTimes": [
                  {"dayOfWeek": "SATURDAY", "startTime": "09:00", "endTime": "12:00"},
                  {"dayOfWeek": "MONDAY", "startTime": "19:00", "endTime": "21:00"},
                  {"dayOfWeek": "MONDAY", "startTime": "07:00", "endTime": "08:00"}
                ]}
                """, user.accessToken())
                .andExpect(status().isOk());

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.availableTimes", hasSize(3)))
                .andExpect(jsonPath("$.availableTimes[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.availableTimes[0].startTime").value("07:00"))
                .andExpect(jsonPath("$.availableTimes[2].dayOfWeek").value("SATURDAY"));
    }

    @Test
    @DisplayName("운동 가능 시각은 서버 시간대와 상관없이 입력한 그대로 DB에 저장된다")
    void availableTimesAreStoredAsWallClock() throws Exception {
        TestUser user = signupAndLogin();
        String me = call(HttpMethod.PUT, "/api/users/me/available-times", """
                {"availableTimes": [{"dayOfWeek": "SATURDAY", "startTime": "08:00", "endTime": "10:00"}]}
                """, user.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer userId = JsonPath.read(me, "$.id");

        // 과거 버그: hibernate.jdbc.time_zone=UTC 설정 때문에 KST 08:00이 23:00으로 저장됐다
        String stored = jdbcClient.sql("SELECT start_time::text FROM user_available_times WHERE user_id = ?")
                .param(userId)
                .query(String.class)
                .single();
        assertThat(stored).isEqualTo("08:00:00");
    }

    @Test
    @DisplayName("같은 요일에 겹치는 시간대는 등록할 수 없다")
    void overlappingAvailableTimes() throws Exception {
        TestUser user = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/available-times", """
                {"availableTimes": [
                  {"dayOfWeek": "MONDAY", "startTime": "19:00", "endTime": "21:00"},
                  {"dayOfWeek": "MONDAY", "startTime": "20:00", "endTime": "22:00"}
                ]}
                """, user.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERLAPPING_AVAILABLE_TIMES"));
    }

    @Test
    @DisplayName("다른 사람 프로필에는 아이디, 이메일, 좌표, 생년월일이 노출되지 않는다")
    void publicProfileHidesPrivateFields() throws Exception {
        TestUser target = signupAndLogin();
        TestUser viewer = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/location", """
                {"latitude": 37.5445, "longitude": 127.0557, "areaName": "서울 성동구 성수동"}
                """, target.accessToken());
        String me = call(HttpMethod.PATCH, "/api/users/me", """
                {"email": "target_%s@fitmate.com"}
                """.formatted(uniqueSuffix()), target.accessToken()).andReturn().getResponse().getContentAsString();
        Integer targetId = JsonPath.read(me, "$.id");

        call(HttpMethod.GET, "/api/users/" + targetId, null, viewer.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(target.nickname()))
                .andExpect(jsonPath("$.activityAreaName").value("서울 성동구 성수동"))
                .andExpect(jsonPath("$.ageGroup").exists())
                .andExpect(jsonPath("$.loginId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    @Test
    @DisplayName("없는 사용자 프로필은 404")
    void publicProfileNotFound() throws Exception {
        TestUser viewer = signupAndLogin();

        call(HttpMethod.GET, "/api/users/999999999", null, viewer.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("프로필 전체 저장(PUT)은 기본 정보·지역·종목·시간을 한 번에 저장한다")
    void updateAllAtOnce() throws Exception {
        TestUser user = signupAndLogin();
        String nickname = "all_" + uniqueSuffix();

        call(HttpMethod.PUT, "/api/users/me", updateAllJson(nickname, 2), user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(nickname))
                .andExpect(jsonPath("$.email").value(nickname + "@fitmate.com"))
                .andExpect(jsonPath("$.bio").value("같이 뛰어요"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
                .andExpect(jsonPath("$.birthDate").value("1995-08-30"))
                .andExpect(jsonPath("$.searchRadiusKm").value(7))
                .andExpect(jsonPath("$.location.areaName").value("서울 마포구 망원동"))
                .andExpect(jsonPath("$.sports[0].code").value("RUNNING"))
                .andExpect(jsonPath("$.availableTimes[0].dayOfWeek").value("TUESDAY"));
    }

    @Test
    @DisplayName("프로필 전체 저장 중 하나라도 실패하면 아무것도 저장되지 않는다 (트랜잭션)")
    void updateAllIsAtomic() throws Exception {
        TestUser user = signupAndLogin();

        // 닉네임은 정상이지만 종목 ID가 없는 값이라 마지막 단계에서 실패
        call(HttpMethod.PUT, "/api/users/me", updateAllJson("atomic_" + uniqueSuffix(), 999), user.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPORT_NOT_FOUND"));

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.nickname").value(user.nickname()))
                .andExpect(jsonPath("$.location.areaName").value("테스트 지역"))
                .andExpect(jsonPath("$.gender").value("MALE"));
    }

    @Test
    @DisplayName("다른 사람이 쓰는 이메일로는 바꿀 수 없고, 빈 값으로 보내면 이메일이 지워진다")
    void changeEmail() throws Exception {
        TestUser me = signupAndLogin();
        TestUser other = signupAndLogin();
        String email = "taken_" + uniqueSuffix() + "@fitmate.com";
        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\"}".formatted(email), other.accessToken())
                .andExpect(status().isOk());

        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"%s\"}".formatted(email), me.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));

        call(HttpMethod.PATCH, "/api/users/me", "{\"email\": \"\"}", other.accessToken())
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()));
    }

    private static String updateAllJson(String nickname, int sportId) {
        return """
                {"nickname": "%s", "email": "%s@fitmate.com", "bio": "같이 뛰어요", "gender": "FEMALE",
                 "birthDate": "1995-08-30", "searchRadiusKm": 7,
                 "location": {"latitude": 37.5561, "longitude": 126.9101, "areaName": "서울 마포구 망원동"},
                 "sports": [{"sportId": %d, "skillLevel": "INTERMEDIATE"}],
                 "availableTimes": [{"dayOfWeek": "TUESDAY", "startTime": "07:00", "endTime": "08:30"}]}
                """.formatted(nickname, nickname, sportId);
    }
}
