package com.fitmate.domain.admin;

import com.fitmate.domain.safety.UserReport;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AdminQuery {

    private final JdbcClient jdbcClient;

    /** 운영 현황을 한 번의 SQL로 */
    public AdminDtos.Stats stats() {
        return jdbcClient.sql("""
                        SELECT
                          (SELECT COUNT(*) FROM users WHERE role <> 'GUEST') AS total_users, -- 체험 계정은 가입자가 아님
                          (SELECT COUNT(*) FROM users WHERE role <> 'GUEST' AND created_at > now() - interval '7 days') AS new_users,
                          (SELECT COUNT(*) FROM match_requests WHERE status = 'ACCEPTED') AS matches,
                          (SELECT COUNT(*) FROM gatherings WHERE status IN ('RECRUITING', 'CLOSED') AND starts_at > now()) AS gatherings,
                          (SELECT COUNT(*) FROM posts WHERE created_at > now() - interval '7 days') AS posts,
                          (SELECT COUNT(*) FROM chat_messages WHERE created_at > now() - interval '7 days'
                                                                AND message_type <> 'SYSTEM') AS messages,
                          (SELECT COUNT(*) FROM user_reports WHERE status = 'PENDING') AS pending_reports,
                          (SELECT COUNT(*) FROM users WHERE suspended_until > now()) AS suspended
                        """)
                .query((rs, rowNum) -> new AdminDtos.Stats(rs.getLong("total_users"), rs.getLong("new_users"),
                        rs.getLong("matches"), rs.getLong("gatherings"), rs.getLong("posts"), rs.getLong("messages"),
                        rs.getLong("pending_reports"), rs.getLong("suspended")))
                .single();
    }

    /** pending=true면 검토 대기, false면 처리 완료. 최신순 */
    public List<AdminDtos.Report> reports(boolean pending, Long cursor, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id, r.reason, r.detail, r.status, r.action, r.admin_note, r.created_at, r.resolved_at,
                               rep.id AS reporter_id, rep.nickname AS reporter_nickname,
                               t.id AS target_id, t.nickname AS target_nickname, t.profile_image_url AS target_image,
                               t.manner_score AS target_manner, t.suspended_until AS target_suspended_until,
                               (SELECT COUNT(*) FROM user_reports x WHERE x.reported_id = r.reported_id) AS target_reports
                        FROM user_reports r
                        LEFT JOIN users rep ON rep.id = r.reporter_id
                        LEFT JOIN users t ON t.id = r.reported_id
                        WHERE (r.status = 'PENDING') = :pending
                          AND (CAST(:cursor AS BIGINT) IS NULL OR r.id < CAST(:cursor AS BIGINT))
                        ORDER BY r.id DESC
                        LIMIT :limit
                        """)
                .param("pending", pending)
                .param("cursor", cursor)
                .param("limit", limit)
                .query((rs, rowNum) -> mapReport(rs))
                .list();
    }

    private static AdminDtos.Report mapReport(ResultSet rs) throws SQLException {
        long reporterId = rs.getLong("reporter_id");
        AdminDtos.UserBrief reporter = rs.wasNull() ? null
                : new AdminDtos.UserBrief(reporterId, rs.getString("reporter_nickname"));
        long targetId = rs.getLong("target_id");
        AdminDtos.ReportedUser reported = rs.wasNull() ? null
                : new AdminDtos.ReportedUser(targetId, rs.getString("target_nickname"), rs.getString("target_image"),
                        rs.getBigDecimal("target_manner"), instant(rs, "target_suspended_until"),
                        rs.getLong("target_reports"));
        String action = rs.getString("action");
        return new AdminDtos.Report(
                rs.getLong("id"),
                UserReport.ReportReason.valueOf(rs.getString("reason")),
                rs.getString("detail"),
                rs.getString("status"),
                action == null ? null : AdminDtos.Action.valueOf(action),
                rs.getString("admin_note"),
                instant(rs, "created_at"),
                instant(rs, "resolved_at"),
                reporter,
                reported);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
