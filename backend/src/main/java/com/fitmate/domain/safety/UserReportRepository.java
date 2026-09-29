package com.fitmate.domain.safety;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    boolean existsByReporterIdAndReportedIdAndStatus(Long reporterId, Long reportedId, String status);
}
