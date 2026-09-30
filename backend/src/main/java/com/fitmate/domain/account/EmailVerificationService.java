package com.fitmate.domain.account;

import com.fitmate.domain.user.UserRepository;
import com.fitmate.global.error.BusinessException;
import com.fitmate.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 가입·이메일 변경 전에 그 이메일을 실제로 받을 수 있는지 확인한다.
 * 1) 코드 요청 → 6자리 코드를 메일로 보냄 (같은 이메일은 1분에 한 번)
 * 2) 코드 확인 → 30분 동안 쓸 수 있는 1회용 인증 토큰 발급
 * 3) 가입·이메일 변경 요청에 토큰을 함께 보내면, 그 토큰이 같은 이메일로 발급된 것인지 확인하고 지운다
 * 인증 없이 아무 이메일이나 등록되면 아이디·비밀번호 찾기 메일이 남에게 가거나 도착하지 않기 때문이다.
 */
@Service
public class EmailVerificationService {

    static final Duration COOLDOWN = Duration.ofMinutes(1);
    static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String COOLDOWN_PREFIX = "auth:email-verify-cooldown:";
    private static final String TOKEN_PREFIX = "auth:email-verified:";

    private final VerificationCodeStore codeStore;
    private final AccountMailSender mailSender;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(VerificationCodeStore codeStore, AccountMailSender mailSender,
                                    UserRepository userRepository, StringRedisTemplate redisTemplate,
                                    @Value("${fitmate.email-verification.enabled:true}") boolean enabled) {
        this.codeStore = codeStore;
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
    }

    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        Boolean first = redisTemplate.opsForValue().setIfAbsent(COOLDOWN_PREFIX + email, "1", COOLDOWN);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "인증 코드는 1분에 한 번만 보낼 수 있어요.");
        }
        mailSender.sendSignupVerificationCode(email, codeStore.issue(VerificationCodeStore.Purpose.SIGNUP_EMAIL, email));
    }

    /** 코드가 맞으면 인증 토큰을 준다. 토큰 원문은 돌려주기만 하고 Redis에는 해시로 저장한다 */
    public String confirm(String rawEmail, String code) {
        String email = normalize(rawEmail);
        if (code == null || !codeStore.verifyAndConsume(VerificationCodeStore.Purpose.SIGNUP_EMAIL, email, code.strip())) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        redisTemplate.opsForValue().set(TOKEN_PREFIX + VerificationCodeStore.sha256(token), email, TOKEN_TTL);
        return token;
    }

    /**
     * 가입·이메일 변경 시 호출한다. 토큰이 이 이메일로 발급된 것이면 지우고 통과, 아니면 EMAIL_NOT_VERIFIED.
     * GETDEL로 꺼내면서 지우므로 같은 토큰으로 두 계정을 만들 수 없다.
     */
    public void consume(String rawEmail, String token) {
        if (!enabled) {
            return;
        }
        String verifiedEmail = token == null || token.isBlank() ? null
                : redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + VerificationCodeStore.sha256(token.strip()));
        if (verifiedEmail == null || !verifiedEmail.equals(normalize(rawEmail))) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
