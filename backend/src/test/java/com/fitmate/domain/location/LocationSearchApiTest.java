package com.fitmate.domain.location;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 테스트 환경에는 카카오 키가 없으므로 내장 지역 목록으로 검색된다. */
class LocationSearchApiTest extends IntegrationTest {

    @Test
    @DisplayName("로그인 없이 지역을 검색할 수 있고, 이름이 검색어로 시작하는 결과가 먼저 나온다")
    void searchWithoutLogin() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=성수", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("성수동"))
                .andExpect(jsonPath("$[0].areaName").value("서울 성동구 성수동1가"))
                .andExpect(jsonPath("$[0].latitude").isNumber())
                .andExpect(jsonPath("$[1].name").value("성수역"));
    }

    @Test
    @DisplayName("띄어쓰기를 무시하고 주소로도 찾는다")
    void searchByAddressIgnoringSpaces() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=마포구 망원", null, null)
                .andExpect(jsonPath("$[0].name").value("망원동"));
    }

    @Test
    @DisplayName("빈 검색어는 빈 목록")
    void emptyQuery() throws Exception {
        call(HttpMethod.GET, "/api/locations/search?query=  ", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
