package com.fitmate.domain.location;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInLocationCatalogTest {

    private static final BuiltInLocationCatalog CATALOG = new BuiltInLocationCatalog();

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "망원1동 | 망원동",
            "성수1가1동 | 성수동",
            "창1동 | 창동",
            "두류1,2동 | 두류동",
            "상동 | 상동",
            "세종대왕면 | 세종대왕면"
    })
    @DisplayName("행정동 이름에서 사람들이 부르는 이름(별칭)을 만든다")
    void alias(String name, String expected) {
        assertThat(BuiltInLocationCatalog.alias(name)).isEqualTo(expected);
    }

    @Test
    @DisplayName("따옴표로 감싼 쉼표 포함 이름을 CSV에서 올바르게 읽는다")
    void parseQuotedCsv() {
        assertThat(BuiltInLocationCatalog.parseCsvLine("\"두류1,2동\",\"대구 달서구 두류1,2동\",35.85,128.56,3"))
                .containsExactly("두류1,2동", "대구 달서구 두류1,2동", "35.85", "128.56", "3");
    }

    @Test
    @DisplayName("전국 시군구와 읍면동이 모두 들어 있다 (서울 25개 구 포함, 약 3,800곳)")
    void coversWholeCountry() {
        assertThat(CATALOG.search("서울", 1000).stream().filter(s -> s.name().endsWith("구")).count())
                .isEqualTo(25);
        assertThat(CATALOG.search("제주", 3)).isNotEmpty();
        assertThat(CATALOG.search("해운대", 3)).isNotEmpty();
        assertThat(CATALOG.search("두류", 3)).extracting(LocationSuggestion::name).contains("두류1,2동");
    }

    @Test
    @DisplayName("좌표는 모두 대한민국 범위 안에 있다")
    void coordinatesInKorea() {
        assertThat(CATALOG.search("동", 10_000)).allSatisfy(s -> {
            assertThat(s.latitude()).isBetween(33.0, 39.0);
            assertThat(s.longitude()).isBetween(124.0, 132.0);
        });
    }
}
