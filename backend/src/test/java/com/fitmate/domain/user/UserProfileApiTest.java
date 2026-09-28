package com.fitmate.domain.user;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserProfileApiTest extends IntegrationTest {

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
                {"bio": "주 3회 헬스합니다", "gender": "FEMALE", "birthYear": 1998, "searchRadiusKm": 10}
                """, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(user.nickname()))
                .andExpect(jsonPath("$.bio").value("주 3회 헬스합니다"))
                .andExpect(jsonPath("$.gender").value("FEMALE"))
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
    @DisplayName("다른 사람 프로필에는 이메일, 좌표, 출생 연도가 노출되지 않는다")
    void publicProfileHidesPrivateFields() throws Exception {
        TestUser target = signupAndLogin();
        TestUser viewer = signupAndLogin();

        call(HttpMethod.PUT, "/api/users/me/location", """
                {"latitude": 37.5445, "longitude": 127.0557, "areaName": "서울 성동구 성수동"}
                """, target.accessToken());
        String me = call(HttpMethod.PATCH, "/api/users/me", """
                {"birthYear": 1998}
                """, target.accessToken()).andReturn().getResponse().getContentAsString();
        Integer targetId = JsonPath.read(me, "$.id");

        call(HttpMethod.GET, "/api/users/" + targetId, null, viewer.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value(target.nickname()))
                .andExpect(jsonPath("$.activityAreaName").value("서울 성동구 성수동"))
                .andExpect(jsonPath("$.ageGroup").exists())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.location").doesNotExist())
                .andExpect(jsonPath("$.birthYear").doesNotExist());
    }

    @Test
    @DisplayName("없는 사용자 프로필은 404")
    void publicProfileNotFound() throws Exception {
        TestUser viewer = signupAndLogin();

        call(HttpMethod.GET, "/api/users/999999999", null, viewer.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
