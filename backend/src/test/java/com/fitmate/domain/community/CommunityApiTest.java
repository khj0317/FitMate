package com.fitmate.domain.community;

import com.fitmate.support.IntegrationTest;
import com.fitmate.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("사진과 함께 글을 쓰면 사진이 줄어들어 저장되고, 글을 지우면 사진 파일도 지워진다")
    void createWithImagesAndDelete() throws Exception {
        TestUser author = signupAndLogin();

        String body = writePost(author, "오늘 운동 인증 💪", TestImages.png(2400, 1200, false), TestImages.jpeg(300, 300))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("CERTIFY"))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].url", startsWith("/files/post/")))
                .andExpect(jsonPath("$.images[0].width").value(1600))
                .andExpect(jsonPath("$.areaName").value("테스트 지역"))
                .andExpect(jsonPath("$.mine").value(true))
                .andReturn().getResponse().getContentAsString();
        long postId = ((Number) JsonPath.read(body, "$.id")).longValue();
        String imageUrl = JsonPath.read(body, "$.images[0].url");
        mockMvc.perform(get(imageUrl)).andExpect(status().isOk());

        call(HttpMethod.GET, "/api/posts?authorId=" + author.id(), null, author.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(postId));

        call(HttpMethod.DELETE, "/api/posts/" + postId, null, author.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/posts/" + postId, null, author.accessToken())
                .andExpect(status().isNotFound());
        mockMvc.perform(get(imageUrl)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("사진은 4장까지만 올릴 수 있다")
    void rejectTooManyImages() throws Exception {
        TestUser author = signupAndLogin();
        byte[] image = TestImages.jpeg(50, 50);
        writePost(author, "사진 많이", image, image, image, image, image)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_IMAGES"));
    }

    @Test
    @DisplayName("우리 동네 글에는 내 활동 반경 안에서 쓴 글만 보인다")
    void nearbyFeed() throws Exception {
        TestUser author = signupAndLogin();
        TestUser neighbor = signupAndLogin();
        TestUser farAway = signupAndLogin();
        double[] location = location(author.id());
        moveTo(neighbor, location[0] + 0.01, location[1]);
        moveTo(farAway, location[0] + 1.0, location[1]);
        long postId = createdPostId(author, "동네 러닝 코스 추천해요");

        call(HttpMethod.GET, "/api/posts?scope=NEARBY", null, neighbor.accessToken())
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) postId)));
        call(HttpMethod.GET, "/api/posts?scope=NEARBY", null, farAway.accessToken())
                .andExpect(jsonPath("$.items[*].id").value(not(hasItem((int) postId))));
        call(HttpMethod.GET, "/api/posts?scope=ALL&category=CERTIFY", null, farAway.accessToken())
                .andExpect(jsonPath("$.items[*].id").value(hasItem((int) postId)));
    }

    @Test
    @DisplayName("좋아요는 같은 사람이 여러 번 눌러도 한 번, 여러 사람이 동시에 눌러도 수가 빠지지 않는다")
    void likes() throws Exception {
        TestUser author = signupAndLogin();
        long postId = createdPostId(author, "좋아요 테스트");
        List<TestUser> fans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            fans.add(signupAndLogin());
        }

        List<Integer> statuses = runConcurrently(10, index -> call(HttpMethod.PUT, "/api/posts/" + postId + "/like",
                null, fans.get(index).accessToken()).andReturn().getResponse().getStatus());
        assertThat(statuses).containsOnly(200);

        TestUser fan = fans.get(0);
        call(HttpMethod.PUT, "/api/posts/" + postId + "/like", null, fan.accessToken())
                .andExpect(jsonPath("$.likeCount").value(10));
        runConcurrently(5, index -> call(HttpMethod.PUT, "/api/posts/" + postId + "/like", null,
                fan.accessToken()).andReturn().getResponse().getStatus());
        call(HttpMethod.GET, "/api/posts/" + postId, null, fan.accessToken())
                .andExpect(jsonPath("$.likeCount").value(10))
                .andExpect(jsonPath("$.liked").value(true));

        call(HttpMethod.DELETE, "/api/posts/" + postId + "/like", null, fan.accessToken())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.likeCount").value(9));
        call(HttpMethod.DELETE, "/api/posts/" + postId + "/like", null, fan.accessToken())
                .andExpect(jsonPath("$.likeCount").value(9));
    }

    @Test
    @DisplayName("댓글·답글을 달면 글쓴이와 댓글 작성자에게 알림이 가고, 답글에는 답글을 달 수 없다")
    void commentsAndReplies() throws Exception {
        TestUser author = signupAndLogin();
        TestUser commenter = signupAndLogin();
        TestUser other = signupAndLogin();
        long postId = createdPostId(author, "질문 있어요");

        long commentId = commentId(comment(commenter, postId, "저도 궁금해요", null).andExpect(status().isCreated()));
        call(HttpMethod.GET, "/api/notifications", null, author.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("POST_COMMENTED"))
                .andExpect(jsonPath("$.items[0].link").value("/community/" + postId));

        // 글쓴이가 답글: 댓글 작성자에게만 알림
        long replyId = commentId(comment(author, postId, "제가 알아볼게요", commentId).andExpect(status().isCreated()));
        call(HttpMethod.GET, "/api/notifications", null, commenter.accessToken())
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_REPLIED"));
        call(HttpMethod.GET, "/api/notifications", null, author.accessToken())
                .andExpect(jsonPath("$.unreadCount").value(1));

        // 다른 사람이 답글: 댓글 작성자와 글쓴이 모두에게 알림
        comment(other, postId, "저도요!", commentId).andExpect(status().isCreated());
        call(HttpMethod.GET, "/api/notifications", null, author.accessToken())
                .andExpect(jsonPath("$.unreadCount").value(2));

        comment(other, postId, "답글의 답글", replyId).andExpect(status().isBadRequest());

        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, other.accessToken())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].replies.length()").value(2))
                .andExpect(jsonPath("$[0].replies[0].id").value(replyId));
        call(HttpMethod.GET, "/api/posts/" + postId, null, other.accessToken())
                .andExpect(jsonPath("$.commentCount").value(3));
    }

    @Test
    @DisplayName("답글이 달린 댓글을 지우면 자리만 남고, 마지막 답글까지 지우면 자리도 사라진다")
    void deleteCommentWithReplies() throws Exception {
        TestUser author = signupAndLogin();
        TestUser commenter = signupAndLogin();
        long postId = createdPostId(author, "댓글 삭제 테스트");
        long commentId = commentId(comment(commenter, postId, "첫 댓글", null));
        long replyId = commentId(comment(author, postId, "답글", commentId));

        call(HttpMethod.DELETE, "/api/comments/" + commentId, null, author.accessToken())
                .andExpect(status().isForbidden());
        call(HttpMethod.DELETE, "/api/comments/" + commentId, null, commenter.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, author.accessToken())
                .andExpect(jsonPath("$[0].deleted").value(true))
                .andExpect(jsonPath("$[0].content").doesNotExist())
                .andExpect(jsonPath("$[0].replies.length()").value(1));

        call(HttpMethod.DELETE, "/api/comments/" + replyId, null, author.accessToken())
                .andExpect(status().isNoContent());
        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, author.accessToken())
                .andExpect(jsonPath("$.length()").value(0));
        call(HttpMethod.GET, "/api/posts/" + postId, null, author.accessToken())
                .andExpect(jsonPath("$.commentCount").value(0));
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id = ?", Integer.class, postId);
        assertThat(rows).isZero();
    }

    @Test
    @DisplayName("글은 작성자만 고치고 지울 수 있다")
    void onlyAuthorCanEdit() throws Exception {
        TestUser author = signupAndLogin();
        TestUser stranger = signupAndLogin();
        long postId = createdPostId(author, "원래 내용");
        String edit = """
                {"category": "QUESTION", "sportId": %d, "content": "고친 내용"}
                """.formatted(GYM);

        call(HttpMethod.PUT, "/api/posts/" + postId, edit, stranger.accessToken())
                .andExpect(status().isForbidden());
        call(HttpMethod.DELETE, "/api/posts/" + postId, null, stranger.accessToken())
                .andExpect(status().isForbidden());
        call(HttpMethod.PUT, "/api/posts/" + postId, edit, author.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("고친 내용"))
                .andExpect(jsonPath("$.category").value("QUESTION"))
                .andExpect(jsonPath("$.sportName").value("헬스"))
                .andExpect(jsonPath("$.edited").value(true));
    }

    @Test
    @DisplayName("차단 관계인 사람의 글과 댓글은 보이지 않는다")
    void blockedUsersAreHidden() throws Exception {
        TestUser author = signupAndLogin();
        TestUser blocked = signupAndLogin();
        TestUser viewer = signupAndLogin();
        long postId = createdPostId(author, "차단 테스트");
        comment(blocked, postId, "차단될 사람의 댓글", null).andExpect(status().isCreated());

        call(HttpMethod.PUT, "/api/users/" + blocked.id() + "/block", null, author.accessToken())
                .andExpect(status().isNoContent());

        call(HttpMethod.GET, "/api/posts/" + postId, null, blocked.accessToken())
                .andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/posts?scope=ALL&authorId=" + author.id(), null, blocked.accessToken())
                .andExpect(jsonPath("$.items.length()").value(0));
        comment(blocked, postId, "또 댓글", null).andExpect(status().isNotFound());
        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, author.accessToken())
                .andExpect(jsonPath("$.length()").value(0));
        call(HttpMethod.GET, "/api/posts/" + postId + "/comments", null, viewer.accessToken())
                .andExpect(jsonPath("$.length()").value(1));
    }

    private ResultActions writePost(TestUser author, String content, byte[]... images) throws Exception {
        MockMultipartHttpServletRequestBuilder builder = multipart("/api/posts");
        builder.file(new MockMultipartFile("post", "", "application/json", """
                {"category": "CERTIFY", "content": "%s"}
                """.formatted(content).getBytes(StandardCharsets.UTF_8)));
        for (int i = 0; i < images.length; i++) {
            builder.file(new MockMultipartFile("images", "photo" + i + ".jpg", "image/jpeg", images[i]));
        }
        return mockMvc.perform(builder.header("Authorization", "Bearer " + author.accessToken()));
    }

    private long createdPostId(TestUser author, String content) throws Exception {
        String body = writePost(author, content).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions comment(TestUser user, long postId, String content, Long parentId) throws Exception {
        return call(HttpMethod.POST, "/api/posts/" + postId + "/comments", """
                {"content": "%s", "parentId": %s}
                """.formatted(content, parentId), user.accessToken());
    }

    private static long commentId(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private double[] location(Long userId) {
        return jdbcTemplate.queryForObject("""
                        SELECT ST_Y(activity_location::geometry) AS lat, ST_X(activity_location::geometry) AS lng
                        FROM users WHERE id = ?""",
                (rs, rowNum) -> new double[]{rs.getDouble("lat"), rs.getDouble("lng")}, userId);
    }

    private void moveTo(TestUser user, double latitude, double longitude) throws Exception {
        call(HttpMethod.PUT, "/api/users/me/location", String.format(Locale.ROOT, """
                {"latitude": %.6f, "longitude": %.6f, "areaName": "이웃 동네"}
                """, latitude, longitude), user.accessToken()).andExpect(status().isOk());
    }
}
