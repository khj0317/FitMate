package com.fitmate.domain.matching;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * PostGIS 반경 검색과 운동 시간 겹침 계산은 JPQL로 표현하기 어려워 네이티브 SQL로 작성한다.
 */
@Repository
@RequiredArgsConstructor
public class MatchCandidateQuery {

    /*
     * 1) ST_DWithin + GIST 인덱스로 반경 안의 사용자만 거른다 (geography 타입이라 단위는 미터)
     * 2) 검색 종목 중 하나 이상을 하는 사람만 남기고, 가까운 순으로 poolSize명까지 자른다
     * 3) 후보별로 나와 같은 요일에 겹치는 운동 가능 시간(분)을 합산한다
     */
    private static final String SQL = """
            WITH me AS (
                SELECT id, activity_location FROM users WHERE id = :userId
            ),
            candidates AS (
                SELECT u.id, ST_Distance(u.activity_location, me.activity_location) AS distance_meters
                FROM users u CROSS JOIN me
                WHERE u.id <> me.id
                  AND u.activity_location IS NOT NULL
                  AND ST_DWithin(u.activity_location, me.activity_location, :radiusMeters)
                  AND EXISTS (
                      SELECT 1 FROM user_sports us
                      WHERE us.user_id = u.id AND us.sport_id IN (:sportIds)
                  )
                ORDER BY distance_meters
                LIMIT :poolSize
            )
            SELECT c.id AS user_id,
                   c.distance_meters,
                   COALESCE((
                       SELECT SUM(EXTRACT(EPOCH FROM (LEAST(mine.end_time, theirs.end_time)
                                                     - GREATEST(mine.start_time, theirs.start_time))) / 60)
                       FROM user_available_times mine
                       JOIN user_available_times theirs ON theirs.day_of_week = mine.day_of_week
                       WHERE mine.user_id = :userId
                         AND theirs.user_id = c.id
                         AND mine.start_time < theirs.end_time
                         AND theirs.start_time < mine.end_time
                   ), 0) AS overlap_minutes
            FROM candidates c
            """;

    private final JdbcClient jdbcClient;

    public List<Row> find(Long userId, double radiusMeters, Collection<Short> sportIds, int poolSize) {
        return jdbcClient.sql(SQL)
                .param("userId", userId)
                .param("radiusMeters", radiusMeters)
                .param("sportIds", sportIds)
                .param("poolSize", poolSize)
                .query((rs, rowNum) -> new Row(
                        rs.getLong("user_id"),
                        rs.getDouble("distance_meters"),
                        rs.getLong("overlap_minutes")))
                .list();
    }

    public record Row(Long userId, double distanceMeters, long overlapMinutes) {
    }
}
