package com.fitmate.domain.presence;

import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "접속 상태")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    @Operation(summary = "접속 상태 조회",
            description = "online이면 현재 접속중, 아니면 lastSeenAt(마지막 접속 시각, 기록이 없으면 null). 한 번에 최대 100명.")
    @GetMapping("/presence")
    public List<PresenceService.Presence> presence(@RequestParam List<Long> userIds) {
        if (userIds.size() > PresenceService.MAX_QUERY_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return presenceService.find(userIds);
    }
}
