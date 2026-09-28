package com.fitmate.domain.user;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileImageApiTest extends IntegrationTest {

    @Test
    @DisplayName("프로필 사진은 가운데를 정사각형으로 잘라 512px로 저장되고, 다른 사람 프로필에도 보인다")
    void uploadProfileImage() throws Exception {
        TestUser user = signupAndLogin();
        TestUser viewer = signupAndLogin();

        String body = upload(user, TestImages.png(1000, 600, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl", startsWith("/files/profile/")))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.profileImageUrl");

        BufferedImage stored = TestImages.read(mockMvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(stored.getHeight()).isEqualTo(512);

        call(HttpMethod.GET, "/api/users/" + user.id(), null, viewer.accessToken())
                .andExpect(jsonPath("$.profileImageUrl").value(url));
    }

    @Test
    @DisplayName("사진을 바꾸면 예전 파일은 지워진다")
    void replaceDeletesPreviousFile() throws Exception {
        TestUser user = signupAndLogin();
        String first = JsonPath.read(upload(user, TestImages.jpeg(300, 300)).andReturn().getResponse().getContentAsString(),
                "$.profileImageUrl");

        String second = JsonPath.read(upload(user, TestImages.jpeg(400, 400)).andReturn().getResponse().getContentAsString(),
                "$.profileImageUrl");

        assertThat(second).isNotEqualTo(first);
        mockMvc.perform(get(first)).andExpect(status().isNotFound());
        mockMvc.perform(get(second)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("사진을 삭제하면 기본 아바타로 돌아가고 파일도 지워진다")
    void deleteProfileImage() throws Exception {
        TestUser user = signupAndLogin();
        String url = JsonPath.read(upload(user, TestImages.jpeg(300, 300)).andReturn().getResponse().getContentAsString(),
                "$.profileImageUrl");

        call(HttpMethod.DELETE, "/api/users/me/profile-image", null, user.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl").doesNotExist());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("이미지가 아니면 거절하고 기존 사진은 그대로 둔다")
    void rejectNonImage() throws Exception {
        TestUser user = signupAndLogin();
        String url = JsonPath.read(upload(user, TestImages.jpeg(300, 300)).andReturn().getResponse().getContentAsString(),
                "$.profileImageUrl");

        upload(user, "not an image".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"));

        call(HttpMethod.GET, "/api/users/me", null, user.accessToken())
                .andExpect(jsonPath("$.profileImageUrl").value(url));
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    private ResultActions upload(TestUser user, byte[] data) throws Exception {
        return mockMvc.perform(multipart("/api/users/me/profile-image")
                .file(new MockMultipartFile("file", "me.png", "image/png", data))
                .header("Authorization", "Bearer " + user.accessToken()));
    }
}
