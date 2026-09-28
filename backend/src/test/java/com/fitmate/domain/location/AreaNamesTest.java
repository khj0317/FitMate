package com.fitmate.domain.location;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AreaNamesTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "서울 성동구 성수동2가 289-5 | 서울 성동구 성수동2가",
            "경기 성남시 분당구 삼평동 681 | 경기 성남시 분당구 삼평동",
            "서울 마포구 망원동 | 서울 마포구 망원동",
            "강원특별자치도 춘천시 신북읍 천전리 100 | 강원특별자치도 춘천시 신북읍",
            "서울 강남구 123 | 서울 강남구"
    })
    void cutsAddressAtDongLevel(String address, String expected) {
        assertThat(AreaNames.fromAddress(address)).isEqualTo(expected);
    }
}
