package com.fitmate.domain.account;

import com.fitmate.domain.auth.RefreshTokenStore;
import com.fitmate.domain.user.User;
import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.demo.DemoAccounts;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;

/**
 * 아이디 찾기, 비밀번호 재설정.
 * 가입 여부와 상관없이 항상 같은 응답을 주고 메일만 조건부로 보내서,
 * 이 기능으로 어떤 아이디·이메일이 가입돼 있는지 알아낼 수 없게 한다.
 */
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    static final Duration MAIL_COOLDOWN = Duration.ofMinutes(1);
    private static final String COOLDOWN_PREFIX = "auth:mail-cooldown:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeStore codeStore;
    private final RefreshTokenStore refreshTokenStore;
    private final AccountMailSender mailSender;
    private final StringRedisTemplate redisTemplate;
    private final DemoAccounts demoAccounts;

    @Transactional(readOnly = true)
    public void sendLoginId(String rawEmail) {
        String email = normalize(rawEmail);
        checkCooldown("login-id:" + email);
        userRepository.findByEmail(email)
                .filter(user -> !demoAccounts.locked(user.getLoginId()))
                .ifPresent(user -> mailSender.sendLoginId(email, user.getLoginId()));
    }

    @Transactional(readOnly = true)
    public void sendPasswordResetCode(String rawLoginId, String rawEmail) {
        String loginId = normalize(rawLoginId);
        String email = normalize(rawEmail);
        checkCooldown("password:" + loginId);
        userRepository.findByLoginId(loginId)
                .filter(user -> email.equals(user.getEmail()))
                .filter(user -> !demoAccounts.locked(user.getLoginId())) // 데모 계정 메일(@fitmate.com)은 남의 도메인
                .ifPresent(user -> mailSender.sendPasswordResetCode(email, codeStore.issue(VerificationCodeStore.Purpose.PASSWORD_RESET, loginId)));
    }

    @Transactional
    public void resetPassword(String rawLoginId, String code, String newPassword, String newPasswordConfirm) {
        if (!newPassword.equals(newPasswordConfirm)) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        String loginId = normalize(rawLoginId);
        if (!codeStore.verifyAndConsume(VerificationCodeStore.Purpose.PASSWORD_RESET, loginId, code.strip())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
        user.changePassword(passwordEncoder.encode(newPassword));
        refreshTokenStore.revokeAll(user.getId()); // 다른 기기에 남아 있던 로그인도 모두 끊는다
    }

    /** 같은 대상에게 1분 안에 다시 메일을 보낼 수 없다 (메일 폭탄·무차별 요청 방지) */
    private void checkCooldown(String target) {
        Boolean first = redisTemplate.opsForValue().setIfAbsent(COOLDOWN_PREFIX + target, "1", MAIL_COOLDOWN);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
