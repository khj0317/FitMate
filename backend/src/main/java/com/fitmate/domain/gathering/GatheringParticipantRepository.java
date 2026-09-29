package com.fitmate.domain.gathering;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GatheringParticipantRepository extends JpaRepository<GatheringParticipant, GatheringParticipant.Key> {

    List<GatheringParticipant> findByGatheringIdAndStatus(Long gatheringId, GatheringParticipant.Status status);
}
