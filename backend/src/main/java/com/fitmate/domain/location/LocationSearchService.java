package com.fitmate.domain.location;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 카카오 API 키(KAKAO_REST_API_KEY)가 있으면 카카오 로컬 API, 없으면 내장 지역 목록으로 검색한다.
 * 입력할 때마다 호출되는 자동완성이라 외부 API 결과는 Redis에 하루 동안 캐시하고,
 * 외부 API가 실패하면 내장 목록으로 대체해서 회원가입이 막히지 않게 한다.
 */
@Slf4j
@Service
public class LocationSearchService {

    static final int MAX_QUERY_LENGTH = 50;
    private static final int LIMIT = 8;
    private static final Duration CACHE_TTL = Duration.ofDays(1);
    private static final String CACHE_PREFIX = "location:search:";
    private static final TypeReference<List<LocationSuggestion>> LIST_TYPE = new TypeReference<>() {
    };

    private final LocationSearchClient client;
    private final BuiltInLocationCatalog fallback = new BuiltInLocationCatalog();
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LocationSearchService(@Value("${kakao.rest-api-key:}") String kakaoRestApiKey,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper) {
        this.client = kakaoRestApiKey.isBlank()
                ? fallback
                : new KakaoLocationSearchClient(RestClient.builder(), kakaoRestApiKey);
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        log.info("지역 검색: {}", kakaoRestApiKey.isBlank() ? "내장 목록 (KAKAO_REST_API_KEY 미설정)" : "카카오 로컬 API");
    }

    public List<LocationSuggestion> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            return List.of();
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }
        if (!client.cacheable()) {
            return client.search(query, LIMIT);
        }

        String cacheKey = CACHE_PREFIX + query.toLowerCase(Locale.ROOT);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return objectMapper.readValue(cached, LIST_TYPE);
        }
        try {
            List<LocationSuggestion> results = client.search(query, LIMIT);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(results), CACHE_TTL);
            return results;
        } catch (RuntimeException e) {
            log.warn("외부 지역 검색 실패, 내장 목록으로 대체합니다: {}", e.getMessage());
            return fallback.search(query, LIMIT);
        }
    }
}
