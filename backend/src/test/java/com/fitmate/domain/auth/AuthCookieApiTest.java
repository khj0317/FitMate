package com.fitmate.domain.auth;

import com.fitmate.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 웹(X-Auth-Mode: cookie)은 리프레시 토큰을 HttpOnly 쿠키로만 주고받는다 */
class AuthCookieApiTest extends IntegrationTest {

    @Test
    @DisplayName("쿠키 모드로 로그인하면 리프레시 토큰은 HttpOnly·SameSite=Strict 쿠키로만 오고 본문에는 없다")
    void loginSetsHttpOnlyCookie() throws Exception {
        TestUser user = signupAndLogin();

        MockHttpServletResponse response = mockMvc.perform(cookieMode("/api/auth/login").content(loginJson(user.loginId(), PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshTokenCookie.NAME + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Secure", "Max-Age=1209600");
    }

    @Test
    @DisplayName("쿠키만으로 재발급할 수 있고, 한 번 쓴 쿠키는 다시 쓸 수 없으며, 로그아웃하면 쿠키가 지워진다")
    void refreshAndLogoutWithCookie() throws Exception {
        TestUser user = signupAndLogin();
        Cookie first = refreshCookieOf(mockMvc.perform(cookieMode("/api/auth/login")
                .content(loginJson(user.loginId(), PASSWORD))).andReturn().getResponse());

        MockHttpServletResponse refreshed = mockMvc.perform(cookieMode("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn().getResponse();
        Cookie second = refreshCookieOf(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // 이미 교체된 쿠키로는 안 된다. 이때 쿠키를 지우라고 응답하면, 다른 탭이 방금 받은 새 쿠키까지
        // 브라우저에서 지워져 모든 탭이 로그아웃되므로 Set-Cookie를 보내지 않는다
        MockHttpServletResponse reused = mockMvc.perform(cookieMode("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse();
        assertThat(reused.getHeader(HttpHeaders.SET_COOKIE)).isNull();
        mockMvc.perform(cookieMode("/api/auth/refresh").cookie(second)).andExpect(status().isOk());
        second = refreshCookieOf(mockMvc.perform(cookieMode("/api/auth/login")
                .content(loginJson(user.loginId(), PASSWORD))).andReturn().getResponse());

        MockHttpServletResponse loggedOut = mockMvc.perform(cookieMode("/api/auth/logout").cookie(second))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();
        assertThat(loggedOut.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        mockMvc.perform(cookieMode("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("쿠키도 본문도 없으면 재발급할 수 없다")
    void refreshWithoutTokenFails() throws Exception {
        mockMvc.perform(cookieMode("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("헤더 없이 부르면(앱) 지금처럼 본문으로 주고받고 쿠키는 만들지 않는다")
    void bodyModeStillWorks() throws Exception {
        TestUser user = signupAndLogin();
        MockHttpServletResponse response = post("/api/auth/refresh", refreshJson(user.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    private static MockHttpServletRequestBuilder cookieMode(String url) {
        return request(org.springframework.http.HttpMethod.POST, url)
                .header(RefreshTokenCookie.MODE_HEADER, "cookie")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private static Cookie refreshCookieOf(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(RefreshTokenCookie.NAME);
        assertThat(cookie).isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }
}
