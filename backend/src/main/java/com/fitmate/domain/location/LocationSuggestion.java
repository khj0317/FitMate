package com.fitmate.domain.location;

/**
 * 지역 검색 결과 한 건.
 * name: 목록에 크게 보여줄 이름 (예: 성수역 2호선), address: 전체 주소,
 * areaName: 프로필에 저장할 동 단위 지역명 (예: 서울 성동구 성수동2가)
 */
public record LocationSuggestion(String name, String address, String areaName, double latitude, double longitude) {
}
