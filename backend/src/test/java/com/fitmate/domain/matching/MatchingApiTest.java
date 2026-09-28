package com.fitmate.domain.matching;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다른 테스트가 만든 사용자와 섞이지 않도록 테스트마다 지구 위 임의의 지점을 기준으로 사용자를 배치한다.
 */
class MatchingApiTest extends IntegrationTest {

    private static final int GYM = 1;
    private static final int RUNNING = 2;
    private static final int CLIMBING = 3;

    private double baseLat;
    private double baseLng;

    private TestUser me;
    private Long nearId;
    private Long runnerId;
    private Long midId;
    private Long farId;

    @BeforeEach
    void setUp() throws Exception {
        baseLat = ThreadLocalRandom.current().nextDouble(-60, 60);
        baseLng = ThreadLocalRandom.current().nextDouble(-170, 170);

        // 나: 헬스 중급, 러닝 초급 / 월 19~21시, 토 9~12시 / 검색 반경 기본 5km
        me = signupAndLogin();
        placeAt(me, 0, 0);
        setSports(me, sport(GYM, "INTERMEDIATE"), sport(RUNNING, "BEGINNER"));
        setTimes(me, time("MONDAY", "19:00", "21:00"), time("SATURDAY", "09:00", "12:00"));

        // 0.9km, 헬스 중급(같은 실력), 월 19~21시(2시간 겹침) → 1순위
        TestUser near = signupAndLogin();
        nearId = placeAt(near, 0.9, 0);
        setSports(near, sport(GYM, "INTERMEDIATE"));
        setTimes(near, time("MONDAY", "19:00", "21:00"));

        // 2km, 러닝 초급(같은 실력), 겹치는 시간 없음 → 2순위
        TestUser runner = signupAndLogin();
        runnerId = placeAt(runner, 0, 2);
        setSports(runner, sport(RUNNING, "BEGINNER"));

        // 3km, 헬스 고급(한 단계 차이), 겹치는 시간 없음 → 3순위
        TestUser mid = signupAndLogin();
        midId = placeAt(mid, -3, 0);
        setSports(mid, sport(GYM, "ADVANCED"));

        // 8km, 헬스 → 기본 반경(5km) 밖
        TestUser far = signupAndLogin();
        farId = placeAt(far, 8, 0);
        setSports(far, sport(GYM, "INTERMEDIATE"));

        // 1km, 클라이밍만 함 → 공통 종목 없음
        TestUser climber = signupAndLogin();
        placeAt(climber, 0, -1);
        setSports(climber, sport(CLIMBING, "BEGINNER"));
    }

    @Test
    @DisplayName("반경 안에서 공통 종목이 있는 사람만 매칭 점수 순으로 추천한다")
    void recommendByScore() throws Exception {
        String body = recommend("").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nickname").exists())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).containsExactly(nearId, runnerId, midId);

        // 1순위 상세: 같은 실력(30) + 2시간 겹침(25 * 120/180 ≈ 17) + 거리 1km로 올림 표시
        assertThat((Integer) JsonPath.read(body, "$[0].scoreDetail.skill")).isEqualTo(30);
        assertThat((Integer) JsonPath.read(body, "$[0].scoreDetail.time")).isEqualTo(17);
        assertThat((Integer) JsonPath.read(body, "$[0].overlapMinutesPerWeek")).isEqualTo(120);
        assertThat((Double) JsonPath.read(body, "$[0].approximateDistanceKm")).isEqualTo(1.0);
        assertThat((String) JsonPath.read(body, "$[0].commonSports[0].name")).isEqualTo("헬스");
    }

    @Test
    @DisplayName("종목을 지정하면 그 종목을 하는 사람만 추천한다")
    void filterBySport() throws Exception {
        String body = recommend("?sportId=" + GYM).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).containsExactly(nearId, midId);
    }

    @Test
    @DisplayName("검색 반경을 넓히면 멀리 있는 사람도 포함된다")
    void widerRadius() throws Exception {
        String body = recommend("?radiusKm=10").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).contains(farId).hasSize(4);
    }

    @Test
    @DisplayName("limit만큼만 반환한다")
    void limit() throws Exception {
        String body = recommend("?limit=1").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(ids(body)).containsExactly(nearId);
    }

    @Test
    @DisplayName("활동 지역이 없으면 LOCATION_REQUIRED")
    void locationRequired() throws Exception {
        TestUser noLocation = signupAndLogin();
        setSports(noLocation, sport(GYM, "BEGINNER"));

        call(HttpMethod.GET, "/api/matching/recommendations", null, noLocation.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));
    }

    @Test
    @DisplayName("등록한 종목이 없고 종목도 지정하지 않으면 SPORT_REQUIRED, 종목을 지정하면 검색 가능")
    void sportRequired() throws Exception {
        TestUser noSports = signupAndLogin();
        placeAt(noSports, 0, 0.1);

        call(HttpMethod.GET, "/api/matching/recommendations", null, noSports.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPORT_REQUIRED"));

        String body = call(HttpMethod.GET, "/api/matching/recommendations?sportId=" + GYM, null, noSports.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ids(body)).contains(nearId, midId);
        // 내 실력을 모르므로 실력 점수는 중간값(15)
        assertThat((Integer) JsonPath.read(body, "$[0].scoreDetail.skill")).isEqualTo(15);
    }

    @Test
    @DisplayName("잘못된 파라미터는 400, 없는 종목은 404")
    void invalidParameters() throws Exception {
        recommend("?radiusKm=100").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        recommend("?sportId=999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPORT_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions recommend(String query) throws Exception {
        return call(HttpMethod.GET, "/api/matching/recommendations" + query, null, me.accessToken());
    }

    /** 기준점에서 북쪽으로 northKm, 동쪽으로 eastKm 떨어진 곳에 배치하고 사용자 ID를 반환한다. */
    private Long placeAt(TestUser user, double northKm, double eastKm) throws Exception {
        double lat = baseLat + northKm / 111.0;
        double lng = baseLng + eastKm / (111.0 * Math.cos(Math.toRadians(baseLat)));
        String json = String.format(Locale.ROOT,
                "{\"latitude\": %.7f, \"longitude\": %.7f, \"areaName\": \"테스트 지역\"}", lat, lng);
        String body = call(HttpMethod.PUT, "/api/users/me/location", json, user.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private void setSports(TestUser user, String... sports) throws Exception {
        call(HttpMethod.PUT, "/api/users/me/sports",
                "{\"sports\": [" + String.join(",", sports) + "]}", user.accessToken())
                .andExpect(status().isOk());
    }

    private void setTimes(TestUser user, String... times) throws Exception {
        call(HttpMethod.PUT, "/api/users/me/available-times",
                "{\"availableTimes\": [" + String.join(",", times) + "]}", user.accessToken())
                .andExpect(status().isOk());
    }

    private static String sport(int sportId, String level) {
        return "{\"sportId\": %d, \"skillLevel\": \"%s\"}".formatted(sportId, level);
    }

    private static String time(String day, String start, String end) {
        return "{\"dayOfWeek\": \"%s\", \"startTime\": \"%s\", \"endTime\": \"%s\"}".formatted(day, start, end);
    }

    private static List<Long> ids(String body) {
        List<Number> ids = JsonPath.read(body, "$[*].userId");
        return ids.stream().map(Number::longValue).toList();
    }
}
