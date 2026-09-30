package com.fitmate.domain.community;

import com.fitmate.domain.notification.Notification;
import com.fitmate.domain.notification.NotificationService;
import com.fitmate.domain.safety.SafetyService;
import com.fitmate.domain.sport.SportRepository;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.image.ImagePurpose;
import com.fitmate.global.image.ImageUploader;
import com.fitmate.global.image.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    static final int PAGE_SIZE = 20;
    private static final int PREVIEW_LENGTH = 40;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommunityQuery communityQuery;
    private final UserRepository userRepository;
    private final SportRepository sportRepository;
    private final SafetyService safetyService;
    private final NotificationService notificationService;
    private final ImageUploader imageUploader;
    private final Clock clock;

    // ---------- 글 ----------

    /** 글을 쓸 때의 활동 지역을 함께 저장해서 "우리 동네 글"을 반경으로 찾는다. 사진은 최대 4장 */
    @Transactional
    public CommunityDtos.PostItem create(Long userId, CommunityDtos.PostInput input, List<MultipartFile> files) {
        List<MultipartFile> images = files == null ? List.of() : files.stream().filter(file -> !file.isEmpty()).toList();
        if (images.size() > Post.MAX_IMAGES) {
            throw new BusinessException(ErrorCode.TOO_MANY_IMAGES);
        }
        validateSport(input.sportId());
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Post post = new Post(userId, input.category(), input.sportId(), input.content().strip(),
                author.getActivityLocation(), author.getActivityAreaName());
        for (MultipartFile file : images) {
            StoredImage image = imageUploader.upload(file, ImagePurpose.POST); // 저장이 롤백되면 올린 파일도 지워진다
            post.addImage(image.url(), image.width(), image.height());
        }
        postRepository.save(post);
        return detail(userId, post.getId());
    }

    public CommunityDtos.FeedPage feed(Long userId, CommunityDtos.Scope scope, Post.Category category, Short sportId,
                                       Long authorId, Long cursor) {
        boolean nearby = scope == CommunityDtos.Scope.NEARBY && authorId == null;
        double radiusMeters = 0;
        if (nearby) {
            User me = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (me.getActivityLocation() == null) {
                throw new BusinessException(ErrorCode.LOCATION_REQUIRED);
            }
            radiusMeters = me.getSearchRadiusKm() * 1000.0;
        }
        List<CommunityDtos.PostItem> found = communityQuery.findFeed(userId, category, sportId, authorId, nearby,
                radiusMeters, cursor, PAGE_SIZE + 1);
        boolean hasNext = found.size() > PAGE_SIZE;
        List<CommunityDtos.PostItem> page = hasNext ? found.subList(0, PAGE_SIZE) : found;
        return new CommunityDtos.FeedPage(page, hasNext ? page.get(page.size() - 1).id() : null);
    }

    /** 차단 관계인 사람의 글은 없는 글처럼 보인다 */
    public CommunityDtos.PostItem detail(Long userId, Long postId) {
        return communityQuery.findOne(userId, postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    @Transactional
    public CommunityDtos.PostItem update(Long userId, Long postId, CommunityDtos.PostInput input) {
        Post post = getOwnPost(userId, postId);
        validateSport(input.sportId());
        post.edit(input.category(), input.sportId(), input.content().strip(), clock.instant());
        postRepository.flush();
        return detail(userId, postId);
    }

    /** 글을 지우면 댓글·좋아요는 DB에서 함께 지워지고, 사진 파일은 커밋 후 지운다 */
    @Transactional
    public void delete(Long userId, Long postId) {
        Post post = getOwnPost(userId, postId);
        post.getImages().forEach(image -> imageUploader.deleteAfterCommit(image.getUrl()));
        postRepository.delete(post);
    }

    // ---------- 좋아요 ----------

    /**
     * 좋아요 행을 넣었을 때(1행)만 수를 올린다. 같은 사람이 동시에 여러 번 눌러도 PK 때문에 한 번만 들어가고,
     * 여러 사람이 동시에 눌러도 수를 DB에서 직접 더하므로 빠지지 않는다.
     */
    @Transactional
    public CommunityDtos.LikeResult like(Long userId, Long postId) {
        detail(userId, postId); // 없거나 차단 관계면 404
        if (postRepository.insertLike(postId, userId) == 1) {
            postRepository.addLikeCount(postId, 1);
        }
        return new CommunityDtos.LikeResult(true, postRepository.likeCount(postId));
    }

    @Transactional
    public CommunityDtos.LikeResult unlike(Long userId, Long postId) {
        detail(userId, postId);
        if (postRepository.deleteLike(postId, userId) == 1) {
            postRepository.addLikeCount(postId, -1);
        }
        return new CommunityDtos.LikeResult(false, postRepository.likeCount(postId));
    }

    // ---------- 댓글 ----------

    /** 댓글과 그 아래 답글을 묶어서 돌려준다. 부모가 보이지 않는(차단) 답글은 함께 숨긴다 */
    public List<CommunityDtos.CommentItem> comments(Long userId, Long postId) {
        detail(userId, postId);
        List<CommunityDtos.CommentItem> flat = communityQuery.findComments(userId, postId);
        Map<Long, List<CommunityDtos.CommentItem>> replies = new LinkedHashMap<>();
        flat.stream().filter(comment -> comment.parentId() != null)
                .forEach(reply -> replies.computeIfAbsent(reply.parentId(), id -> new ArrayList<>()).add(reply));
        return flat.stream()
                .filter(comment -> comment.parentId() == null)
                .map(comment -> new CommunityDtos.CommentItem(comment.id(), null, comment.author(), comment.content(),
                        comment.deleted(), comment.mine(), comment.createdAt(),
                        replies.getOrDefault(comment.id(), List.of())))
                // 답글이 모두 가려진 삭제 댓글은 빈 자리만 남으므로 보여주지 않는다
                .filter(comment -> !comment.deleted() || !comment.replies().isEmpty())
                .toList();
    }

    @Transactional
    public CommunityDtos.CommentItem addComment(Long userId, Long postId, CommunityDtos.CommentInput input) {
        CommunityDtos.PostItem post = detail(userId, postId);
        Comment parent = null;
        if (input.parentId() != null) {
            parent = commentRepository.findById(input.parentId())
                    .filter(found -> found.getPostId().equals(postId) && !found.isDeleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            if (parent.isReply()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "답글에는 답글을 달 수 없어요.");
            }
        }
        String content = input.content().strip();
        Comment saved = commentRepository.save(new Comment(postId, userId, input.parentId(), content));
        postRepository.addCommentCount(postId, 1);

        User writer = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String link = "/community/" + postId;
        Long postAuthorId = post.author().userId();
        if (parent != null && parent.getAuthorId() != null && !parent.isAuthor(userId)) {
            notificationService.notify(parent.getAuthorId(), Notification.Type.COMMENT_REPLIED,
                    "%s님이 답글을 남겼어요".formatted(writer.getNickname()), preview(content), link);
        }
        boolean postAuthorAlreadyNotified = parent != null && parent.isAuthor(postAuthorId);
        if (!postAuthorId.equals(userId) && !postAuthorAlreadyNotified) {
            notificationService.notify(postAuthorId, Notification.Type.POST_COMMENTED,
                    "%s님이 내 글에 댓글을 남겼어요".formatted(writer.getNickname()), preview(content), link);
        }
        CommunityDtos.Author me = new CommunityDtos.Author(writer.getId(), writer.getNickname(),
                writer.getProfileImageUrl(), writer.getMannerScore());
        return new CommunityDtos.CommentItem(saved.getId(), saved.getParentId(), me, content, false, true,
                clock.instant(), List.of());
    }

    /**
     * 답글이 달린 댓글은 "삭제된 댓글"로 자리만 남기고, 아니면 완전히 지운다.
     * 자리만 남은 댓글의 마지막 답글이 지워지면 그 자리도 함께 지운다.
     */
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        if (!comment.isReply() && commentRepository.countByParentIdAndDeletedFalse(comment.getId()) > 0) {
            comment.softDelete();
        } else {
            commentRepository.delete(comment);
            if (comment.isReply()) {
                commentRepository.flush();
                commentRepository.findById(comment.getParentId())
                        .filter(Comment::isDeleted)
                        .filter(parent -> commentRepository.countByParentIdAndDeletedFalse(parent.getId()) == 0)
                        .ifPresent(commentRepository::delete);
            }
        }
        postRepository.addCommentCount(comment.getPostId(), -1);
    }

    private Post getOwnPost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isAuthor(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_AUTHOR);
        }
        return post;
    }

    private void validateSport(Short sportId) {
        if (sportId != null && !sportRepository.existsById(sportId)) {
            throw new BusinessException(ErrorCode.SPORT_NOT_FOUND);
        }
    }

    private static String preview(String content) {
        String oneLine = content.replaceAll("\\s+", " ");
        return oneLine.length() <= PREVIEW_LENGTH ? oneLine : oneLine.substring(0, PREVIEW_LENGTH) + "…";
    }
}
