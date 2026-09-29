package com.fitmate.domain.notification;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationApiTest extends IntegrationTest {

    @Test
    @DisplayName("매칭 요청을 받으면 알림이 생기고, 수락하면 보낸 사람에게 채팅방 링크가 담긴 알림이 간다")
    void matchRequestNotifications() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();

        Long roomId = matchedRoomId(a, b);

        call(HttpMethod.GET, "/api/notifications", null, b.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].type").value("MATCH_REQUEST_RECEIVED"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        call(HttpMethod.GET, "/api/notifications", null, a.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("MATCH_REQUEST_ACCEPTED"))
                .andExpect(jsonPath("$.items[0].link").value("/chats/" + roomId));
    }

    @Test
    @DisplayName("하나씩 읽음 처리하거나 모두 읽음 처리할 수 있고, 남의 알림은 건드릴 수 없다")
    void markRead() throws Exception {
        TestUser receiver = signupWithGym();
        TestUser first = signupWithGym();
        TestUser second = signupWithGym();
        requestMatch(first, receiver);
        requestMatch(second, receiver);

        String body = call(HttpMethod.GET, "/api/notifications", null, receiver.accessToken())
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andReturn().getResponse().getContentAsString();
        long latestId = ((Number) JsonPath.read(body, "$.items[0].id")).longValue();

        call(HttpMethod.POST, "/api/notifications/" + latestId + "/read", null, first.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/notifications/" + latestId + "/read", null, receiver.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/notifications", null, receiver.accessToken())
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.items[0].read").value(true))
                .andExpect(jsonPath("$.items[1].read").value(false));

        call(HttpMethod.POST, "/api/notifications/read-all", null, receiver.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/notifications", null, receiver.accessToken())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
