package com.fitmate.domain.location;

import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 카카오 API 키가 없을 때 쓰는 내장 지역 목록.
 * - kr-areas.csv : 전국 시 · 시군구 · 읍면동 (통계청 행정동 경계에서 생성, scripts/generate_kr_areas.py)
 * - kr-places.csv: 주요 역 · 명소
 *
 * 사람들은 행정동 이름(망원1동, 성수1가1동) 대신 "망원동", "성수동"처럼 검색하므로
 * 숫자와 "N가"를 뺀 별칭(망원동, 성수동)으로도 찾을 수 있게 한다.
 */
public class BuiltInLocationCatalog implements LocationSearchClient {

    private static final List<String> RESOURCES = List.of("locations/kr-areas.csv", "locations/kr-places.csv");

    private final List<Entry> entries;

    public BuiltInLocationCatalog() {
        this.entries = RESOURCES.stream().flatMap(BuiltInLocationCatalog::load).toList();
    }

    @Override
    public List<LocationSuggestion> search(String query, int limit) {
        String q = normalize(query);
        if (q.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(entry -> rank(entry, q) < Integer.MAX_VALUE)
                .sorted(Comparator.comparingInt((Entry entry) -> rank(entry, q))
                        .thenComparingInt(Entry::level)
                        .thenComparingInt(entry -> entry.name().length())
                        .thenComparing(Entry::areaName))
                .limit(limit)
                .map(entry -> new LocationSuggestion(entry.name(), entry.areaName(), entry.areaName(),
                        entry.latitude(), entry.longitude()))
                .toList();
    }

    /**
     * 0: 이름(또는 별칭)이 검색어로 시작 / 1: 이름에 포함 / 2: 주소에 포함 / MAX: 불일치
     * 같은 순위 안에서는 넓은 지역(시 → 구 → 역 → 동)을 먼저 보여준다.
     */
    private static int rank(Entry entry, String q) {
        if (entry.nameKey().startsWith(q) || entry.aliasKey().startsWith(q)) return 0;
        if (entry.nameKey().contains(q) || entry.aliasKey().contains(q)) return 1;
        if (entry.areaKey().contains(q)) return 2;
        return Integer.MAX_VALUE;
    }

    static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    /** 행정동 이름을 사람들이 부르는 이름으로: 망원1동 → 망원동, 성수1가1동 → 성수동, 창1동 → 창동, 두류1,2동 → 두류동 */
    static String alias(String name) {
        return name.replaceAll("\\d+가", "").replaceAll("제?[\\d,·.]+(?=동$)", "");
    }

    private static Stream<Entry> load(String resource) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8))) {
            List<Entry> loaded = new ArrayList<>();
            reader.readLine(); // 헤더
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank()) continue;
                List<String> cols = parseCsvLine(line);
                loaded.add(new Entry(cols.get(0), cols.get(1), Double.parseDouble(cols.get(2)),
                        Double.parseDouble(cols.get(3)), Integer.parseInt(cols.get(4))));
            }
            return loaded.stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** "두류1,2동"처럼 쉼표가 들어간 이름은 큰따옴표로 감싸져 있다 */
    static List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cols.add(current.toString());
        return cols;
    }

    private record Entry(String name, String areaName, double latitude, double longitude, int level,
                         String nameKey, String aliasKey, String areaKey) {

        Entry(String name, String areaName, double latitude, double longitude, int level) {
            this(name, areaName, latitude, longitude, level,
                    normalize(name), normalize(alias(name)), normalize(areaName));
        }
    }
}
