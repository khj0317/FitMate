package com.fitmate.domain.auth;

import com.fitmate.domain.account.EmailVerificationService;
import com.fitmate.domain.auth.dto.AuthRequests;
import com.fitmate.domain.auth.dto.AuthResponses;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.demo.DemoAccounts;
import com.fitmate.global.error.ErrorCode;
import com.fitmate.global.util.GeoPoints;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final EmailVerificationService emailVerificationService;
    private final DemoAccounts demoAccounts;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 이보다 먼 정지는 영구 정지로 본다 */
    private static final Instant PERMANENT_THRESHOLD = Instant.parse("2100-01-01T00:00:00Z");

    /**
     * 사전 중복 검사는 친절한 에러 메시지용이고, 동시 가입 요청은 DB 유니크 제약이 최종적으로 막는다.
     * (GlobalExceptionHandler가 제약 조건 이름을 DUPLICATE_LOGIN_ID/EMAIL/NICKNAME으로 변환)
     */
    @Transactional
    public AuthResponses.Signup signup(AuthRequests.Signup request) {
        if (!request.password().equals(request.passwordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        if (request.birthDate().isBefore(User.MIN_BIRTH_DATE)) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_DATE);
        }
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        emailVerificationService.consume(request.email(), request.emailVerificationToken());

        User user = new User(request.loginId(), passwordEncoder.encode(request.password()), request.nickname());
        user.changeEmail(request.email());
        user.changeBirthDate(request.birthDate());
        user.changeGender(request.gender());
        user.changeActivityLocation(
                GeoPoints.of(request.location().latitude(), request.location().longitude()),
                request.location().areaName().strip());
        return new AuthResponses.Signup(userRepository.save(user).getId());
    }

    @Transactional(readOnly = true)
    public AuthResponses.Token login(AuthRequests.Login request, String clientIp) {
        loginAttemptLimiter.checkAllowed(request.loginId(), clientIp);
        // 아이디 존재 여부를 노출하지 않도록 계정이 없을 때와 비밀번호가 틀릴 때 같은 에러를 준다
        User user = userRepository.findByLoginId(request.loginId())
                .filter(found -> !demoAccounts.locked(found.getLoginId()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElse(null);
        if (user == null) {
            loginAttemptLimiter.recordFailure(request.loginId(), clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        loginAttemptLimiter.reset(request.loginId());
        checkNotSuspended(user);
        return issueTokens(user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponses.Token refresh(AuthRequests.Refresh request) {
        User user = refreshTokenStore.consume(request.refreshToken())
                .flatMap(userRepository::findById)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        checkNotSuspended(user);
        return issueTokens(user.getId());
    }

    private void checkNotSuspended(User user) {
        if (!user.isSuspended(Instant.now())) {
            return;
        }
        String until = user.getSuspendedUntil().isAfter(PERMANENT_THRESHOLD) ? "영구 정지"
                : DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm").withZone(SEOUL).format(user.getSuspendedUntil()) + "까지";
        throw new BusinessException(ErrorCode.ACCOUNT_SUSPENDED,
                "운영 정책 위반으로 이용이 정지된 계정이에요 (" + until + ").");
    }

    public void logout(AuthRequests.Refresh request) {
        refreshTokenStore.revoke(request.refreshToken());
    }

    private AuthResponses.Token issueTokens(Long userId) {
        return AuthResponses.Token.bearer(
                accessTokenIssuer.issue(userId),
                refreshTokenStore.issue(userId),
                accessTokenIssuer.ttlSeconds()
        );
    }

}
