package com.fitmate.domain.chat;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatImageApiTest extends IntegrationTest {

    @Test
    @DisplayName("사진을 보내면 줄여서 저장하고 사진 메시지가 되며, 저장된 파일을 바로 받을 수 있다")
    void sendImage() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        String body = sendImage(a, roomId, TestImages.png(2400, 1200, false), "photo.png")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("IMAGE"))
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.imageUrl", startsWith("/files/chat/")))
                .andExpect(jsonPath("$.imageWidth").value(1600))
                .andExpect(jsonPath("$.imageHeight").value(800))
                .andReturn().getResponse().getContentAsString();

        String imageUrl = JsonPath.read(body, "$.imageUrl");
        byte[] stored = mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(TestImages.read(stored).getWidth()).isEqualTo(1600);

        // 채팅방 목록 미리보기는 "사진"으로 보이고, 받는 사람에게는 안 읽은 메시지로 잡힌다
        call(HttpMethod.GET, "/api/chat-rooms", null, b.accessToken())
                .andExpect(jsonPath("$[0].lastMessage.content").value("📷 사진"))
                .andExpect(jsonPath("$[0].unreadCount").value(1));
        call(HttpMethod.GET, "/api/chat-rooms/" + roomId + "/messages", null, b.accessToken())
                .andExpect(jsonPath("$.messages[0].type").value("IMAGE"))
                .andExpect(jsonPath("$.messages[0].imageUrl").value(imageUrl));
    }

    @Test
    @DisplayName("이미지가 아닌 파일은 확장자를 바꿔도 거절한다")
    void rejectNonImage() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        Long roomId = matchedRoomId(a, b);

        sendImage(a, roomId, "<script>alert(1)</script>".getBytes(), "fake.jpg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));
    }

    @Test
    @DisplayName("참여하지 않은 채팅방에는 사진을 보낼 수 없다")
    void strangerCannotSend() throws Exception {
        TestUser a = signupWithGym();
        TestUser b = signupWithGym();
        TestUser stranger = signupAndLogin();
        Long roomId = matchedRoomId(a, b);

        sendImage(stranger, roomId, TestImages.jpeg(100, 100), "photo.jpg")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_ROOM_NOT_FOUND"));
    }

    private ResultActions sendImage(TestUser user, Long roomId, byte[] data, String filename) throws Exception {
        return mockMvc.perform(multipart("/api/chat-rooms/" + roomId + "/images")
                .file(new MockMultipartFile("file", filename, "image/jpeg", data))
                .header("Authorization", "Bearer " + user.accessToken()));
    }
}
