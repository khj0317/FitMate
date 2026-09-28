package com.fitmate.domain.matchrequest;

import com.fitmate.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchRequestApiTest extends IntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    @DisplayName("요청 → 받은 목록 확인 → 수락하면 채팅방이 생기고 보낸 쪽에서도 채팅방 ID를 볼 수 있다")
    void requestAndAccept() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        Long requestId = createdMatchRequestId(requester, receiver);

        call(HttpMethod.GET, "/api/match-requests/received", null, receiver.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(requestId))
                .andExpect(jsonPath("$[0].counterpart.nickname").value(requester.nickname()))
                .andExpect(jsonPath("$[0].sportName").value("헬스"))
                .andExpect(jsonPath("$[0].message").value("같이 운동해요"));

        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, receiver.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatRoomId").isNumber());

        call(HttpMethod.GET, "/api/match-requests/sent?status=ACCEPTED", null, requester.accessToken())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].counterpart.nickname").value(receiver.nickname()))
                .andExpect(jsonPath("$[0].chatRoomId").isNumber());

        // 이미 매칭된 상대에게는 다시 요청할 수 없다 (방향이 반대여도)
        requestMatch(receiver, requester)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MATCHED"));
    }

    @Test
    @DisplayName("잘못된 요청: 자기 자신, 상대가 안 하는 종목, 중복 대기, 상대가 먼저 보낸 경우")
    void invalidRequests() throws Exception {
        TestUser me = signupWithGym();
        TestUser gymUser = signupWithGym();
        TestUser noSportUser = signupAndLogin();

        requestMatch(me, me).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CANNOT_REQUEST_SELF"));
        requestMatch(me, noSportUser).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECEIVER_DOES_NOT_PLAY_SPORT"));

        requestMatch(me, gymUser).andExpect(status().isCreated());
        requestMatch(me, gymUser).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MATCH_REQUEST"));
        requestMatch(gymUser, me).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVERSE_MATCH_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("거절되거나 취소된 뒤에는 다시 요청할 수 있다")
    void requestAgainAfterRejectOrCancel() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();

        Long first = createdMatchRequestId(requester, receiver);
        call(HttpMethod.POST, "/api/match-requests/" + first + "/reject", null, receiver.accessToken())
                .andExpect(status().isNoContent());

        Long second = createdMatchRequestId(requester, receiver);
        call(HttpMethod.POST, "/api/match-requests/" + second + "/cancel", null, requester.accessToken())
                .andExpect(status().isNoContent());

        requestMatch(requester, receiver).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("권한 없는 처리는 404: 보낸 사람은 수락 못 하고, 받은 사람은 취소 못 하고, 제3자는 아무것도 못 한다")
    void onlyOwnerCanHandle() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        TestUser stranger = signupWithGym();
        Long requestId = createdMatchRequestId(requester, receiver);

        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, requester.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/cancel", null, receiver.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/reject", null, stranger.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 처리된 요청을 다시 처리하면 409")
    void handleTwice() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        Long requestId = createdMatchRequestId(requester, receiver);

        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/reject", null, receiver.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, receiver.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_MATCH_REQUEST_STATUS"));
    }

    @Test
    @DisplayName("수락과 취소가 동시에 들어오면 하나만 성공한다 (비관적 락)")
    void concurrentAcceptAndCancel() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        Long requestId = createdMatchRequestId(requester, receiver);

        List<Integer> statuses = runConcurrently(2, i -> (i == 0
                ? call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, receiver.accessToken())
                : call(HttpMethod.POST, "/api/match-requests/" + requestId + "/cancel", null, requester.accessToken()))
                .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200 || s == 204).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(1);
    }

    @Test
    @DisplayName("같은 요청을 동시에 여러 번 수락해도 채팅방은 하나만 만들어진다")
    void concurrentAcceptCreatesSingleRoom() throws Exception {
        TestUser requester = signupWithGym();
        TestUser receiver = signupWithGym();
        Long requestId = createdMatchRequestId(requester, receiver);

        List<Integer> statuses = runConcurrently(5, i ->
                call(HttpMethod.POST, "/api/match-requests/" + requestId + "/accept", null, receiver.accessToken())
                        .andReturn().getResponse().getStatus());

        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 409).hasSize(4);

        Long rooms = jdbcClient.sql("SELECT COUNT(*) FROM chat_rooms WHERE direct_key = ?")
                .param(Math.min(requester.id(), receiver.id()) + ":" + Math.max(requester.id(), receiver.id()))
                .query(Long.class)
                .single();
        assertThat(rooms).isEqualTo(1);
    }
}
