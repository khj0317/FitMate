package com.fitmate.domain.matchrequest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    /**
     * SELECT ... FOR UPDATE: 같은 요청에 수락과 취소가 동시에 들어오면 뒤에 온 트랜잭션이
     * 앞 트랜잭션이 끝날 때까지 기다렸다가 바뀐 상태를 보고 실패한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MatchRequest m where m.id = :id")
    Optional<MatchRequest> findByIdForUpdate(@Param("id") Long id);

    boolean existsByRequesterIdAndReceiverIdAndStatus(Long requesterId, Long receiverId, MatchRequestStatus status);

    @EntityGraph(attributePaths = {"requester", "sport"})
    List<MatchRequest> findByReceiverIdAndStatusOrderByIdDesc(Long receiverId, MatchRequestStatus status);

    @EntityGraph(attributePaths = {"receiver", "sport"})
    List<MatchRequest> findByRequesterIdAndStatusOrderByIdDesc(Long requesterId, MatchRequestStatus status);
}
