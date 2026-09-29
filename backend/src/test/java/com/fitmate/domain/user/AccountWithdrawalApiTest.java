package com.fitmate.domain.user;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountWithdrawalApiTest extends IntegrationTest {

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴되지 않는다")
    void wrongPassword() throws Exception {
        TestUser user = signupAndLogin();

        withdraw(user, "wrongPassword1")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        post("/api/auth/login", loginJson(user.loginId(), PASSWORD)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("탈퇴하면 로그인·토큰 재발급이 안 되고, 같은 아이디로 다시 가입할 수 있으며, 프로필 사진 파일도 지워진다")
    void withdraw() throws Exception {
        TestUser user = signupAndLogin();
        String photo = JsonPath.read(mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(new MockMultipartFile("file", "me.jpg", "image/jpeg", TestImages.jpeg(300, 300)))
                        .header("Authorization", "Bearer " + user.accessToken()))
                .andReturn().getResponse().getContentAsString(), "$.profileImageUrl");

        withdraw(user, PASSWORD).andExpect(status().isNoContent());

        post("/api/auth/login", loginJson(user.loginId(), PASSWORD)).andExpect(status().isUnauthorized());
        post("/api/auth/refresh", refreshJson(user.refreshToken())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(photo)).andExpect(status().isNotFound());
        post("/api/auth/signup", signupJson(user.loginId(), PASSWORD, "re_" + uniqueSuffix()))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("탈퇴해도 상대방의 채팅방과 대화는 남고, 보낸 사람은 비워지며 더 이상 메시지를 보낼 수 없다")
    void chatAfterWithdrawal() throws Exception {
        TestUser leaving = signupWithGym();
        TestUser remaining = signupWithGym();
        Long roomId = matchedRoomId(leaving, remaining);
        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "그동안 고마웠어요"}
                """, leaving.accessToken());

        withdraw(leaving, PASSWORD).andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/chat-rooms", null, remaining.accessToken())
                .andExpect(jsonPath("$[0].roomId").value(roomId))
                .andExpect(jsonPath("$[0].counterpart").doesNotExist())
                .andExpect(jsonPath("$[0].canSend").value(false));
        String page = call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, remaining.accessToken())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(page, "$.messages[0].content")).isEqualTo("그동안 고마웠어요");
        assertThat((Object) JsonPath.read(page, "$.messages[0].senderId")).isNull();

        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "잘 지내요"}
                """, remaining.accessToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_UNAVAILABLE"));
    }

    private ResultActions withdraw(TestUser user, String password) throws Exception {
        return call(HttpMethod.POST, "/api/users/me/withdrawal", """
                {"password": "%s"}
                """.formatted(password), user.accessToken());
    }
}
