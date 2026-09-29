package com.fitmate.domain.admin;

import com.fitmate.domain.auth.RefreshTokenStore;
import com.fitmate.domain.community.PostRepository;
import com.fitmate.domain.notification.Notification;
import com.fitmate.domain.notification.NotificationService;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 관리자 기능: 운영 현황, 신고 처리(경고·정지), 정지 해제, 글 숨김.
 * 모든 메서드는 먼저 요청한 사람이 ADMIN인지 DB에서 확인한다 (토큰을 새로 받기 전에 권한을 뺏어도 바로 막히도록).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    static final int PAGE_SIZE = 30;
    /** 영구 정지는 아주 먼 미래까지 정지로 표현한다 */
    static final Instant PERMANENT = Instant.parse("9999-12-31T00:00:00Z");
    private static final Duration SUSPEND_7D = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final AdminQuery adminQuery;
    private final JdbcClient jdbcClient;
    private final NotificationService notificationService;
    private final RefreshTokenStore refreshTokenStore;
    private final PostRepository postRepository;
    private final Clock clock;

    public AdminDtos.Stats stats(Long adminId) {
        requireAdmin(adminId);
        return adminQuery.stats();
    }

    public AdminDtos.ReportPage reports(Long adminId, boolean pending, Long cursor) {
        requireAdmin(adminId);
        List<AdminDtos.Report> found = adminQuery.reports(pending, cursor, PAGE_SIZE + 1);
        boolean hasNext = found.size() > PAGE_SIZE;
        List<AdminDtos.Report> page = hasNext ? found.subList(0, PAGE_SIZE) : found;
        return new AdminDtos.ReportPage(page, hasNext ? page.get(page.size() - 1).id() : null);
    }

    private record Handled(Long reporterId, Long reportedId) {
    }

    /**
     * 신고 하나를 처리하면 같은 사람에 대한 검토 대기 신고도 모두 같은 결과로 처리한다.
     * 대기 중인 신고만 조건부 UPDATE로 바꾸므로, 관리자 두 명이 동시에 처리해도 한 번만 반영된다.
     */
    @Transactional
    public AdminDtos.Resolved resolve(Long adminId, Long reportId, AdminDtos.Resolve request) {
        requireAdmin(adminId);
        AdminDtos.Action action = request.action();
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();

        List<Handled> handled = jdbcClient.sql("""
                        UPDATE user_reports
                        SET status = :status, action = :action, admin_note = :note, resolved_by = :admin, resolved_at = now()
                        WHERE status = 'PENDING'
                          AND (id = :id OR (reported_id IS NOT NULL
                               AND reported_id = (SELECT reported_id FROM user_reports WHERE id = :id AND status = 'PENDING')))
                        RETURNING reporter_id, reported_id
                        """)
                .param("status", action == AdminDtos.Action.DISMISS ? "DISMISSED" : "RESOLVED")
                .param("action", action.name())
                .param("note", note)
                .param("admin", adminId)
                .param("id", reportId)
                .query((rs, rowNum) -> new Handled((Long) rs.getObject("reporter_id"), (Long) rs.getObject("reported_id")))
                .list();
        if (handled.isEmpty()) {
            boolean exists = Boolean.TRUE.equals(jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM user_reports WHERE id = :id)")
                    .param("id", reportId).query(Boolean.class).single());
            throw new BusinessException(exists ? ErrorCode.REPORT_ALREADY_HANDLED : ErrorCode.REPORT_NOT_FOUND);
        }

        Long reportedId = handled.get(0).reportedId();
        if (reportedId != null) {
            applyToReportedUser(reportedId, action);
        }
        Set<Long> reporters = new LinkedHashSet<>();
        handled.forEach(h -> {
            if (h.reporterId() != null) {
                reporters.add(h.reporterId());
            }
        });
        String body = action == AdminDtos.Action.DISMISS
                ? "신고해 주신 내용을 확인했어요. 이번에는 운영 정책 위반이 확인되지 않았어요."
                : "신고해 주신 내용을 확인하고 조치했어요. 안전한 FitMate를 만들어 주셔서 고마워요!";
        reporters.forEach(reporterId -> notificationService.notify(reporterId, Notification.Type.REPORT_RESOLVED,
                "신고가 처리됐어요", body, null));
        return new AdminDtos.Resolved(handled.size());
    }

    private void applyToReportedUser(Long reportedId, AdminDtos.Action action) {
        switch (action) {
            case DISMISS -> {
            }
            case WARN -> notificationService.notify(reportedId, Notification.Type.ADMIN_WARNING,
                    "운영 정책 위반 경고",
                    "신고가 접수되어 확인한 결과 커뮤니티 이용 규칙 위반이 확인됐어요. 반복되면 이용이 정지될 수 있어요.",
                    null);
            case SUSPEND_7D -> suspend(reportedId, clock.instant().plus(SUSPEND_7D));
            case SUSPEND_PERMANENT -> suspend(reportedId, PERMANENT);
        }
    }

    /** 정지하면 모든 기기의 로그인(리프레시 토큰)을 끊는다. 이미 받은 액세스 토큰은 최대 30분 뒤 만료된다 */
    private void suspend(Long userId, Instant until) {
        User target = userRepository.findById(userId).orElse(null);
        if (target == null) {
            return;
        }
        if (target.isAdmin()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 계정은 정지할 수 없어요.");
        }
        jdbcClient.sql("UPDATE users SET suspended_until = :until WHERE id = :id")
                .param("until", java.sql.Timestamp.from(until))
                .param("id", userId)
                .update();
        refreshTokenStore.revokeAll(userId);
    }

    @Transactional
    public void unsuspend(Long adminId, Long userId) {
        requireAdmin(adminId);
        if (jdbcClient.sql("UPDATE users SET suspended_until = NULL WHERE id = :id").param("id", userId).update() == 0) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    @Transactional
    public void setPostHidden(Long adminId, Long postId, boolean hidden) {
        requireAdmin(adminId);
        int updated = jdbcClient.sql("UPDATE posts SET hidden = :hidden WHERE id = :id")
                .param("hidden", hidden).param("id", postId).update();
        if (updated == 0 || !postRepository.existsById(postId)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
    }

    private void requireAdmin(Long userId) {
        boolean admin = userRepository.findById(userId).map(User::isAdmin).orElse(false);
        if (!admin) {
            throw new BusinessException(ErrorCode.ADMIN_ONLY);
        }
    }
}
