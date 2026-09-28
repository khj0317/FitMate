package com.fitmate.domain.user;

import com.fitmate.domain.user.dto.UserRequests;
import com.fitmate.domain.user.dto.UserResponses;
import com.fitmate.global.security.LoginUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원 프로필")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 프로필 조회")
    @GetMapping("/me")
    public UserResponses.MyProfile getMyProfile(@Parameter(hidden = true) @LoginUserId Long userId) {
        return userService.getMyProfile(userId);
    }

    @Operation(summary = "내 프로필 수정", description = "보낸 필드만 수정합니다.")
    @PatchMapping("/me")
    public UserResponses.MyProfile updateProfile(@Parameter(hidden = true) @LoginUserId Long userId,
                                                 @Valid @RequestBody UserRequests.UpdateProfile request) {
        return userService.updateProfile(userId, request);
    }

    @Operation(summary = "내 프로필 전체 저장",
            description = "기본 정보, 활동 지역, 운동 종목, 운동 가능 시간을 한 번에 저장합니다. 하나라도 실패하면 모두 저장되지 않습니다.")
    @PutMapping("/me")
    public UserResponses.MyProfile updateAll(@Parameter(hidden = true) @LoginUserId Long userId,
                                             @Valid @RequestBody UserRequests.UpdateAll request) {
        return userService.updateAll(userId, request);
    }

    @Operation(summary = "활동 지역 설정")
    @PutMapping("/me/location")
    public UserResponses.MyProfile updateLocation(@Parameter(hidden = true) @LoginUserId Long userId,
                                                  @Valid @RequestBody UserRequests.UpdateLocation request) {
        return userService.updateLocation(userId, request);
    }

    @Operation(summary = "운동 종목·실력 설정", description = "보낸 목록으로 전체를 교체합니다.")
    @PutMapping("/me/sports")
    public UserResponses.MyProfile updateSports(@Parameter(hidden = true) @LoginUserId Long userId,
                                                @Valid @RequestBody UserRequests.UpdateSports request) {
        return userService.updateSports(userId, request);
    }

    @Operation(summary = "운동 가능 시간대 설정", description = "보낸 목록으로 전체를 교체합니다.")
    @PutMapping("/me/available-times")
    public UserResponses.MyProfile updateAvailableTimes(@Parameter(hidden = true) @LoginUserId Long userId,
                                                        @Valid @RequestBody UserRequests.UpdateAvailableTimes request) {
        return userService.updateAvailableTimes(userId, request);
    }

    @Operation(summary = "다른 사용자 프로필 조회")
    @GetMapping("/{userId}")
    public UserResponses.PublicProfile getPublicProfile(@PathVariable Long userId) {
        return userService.getPublicProfile(userId);
    }
}
