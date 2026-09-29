package com.fitmate.domain.gathering;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GatheringRepository extends JpaRepository<Gathering, Long> {

    /**
     * 선착순 자리 확보. "모집 중이고, 자리가 남았고, 아직 시작 전"일 때만 인원을 1 늘리고,
     * 이번에 정원이 차면 모집 마감(CLOSED)으로 바꾼다. 조건 확인과 증가가 UPDATE 한 문장이라
     * 행 잠금 안에서 원자적으로 처리되고, 동시에 몇 명이 신청해도 정원을 넘지 않는다.
     * (SET 절의 current_count는 모두 바뀌기 전 값을 쓴다)
     *
     * @return 1이면 자리 확보, 0이면 마감·정원 초과·시작됨
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE gatherings
            SET current_count = current_count + 1,
                status = CASE WHEN current_count + 1 >= capacity THEN 'CLOSED' ELSE status END,
                version = version + 1
            WHERE id = :id
              AND status = 'RECRUITING'
              AND current_count < capacity
              AND starts_at > now()
            """, nativeQuery = true)
    int tryReserveSeat(@Param("id") Long id);

    /** 참가자가 나가면 한 자리를 돌려주고, 마감이었다면 다시 모집 중으로 연다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE gatherings
            SET current_count = current_count - 1,
                status = CASE WHEN status = 'CLOSED' THEN 'RECRUITING' ELSE status END,
                version = version + 1
            WHERE id = :id
              AND current_count > 1
              AND status IN ('RECRUITING', 'CLOSED')
            """, nativeQuery = true)
    int releaseSeat(@Param("id") Long id);
}
