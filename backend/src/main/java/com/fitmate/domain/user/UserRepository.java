package com.fitmate.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByNickname(String nickname);

    boolean existsByIdAndRole(Long id, User.Role role);

    long countByRole(User.Role role);

    @Query("select u.id from User u where u.role = :role and u.createdAt < :before")
    List<Long> findIdsByRoleAndCreatedAtBefore(@Param("role") User.Role role, @Param("before") Instant before);

    /** 읽고-계산하고-쓰지 않고 DB에서 한 번에 더해서, 동시에 평가가 몰려도 점수를 잃지 않는다 (0.0 ~ 99.9) */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE users
            SET manner_score = LEAST(99.9, GREATEST(0, manner_score + :delta))
            WHERE id = :id
            """, nativeQuery = true)
    int adjustMannerScore(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
