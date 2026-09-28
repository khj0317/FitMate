package com.fitmate.domain.location;

import java.util.List;

public interface LocationSearchClient {

    List<LocationSuggestion> search(String query, int limit);

    /** 외부 API 결과처럼 캐시할 가치가 있는지 (내장 목록은 메모리 검색이라 캐시 불필요) */
    default boolean cacheable() {
        return false;
    }
}
