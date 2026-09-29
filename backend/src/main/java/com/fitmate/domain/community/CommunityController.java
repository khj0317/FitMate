package com.fitmate.domain.community;

import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "커뮤니티")
@RestController
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    @Operation(summary = "글 쓰기", description = "multipart: post(JSON) + images(사진 최대 4장, 선택)")
    @PostMapping(value = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityDtos.PostItem create(@Parameter(hidden = true) @LoginUserId Long me,
                                         @Valid @RequestPart("post") CommunityDtos.PostInput post,
                                         @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        return communityService.create(me, post, images);
    }

    @Operation(summary = "피드", description = "scope=NEARBY(내 활동 반경, 기본) / ALL, authorId를 주면 그 사람이 쓴 글. 커서 페이지네이션")
    @GetMapping("/api/posts")
    public CommunityDtos.FeedPage feed(@Parameter(hidden = true) @LoginUserId Long me,
                                       @RequestParam(defaultValue = "NEARBY") CommunityDtos.Scope scope,
                                       @RequestParam(required = false) Post.Category category,
                                       @RequestParam(required = false) Short sportId,
                                       @RequestParam(required = false) Long authorId,
                                       @RequestParam(required = false) Long cursor) {
        return communityService.feed(me, scope, category, sportId, authorId, cursor);
    }

    @Operation(summary = "글 상세")
    @GetMapping("/api/posts/{postId}")
    public CommunityDtos.PostItem detail(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        return communityService.detail(me, postId);
    }

    @Operation(summary = "글 고치기", description = "작성자만. 사진은 바꿀 수 없음")
    @PutMapping("/api/posts/{postId}")
    public CommunityDtos.PostItem update(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId,
                                         @Valid @RequestBody CommunityDtos.PostInput request) {
        return communityService.update(me, postId, request);
    }

    @Operation(summary = "글 지우기", description = "작성자만")
    @DeleteMapping("/api/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        communityService.delete(me, postId);
    }

    @Operation(summary = "좋아요", description = "여러 번 눌러도 한 번만 반영")
    @PutMapping("/api/posts/{postId}/like")
    public CommunityDtos.LikeResult like(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        return communityService.like(me, postId);
    }

    @Operation(summary = "좋아요 취소")
    @DeleteMapping("/api/posts/{postId}/like")
    public CommunityDtos.LikeResult unlike(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long postId) {
        return communityService.unlike(me, postId);
    }

    @Operation(summary = "댓글 목록", description = "댓글마다 답글(replies)이 묶여서 옵니다")
    @GetMapping("/api/posts/{postId}/comments")
    public List<CommunityDtos.CommentItem> comments(@Parameter(hidden = true) @LoginUserId Long me,
                                                    @PathVariable Long postId) {
        return communityService.comments(me, postId);
    }

    @Operation(summary = "댓글 · 답글 쓰기", description = "parentId를 주면 답글 (한 단계까지)")
    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommunityDtos.CommentItem addComment(@Parameter(hidden = true) @LoginUserId Long me,
                                                @PathVariable Long postId,
                                                @Valid @RequestBody CommunityDtos.CommentInput request) {
        return communityService.addComment(me, postId, request);
    }

    @Operation(summary = "댓글 지우기", description = "답글이 달린 댓글은 \"삭제된 댓글\"로 남습니다")
    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@Parameter(hidden = true) @LoginUserId Long me, @PathVariable Long commentId) {
        communityService.deleteComment(me, commentId);
    }
}
