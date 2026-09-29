package com.fitmate.domain.safety;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** 신고. 운영자가 검토하며, 신고한 사람·당한 사람이 탈퇴해도 기록은 남는다(ID만 null). */
@Entity
@Table(name = "user_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reporterId;

    private Long reportedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportReason reason;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public UserReport(Long reporterId, Long reportedId, ReportReason reason, String detail) {
        this.reporterId = reporterId;
        this.reportedId = reportedId;
        this.reason = reason;
        this.detail = detail;
    }

    public enum ReportReason {
        SPAM,
        ABUSE,
        SEXUAL,
        FAKE_PROFILE,
        NO_SHOW,
        OTHER
    }
}
