package com.fitmate.domain.matching;

import com.fitmate.domain.matching.dto.MatchCandidateResponse;
import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "매칭")
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @Operation(
            summary = "내 주변 운동 친구 추천",
            description = """
                    활동 지역 반경 안에서 같은 종목을 하는 사용자를 매칭 점수 순으로 추천합니다.
                    점수(100점) = 거리 35 + 실력 30 + 운동 시간 25 + 매너 10
                    sportId를 생략하면 내가 등록한 종목 전체로 검색하고, radiusKm를 생략하면 프로필의 검색 반경을 사용합니다.
                    """)
    @GetMapping("/recommendations")
    public List<MatchCandidateResponse> recommend(
            @Parameter(hidden = true) @LoginUserId Long userId,
            @Parameter(description = "검색할 운동 종목 ID") @RequestParam(required = false) Short sportId,
            @Parameter(description = "검색 반경(km), 1~50") @RequestParam(required = false) @Min(1) @Max(50) Integer radiusKm,
            @Parameter(description = "최대 결과 수, 1~50") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return matchingService.recommend(userId, sportId, radiusKm, limit);
    }
}
