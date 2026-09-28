package com.fitmate.domain.chat;

import com.fitmate.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatApiTest extends IntegrationTest {

    @Test
    @DisplayName("채팅방 목록에 상대방, 마지막 메시지, 안 읽은 수가 나오고 읽음 처리하면 0이 된다")
    void roomListWithUnreadCount() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        send(a, roomId, "안녕하세요");
        send(a, roomId, "내일 저녁 헬스 어때요?");
        Long lastId = send(a, roomId, "7시 괜찮아요");

        // 받는 사람: 안 읽은 메시지 3개
        call(HttpMethod.GET, "/api/chat-rooms", null, b.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomId").value(roomId))
                .andExpect(jsonPath("$[0].type").value("DIRECT"))
                .andExpect(jsonPath("$[0].counterpart.nickname").value(a.nickname()))
                .andExpect(jsonPath("$[0].lastMessage.content").value("7시 괜찮아요"))
                .andExpect(jsonPath("$[0].unreadCount").value(3));

        // 보낸 사람: 내 메시지는 안 읽은 수에 포함되지 않는다
        call(HttpMethod.GET, "/api/chat-rooms", null, a.accessToken())
                .andExpect(jsonPath("$[0].unreadCount").value(0));

        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/read", """
                {"lastMessageId": %d}
                """.formatted(lastId), b.accessToken())
                .andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/chat-rooms", null, b.accessToken())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
    }

    @Test
    @DisplayName("메시지 목록에 상대가 읽은 위치가 들어 있다: 안 읽었으면 null, 읽으면 그 메시지 ID, 답장해도 읽음 처리")
    void otherLastReadMessageId() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        Long first = send(a, roomId, "첫 메시지");
        Long second = send(a, roomId, "두 번째");
        assertThat((Object) JsonPath.read(getMessages(a, roomId, null), "$.otherLastReadMessageId")).isNull();

        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/read", """
                {"lastMessageId": %d}
                """.formatted(first), b.accessToken());
        assertThat(((Number) JsonPath.read(getMessages(a, roomId, null), "$.otherLastReadMessageId")).longValue())
                .isEqualTo(first);

        // 답장하면 그 전 메시지는 모두 읽은 것 (카카오톡처럼 1이 사라진다)
        Long reply = send(b, roomId, "답장");
        assertThat(((Number) JsonPath.read(getMessages(a, roomId, null), "$.otherLastReadMessageId")).longValue())
                .isEqualTo(reply)
                .isGreaterThan(second);
    }

    @Test
    @DisplayName("답장하면 상대방 쪽 안 읽은 수만 늘어난다")
    void replyUpdatesUnreadForOtherSide() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        send(a, roomId, "안녕하세요");
        send(b, roomId, "반가워요"); // b가 답장하면 b의 읽음 위치도 앞으로 이동

        call(HttpMethod.GET, "/api/chat-rooms", null, b.accessToken())
                .andExpect(jsonPath("$[0].unreadCount").value(0));
        call(HttpMethod.GET, "/api/chat-rooms", null, a.accessToken())
                .andExpect(jsonPath("$[0].unreadCount").value(1));
    }

    @Test
    @DisplayName("메시지는 최신순 커서 페이지네이션으로 조회한다")
    void cursorPagination() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);
        for (int i = 1; i <= 5; i++) {
            send(i % 2 == 0 ? b : a, roomId, "메시지 " + i);
        }

        String first = getMessages(b, roomId, null);
        assertThat(contents(first)).containsExactly("메시지 5", "메시지 4");
        assertThat((String) JsonPath.read(first, "$.messages[0].senderNickname")).isEqualTo(a.nickname());

        String second = getMessages(b, roomId, nextCursor(first));
        assertThat(contents(second)).containsExactly("메시지 3", "메시지 2");

        String last = getMessages(b, roomId, nextCursor(second));
        assertThat(contents(last)).containsExactly("메시지 1");
        assertThat((Object) JsonPath.read(last, "$.nextCursor")).isNull();
    }

    @Test
    @DisplayName("이모티콘(합성 이모티콘, 피부색 포함)이 깨지지 않고 저장·조회된다")
    void emojiRoundTrip() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);
        String emoji = "💪🔥 오늘 운동 완료 😮‍💨👍🏻";

        send(a, roomId, emoji);

        String page = getMessages(b, roomId, null);
        assertThat(contents(page)).containsExactly(emoji);
    }

    @Test
    @DisplayName("참여하지 않은 채팅방은 조회도 전송도 할 수 없다 (404)")
    void nonMemberCannotAccess() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        TestUser stranger = signupAndLogin();
        Long roomId = matchedRoomId(a, b);

        call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, stranger.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
        call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "끼어들기"}
                """, stranger.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/chat-rooms", null, stranger.accessToken())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("빈 메시지는 보낼 수 없고, 다른 방의 메시지 ID로 읽음 처리할 수 없다")
    void invalidInput() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        TestUser c = signupWithGym();
        Long roomAB = matchedRoomId(a, b);
        Long roomAC = matchedRoomId(a, c);
        Long messageInAC = send(a, roomAC, "다른 방 메시지");

        call(HttpMethod.POST, "/api/chat-rooms/" + roomAB + "/messages", """
                {"content": "   "}
                """, a.accessToken())
                .andExpect(status().isBadRequest());
        call(HttpMethod.POST, "/api/chat-rooms/" + roomAB + "/read", """
                {"lastMessageId": %d}
                """.formatted(messageInAC), b.accessToken())
                .andExpect(status().isBadRequest());
    }

    private Long send(TestUser sender, Long roomId, String content) throws Exception {
        String body = call(HttpMethod.POST, "/api/chat-rooms/" + roomId + "/messages", """
                {"content": "%s"}
                """.formatted(content), sender.accessToken())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String getMessages(TestUser user, Long roomId, Long cursor) throws Exception {
        String url = "/api/chat-rooms/" + roomId + "/messages?size=2" + (cursor == null ? "" : "&cursor=" + cursor);
        return call(HttpMethod.GET, url, null, user.accessToken())
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<String> contents(String body) {
        return JsonPath.read(body, "$.messages[*].content");
    }

    private static Long nextCursor(String body) {
        return ((Number) JsonPath.read(body, "$.nextCursor")).longValue();
    }
}
