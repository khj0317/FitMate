package com.fitmate.domain.safety;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

    /** 둘 중 누가 누구를 차단했든 차단 관계가 있는지 */
    @Query("""
            select count(b) > 0 from UserBlock b
            where (b.blockerId = :a and b.blockedId = :b) or (b.blockerId = :b and b.blockedId = :a)
            """)
    boolean existsBetween(@Param("a") Long a, @Param("b") Long b);

    List<UserBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);
}
