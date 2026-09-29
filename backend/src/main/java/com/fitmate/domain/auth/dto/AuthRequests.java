package com.fitmate.domain.auth.dto;

import com.fitmate.domain.user.Gender;
import com.fitmate.domain.user.dto.UserRequests;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Locale;

public final class AuthRequests {

    public static final String LOGIN_ID_PATTERN = "^[a-z0-9_]{4,20}$";
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String LOGIN_ID_MESSAGE = "아이디는 영문 소문자, 숫자, _로 4~20자여야 합니다.";

    private AuthRequests() {
    }

    public record Signup(
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Pattern(regexp = LOGIN_ID_PATTERN, message = LOGIN_ID_MESSAGE)
            String loginId,

            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
            @Pattern(regexp = PASSWORD_PATTERN, message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String password,

            @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
            String passwordConfirm,

            @NotBlank(message = "닉네임을 입력해 주세요.")
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.")
            String nickname,

            /* 아이디·비밀번호 찾기에 쓰이므로 필수 */
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 255)
            String email,

            @NotNull(message = "생년월일을 입력해 주세요.")
            @Past(message = "생년월일이 올바르지 않습니다.")
            LocalDate birthDate,

            @NotNull(message = "성별을 선택해 주세요.")
            Gender gender,

            @NotNull(message = "활동 지역을 선택해 주세요.")
            @Valid
            UserRequests.UpdateLocation location,

            /* 이메일 인증(POST /api/auth/email-verification/confirm)으로 받은 토큰 */
            String emailVerificationToken
    ) {
        /** 형식 검증 전에 정규화: 아이디는 소문자로, 이메일은 공백 제거 후 비어 있으면 null */
        public Signup {
            loginId = loginId == null ? null : loginId.strip().toLowerCase(Locale.ROOT);
            email = email == null || email.isBlank() ? null : email.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record Login(
            @NotBlank(message = "아이디를 입력해 주세요.") String loginId,
            @NotBlank(message = "비밀번호를 입력해 주세요.") String password
    ) {
        public Login {
            loginId = loginId == null ? null : loginId.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record Refresh(@NotBlank String refreshToken) {
    }
}
