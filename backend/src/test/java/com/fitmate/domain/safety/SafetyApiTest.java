package com.fitmate.domain.safety;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SafetyApiTest extends IntegrationTest {

    @Test
    @DisplayName("차단하면 대기 중인 요청이 취소되고, 어느 쪽에서도 다시 요청할 수 없다")
    void blockCancelsAndPreventsRequests() throws Exception {
        TestUser me = signupWithGym();
        TestUser other = signupWithGym();
        createdMatchRequestId(other, me);

        block(me, other).andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/match-requests/received", null, me.accessToken())
                .andExpect(jsonPath("$", hasSize(0)));
        requestMatch(other, me).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_UNAVAILABLE"));
        requestMatch(me, other).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_UNAVAILABLE"));
    }

    @Test
    @DisplayName("차단하면 채팅을 주고받을 수 없고, 차단한 사람의 목록에서는 방이 숨겨진다. 해제하면 돌아온다")
    void blockChat() throws Exception {
        TestUser me = signupWithGym();
        TestUser other = signupWithGym();
        Long roomId = matchedRoomId(me, other);

        block(me, other);

        sendText(other, roomId).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_UNAVAILABLE"));
        sendText(me, roomId).andExpect(status().isForbidden());
        call(HttpMethod.GET, "/api/chat-rooms", null, me.accessToken())
                .andExpect(jsonPath("$", hasSize(0)));
        call(HttpMethod.GET, "/api/chat-rooms", null, other.accessToken())
                .andExpect(jsonPath("$[0].canSend").value(false));

        call(HttpMethod.DELETE, "/api/users/" + other.id() + "/block", null, me.accessToken())
                .andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/chat-rooms", null, me.accessToken())
                .andExpect(jsonPath("$[0].canSend").value(true));
        sendText(other, roomId).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("차단 관계면 서로의 프로필이 없는 사용자처럼 보인다")
    void blockHidesProfile() throws Exception {
        TestUser me = signupAndLogin();
        TestUser other = signupAndLogin();

        block(me, other);

        call(HttpMethod.GET, "/api/users/" + other.id(), null, me.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/users/" + me.id(), null, other.accessToken())
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("차단 목록을 보고, 같은 사람을 여러 번 차단해도 한 번만 기록된다. 자기 자신은 차단할 수 없다")
    void blockList() throws Exception {
        TestUser me = signupAndLogin();
        TestUser other = signupAndLogin();

        block(me, other).andExpect(status().isNoContent());
        block(me, other).andExpect(status().isNoContent());
        block(me, me).andExpect(status().isBadRequest());

        call(HttpMethod.GET, "/api/users/me/blocks", null, me.accessToken())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(other.id()))
                .andExpect(jsonPath("$[0].nickname").value(other.nickname()));
    }

    @Test
    @DisplayName("신고는 사유와 함께 저장되고, 검토 중에는 같은 사람을 다시 신고할 수 없다. 함께 차단할 수 있다")
    void report() throws Exception {
        TestUser me = signupWithGym();
        TestUser other = signupWithGym();

        report(me, other, "NO_SHOW", true).andExpect(status().isCreated());
        report(me, other, "SPAM", false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REPORT"));

        String blocks = call(HttpMethod.GET, "/api/users/me/blocks", null, me.accessToken())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(((Number) JsonPath.read(blocks, "$[0].userId")).longValue())
                .isEqualTo(other.id());

        report(me, other, "WRONG_REASON", false).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions block(TestUser me, TestUser target) throws Exception {
        return call(HttpMethod.PUT, "/api/users/" + target.id() + "/block", null, me.accessToken());
    }

    private org.springframework.test.web.servlet.ResultActions report(TestUser me, TestUser target, String reason,
                                                                      boolean block) throws Exception {
        return call(HttpMethod.POST, "/api/users/" + target.id() + "/report", """
                {"reason": "%s", "detail": "약속 장소에 나오지 않았어요", "block": %s}
                """.formatted(reason, block), me.accessToken());
    }

    private org.springframework.test.web.servlet.ResultActions sendText(TestUser sender, Long roomId) throws Exception {
        return call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "안녕하세요"}
                """, sender.accessToken());
    }
}
