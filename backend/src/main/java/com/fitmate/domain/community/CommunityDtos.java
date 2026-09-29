package com.fitmate.domain.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CommunityDtos {

    private CommunityDtos() {
    }

    /** 글 쓰기·고치기 (사진은 쓸 때만 multipart로 함께 올린다) */
    public record PostInput(
            @NotNull(message = "글 종류를 선택해 주세요.") Post.Category category,
            Short sportId,
            @NotBlank(message = "내용을 입력해 주세요.")
            @Size(max = Post.MAX_LENGTH, message = "글은 3000자 이하여야 합니다.") String content
    ) {
    }

    public record Author(Long userId, String nickname, String profileImageUrl, BigDecimal mannerScore) {
    }

    public record Image(String url, int width, int height) {
    }

    public record PostItem(
            Long id,
            Post.Category category,
            Short sportId,
            String sportCode,
            String sportName,
            String content,
            List<Image> images,
            Author author,
            /* 동 이름까지만 (좌표·거리는 보여주지 않는다) */
            String areaName,
            int likeCount,
            int commentCount,
            boolean liked,
            boolean mine,
            Instant createdAt,
            boolean edited
    ) {
        PostItem withImages(List<Image> images) {
            return new PostItem(id, category, sportId, sportCode, sportName, content, images, author, areaName,
                    likeCount, commentCount, liked, mine, createdAt, edited);
        }
    }

    public record FeedPage(List<PostItem> items, Long nextCursor) {
    }

    public enum Scope {
        /** 내 활동 지역 반경 안의 글 */
        NEARBY,
        ALL
    }

    public record CommentInput(
            @NotBlank(message = "댓글을 입력해 주세요.")
            @Size(max = Comment.MAX_LENGTH, message = "댓글은 1000자 이하여야 합니다.") String content,
            Long parentId
    ) {
    }

    /** author가 null이면 탈퇴한 회원, deleted면 내용 없이 "삭제된 댓글"로 보여준다 */
    public record CommentItem(
            Long id,
            Long parentId,
            Author author,
            String content,
            boolean deleted,
            boolean mine,
            Instant createdAt,
            List<CommentItem> replies
    ) {
    }

    public record LikeResult(boolean liked, int likeCount) {
    }
}
