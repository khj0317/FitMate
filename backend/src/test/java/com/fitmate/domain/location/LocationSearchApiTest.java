package com.fitmate.domain.location;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 테스트 환경에는 카카오 키가 없으므로 내장 전국 행정구역 목록으로 검색된다. */
class LocationSearchApiTest extends IntegrationTest {

    @Test
    @DisplayName("로그인 없이 검색할 수 있고, 구가 동보다 먼저 나온다 (도봉 → 도봉구, 도봉산역, 도봉1동...)")
    void districtBeforeDong() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=도봉", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("도봉구"))
                .andExpect(jsonPath("$[0].areaName").value("서울 도봉구"))
                .andExpect(jsonPath("$[0].latitude").isNumber())
                .andExpect(jsonPath("$[*].name", hasItem("도봉1동")))
                .andExpect(jsonPath("$[*].areaName", hasItem("서울 도봉구 도봉2동")));
    }

    @Test
    @DisplayName("일반구가 있는 시는 시 → 구 순서로 나온다 (부천 → 부천시, 부천시 원미구...)")
    void cityWithDistricts() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=부천", null, null)
                .andExpect(jsonPath("$[0].name").value("부천시"))
                .andExpect(jsonPath("$[0].areaName").value("경기 부천시"))
                .andExpect(jsonPath("$[*].name", hasItem("부천시 원미구")))
                .andExpect(jsonPath("$[*].name", hasItem("부천시 소사구")));
    }

    @Test
    @DisplayName("사람들이 부르는 동 이름으로도 행정동을 찾는다 (망원동 → 망원1동, 망원2동)")
    void colloquialDongName() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=망원동", null, null)
                .andExpect(jsonPath("$[*].name", hasItem("망원1동")))
                .andExpect(jsonPath("$[*].name", hasItem("망원2동")));
    }

    @Test
    @DisplayName("띄어쓰기를 무시하고 주소로도 찾는다")
    void searchByAddressIgnoringSpaces() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=마포구 망원", null, null)
                .andExpect(jsonPath("$[0].areaName").value("서울 마포구 망원1동"));
    }

    @Test
    @DisplayName("역 이름으로 찾는다")
    void station() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=성수역", null, null)
                .andExpect(jsonPath("$[0].name").value("성수역"));
    }

    @Test
    @DisplayName("빈 검색어는 빈 목록")
    void emptyQuery() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=  ", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
