package com.fitmate.domain.location;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 카카오 API 키가 없을 때 쓰는 내장 지역 목록 (서울 주요 지역 + 광역시 핵심 지역).
 * 키 없이도 바로 실행해 볼 수 있고, 테스트가 외부 API에 의존하지 않게 해 준다.
 */
public class BuiltInLocationCatalog implements LocationSearchClient {

    private static final String RESOURCE = "locations/kr-areas.csv";

    private final List<Entry> entries;

    public BuiltInLocationCatalog() {
        this.entries = load();
    }

    @Override
    public List<LocationSuggestion> search(String query, int limit) {
        String q = normalize(query);
        return entries.stream()
                .filter(entry -> entry.searchKey().contains(q))
                .sorted(Comparator.comparingInt((Entry entry) -> rank(entry, q)).thenComparing(Entry::name))
                .limit(limit)
                .map(entry -> new LocationSuggestion(entry.name(), entry.areaName(), entry.areaName(),
                        entry.latitude(), entry.longitude()))
                .toList();
    }

    /** 이름이 검색어로 시작하면 가장 먼저, 이름에 포함되면 그다음, 주소에만 있으면 마지막 */
    private static int rank(Entry entry, String q) {
        String name = normalize(entry.name());
        if (name.startsWith(q)) return 0;
        if (name.contains(q)) return 1;
        return 2;
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static List<Entry> load() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1) // 헤더
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(","))
                    .map(cols -> new Entry(cols[0], cols[1], Double.parseDouble(cols[2]), Double.parseDouble(cols[3])))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Entry(String name, String areaName, double latitude, double longitude) {
        String searchKey() {
            return normalize(name + areaName);
        }
    }
}
