package com.fitmate.domain.gathering;

import com.fitmate.domain.gathering.dto.GatheringDtos;
import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "모임")
@RestController
@RequestMapping("/api/gatherings")
@RequiredArgsConstructor
public class GatheringController {

    private final GatheringService gatheringService;

    @Operation(summary = "모임 만들기", description = "모임장이 첫 참가자가 되고 단체 채팅방이 만들어집니다. 시간은 10분 뒤 ~ 60일 안.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GatheringDtos.Detail create(@Parameter(hidden = true) @LoginUserId Long userId,
                                       @Valid @RequestBody GatheringDtos.Create request) {
        return gatheringService.create(userId, request);
    }

    @Operation(summary = "내 주변 모임", description = "앞으로 열리는 모임을 가까운 날짜순으로 보여줍니다.")
    @GetMapping
    public List<GatheringDtos.Summary> nearby(@Parameter(hidden = true) @LoginUserId Long userId,
                                              @RequestParam(required = false) Short sportId,
                                              @RequestParam(required = false) @Min(1) @Max(50) Integer radiusKm) {
        return gatheringService.nearby(userId, sportId, radiusKm);
    }

    @Operation(summary = "내가 참여한 모임")
    @GetMapping("/mine")
    public List<GatheringDtos.Summary> mine(@Parameter(hidden = true) @LoginUserId Long userId) {
        return gatheringService.mine(userId);
    }

    @Operation(summary = "모임 상세", description = "참가자 목록과, 참여 중이면 단체 채팅방 ID를 포함합니다.")
    @GetMapping("/{gatheringId}")
    public GatheringDtos.Detail detail(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long gatheringId) {
        return gatheringService.detail(userId, gatheringId);
    }

    @Operation(summary = "모임 참여 (선착순)", description = "정원이 다 찼거나 마감·시작된 모임이면 409.")
    @PostMapping("/{gatheringId}/participants")
    public GatheringDtos.Joined join(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long gatheringId) {
        return gatheringService.join(userId, gatheringId);
    }

    @Operation(summary = "모임 나가기", description = "시작 전에만 가능, 모임장은 나갈 수 없습니다.")
    @DeleteMapping("/{gatheringId}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long gatheringId) {
        gatheringService.leave(userId, gatheringId);
    }

    @Operation(summary = "모임 취소 (모임장)", description = "시작 전에만 가능, 참가자 모두에게 알림이 갑니다.")
    @DeleteMapping("/{gatheringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@Parameter(hidden = true) @LoginUserId Long userId, @PathVariable Long gatheringId) {
        gatheringService.cancel(userId, gatheringId);
    }
}
