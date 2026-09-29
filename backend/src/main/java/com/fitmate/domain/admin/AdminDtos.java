package com.fitmate.domain.admin;

import com.fitmate.domain.safety.UserReport;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record Stats(
            long totalUsers,
            long newUsers7d,
            long acceptedMatches,
            long upcomingGatherings,
            long posts7d,
            long messages7d,
            long pendingReports,
            long suspendedUsers
    ) {
    }

    public enum Action {
        /** 문제 없음 */
        DISMISS,
        /** 경고 알림 */
        WARN,
        SUSPEND_7D,
        SUSPEND_PERMANENT
    }

    public record Resolve(@NotNull(message = "처리 방법을 골라 주세요.") Action action,
                          @Size(max = 500, message = "메모는 500자 이하여야 합니다.") String note) {
    }

    public record UserBrief(Long userId, String nickname) {
    }

    /** reported가 null이면 신고당한 사람이 탈퇴했다 */
    public record ReportedUser(Long userId, String nickname, String profileImageUrl, BigDecimal mannerScore,
                               Instant suspendedUntil, long totalReports) {
    }

    public record Report(
            Long id,
            UserReport.ReportReason reason,
            String detail,
            String status,
            Action action,
            String adminNote,
            Instant createdAt,
            Instant resolvedAt,
            UserBrief reporter,
            ReportedUser reported
    ) {
    }

    public record ReportPage(List<Report> items, Long nextCursor) {
    }

    public record Resolved(int handledReports) {
    }
}
