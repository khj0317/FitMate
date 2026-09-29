package com.fitmate.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 좋아요·댓글 수는 "읽고 +1 해서 저장"하면 동시에 누를 때 서로 덮어써서 수가 빠진다.
 * 그래서 좋아요 행을 넣거나 지운 결과(0 또는 1행)에 따라 DB에서 한 문장으로 더하고 뺀다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    boolean existsByAuthorId(Long authorId);

    /** 이미 눌렀으면 0 (PK 충돌을 예외 대신 무시) */
    @Modifying
    @Query(value = "INSERT INTO post_likes (post_id, user_id) VALUES (:postId, :userId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM post_likes WHERE post_id = :postId AND user_id = :userId", nativeQuery = true)
    int deleteLike(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying
    @Query(value = "UPDATE posts SET like_count = GREATEST(0, like_count + :delta) WHERE id = :postId",
            nativeQuery = true)
    int addLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Modifying
    @Query(value = "UPDATE posts SET comment_count = GREATEST(0, comment_count + :delta) WHERE id = :postId",
            nativeQuery = true)
    int addCommentCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Query(value = "SELECT like_count FROM posts WHERE id = :postId", nativeQuery = true)
    int likeCount(@Param("postId") Long postId);

    @Query(value = """
            SELECT i.url FROM post_images i JOIN posts p ON p.id = i.post_id WHERE p.author_id = :authorId
            """, nativeQuery = true)
    List<String> findImageUrlsByAuthorId(@Param("authorId") Long authorId);
}
