package com.fitmate.domain.community;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 동네 운동 게시판 글. 좋아요·댓글 수는 동시에 몰려도 빠지지 않게 DB에서 직접 더하고 뺀다 (PostRepository) */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    public static final int MAX_LENGTH = 3000;
    public static final int MAX_IMAGES = 4;

    public enum Category {
        CERTIFY, QUESTION, REVIEW, FREE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    private Short sportId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(length = 100)
    private String areaName;

    @Column(nullable = false)
    private int likeCount;

    @Column(nullable = false)
    private int commentCount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<PostImage> images = new ArrayList<>();

    public Post(Long authorId, Category category, Short sportId, String content, Point location, String areaName) {
        this.authorId = authorId;
        this.category = category;
        this.sportId = sportId;
        this.content = content;
        this.location = location;
        this.areaName = areaName;
    }

    public void addImage(String url, int width, int height) {
        images.add(new PostImage(this, url, width, height, (short) images.size()));
    }

    public boolean isAuthor(Long userId) {
        return authorId.equals(userId);
    }

    /** 사진은 바꾸지 않고 글 내용과 분류만 고친다 */
    public void edit(Category category, Short sportId, String content, Instant now) {
        this.category = category;
        this.sportId = sportId;
        this.content = content;
        this.updatedAt = now;
    }
}
