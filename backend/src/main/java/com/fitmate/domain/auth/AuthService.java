package com.fitmate.domain.auth;

import com.fitmate.domain.auth.dto.AuthRequests;
import com.fitmate.domain.auth.dto.AuthResponses;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final RefreshTokenStore refreshTokenStore;

    /**
     * 사전 중복 검사는 친절한 에러 메시지용이고, 동시 가입 요청은 DB 유니크 제약이 최종적으로 막는다.
     * (GlobalExceptionHandler가 제약 조건 이름을 DUPLICATE_EMAIL/NICKNAME으로 변환)
     */
    @Transactional
    public AuthResponses.Signup signup(AuthRequests.Signup request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }

        User user = new User(email, passwordEncoder.encode(request.password()), request.nickname());
        return new AuthResponses.Signup(userRepository.save(user).getId());
    }

    @Transactional(readOnly = true)
    public AuthResponses.Token login(AuthRequests.Login request) {
        // 이메일 존재 여부를 노출하지 않도록 계정이 없을 때와 비밀번호가 틀릴 때 같은 에러를 준다
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        return issueTokens(user.getId());
    }

    @Transactional(readOnly = true)
    public AuthResponses.Token refresh(AuthRequests.Refresh request) {
        Long userId = refreshTokenStore.consume(request.refreshToken())
                .filter(userRepository::existsById)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issueTokens(userId);
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
