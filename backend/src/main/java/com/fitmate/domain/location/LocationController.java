package com.fitmate.domain.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "지역 검색")
@SecurityRequirements // 회원가입 화면에서도 쓰므로 로그인 없이 호출
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationSearchService locationSearchService;

    @Operation(summary = "지역 검색 (자동완성)", description = "동 이름, 역 이름 등으로 검색합니다. 예: 성수, 강남역, 망원동")
    @GetMapping("/search")
    public List<LocationSuggestion> search(@RequestParam(defaultValue = "") String query) {
        return locationSearchService.search(query);
    }
}
