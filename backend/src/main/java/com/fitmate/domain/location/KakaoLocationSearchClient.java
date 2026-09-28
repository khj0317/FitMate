package com.fitmate.domain.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카카오 로컬 API로 지역을 검색한다.
 * 주소 검색(동 이름 등)을 먼저, 키워드 검색(역, 장소)을 뒤에 붙이고 같은 지역명은 합친다.
 */
public class KakaoLocationSearchClient implements LocationSearchClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;

    public KakaoLocationSearchClient(RestClient.Builder builder, String restApiKey) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        requestFactory.setReadTimeout(TIMEOUT);
        this.restClient = builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + restApiKey)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<LocationSuggestion> search(String query, int limit) {
        Map<String, LocationSuggestion> results = new LinkedHashMap<>();

        AddressResponse addresses = restClient.get()
                .uri(uri -> uri.path("/v2/local/search/address.json")
                        .queryParam("query", query).queryParam("size", 5).build())
                .retrieve()
                .body(AddressResponse.class);
        if (addresses != null) {
            for (AddressDocument doc : addresses.documents()) {
                String areaName = AreaNames.fromAddress(doc.addressName());
                results.putIfAbsent("addr:" + areaName,
                        new LocationSuggestion(areaName, doc.addressName(), areaName, doc.y(), doc.x()));
            }
        }

        KeywordResponse keywords = restClient.get()
                .uri(uri -> uri.path("/v2/local/search/keyword.json")
                        .queryParam("query", query).queryParam("size", 10).build())
                .retrieve()
                .body(KeywordResponse.class);
        if (keywords != null) {
            for (KeywordDocument doc : keywords.documents()) {
                results.putIfAbsent("place:" + doc.placeName() + doc.addressName(), new LocationSuggestion(
                        doc.placeName(), doc.addressName(), AreaNames.fromAddress(doc.addressName()), doc.y(), doc.x()));
            }
        }

        return new ArrayList<>(results.values()).subList(0, Math.min(limit, results.size()));
    }

    @Override
    public boolean cacheable() {
        return true;
    }

    record AddressResponse(List<AddressDocument> documents) {
    }

    record AddressDocument(@JsonProperty("address_name") String addressName, double x, double y) {
    }

    record KeywordResponse(List<KeywordDocument> documents) {
    }

    record KeywordDocument(
            @JsonProperty("place_name") String placeName,
            @JsonProperty("address_name") String addressName,
            double x,
            double y
    ) {
    }
}
