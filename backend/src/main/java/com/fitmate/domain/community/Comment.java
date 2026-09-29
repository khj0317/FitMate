package com.fitmate.domain.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** 댓글. parentId가 있으면 답글이고, 답글에는 다시 답글을 달 수 없다 (한 단계) */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment {

    public static final int MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    private Long authorId;

    private Long parentId;

    @Column(length = MAX_LENGTH)
    private String content;

    @Column(nullable = false)
    private boolean deleted;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Comment(Long postId, Long authorId, Long parentId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.content = content;
    }

    public boolean isReply() {
        return parentId != null;
    }

    public boolean isAuthor(Long userId) {
        return authorId != null && authorId.equals(userId);
    }

    /** 답글이 달린 댓글은 자리를 남겨 대화 흐름이 끊기지 않게 하고 내용만 지운다 */
    public void softDelete() {
        this.deleted = true;
        this.content = null;
    }
}
