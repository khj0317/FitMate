package com.fitmate.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {

    private AuthRequests() {
    }

    public record Signup(
            @NotBlank @Email(message = "올바른 이메일 형식이 아닙니다.") @Size(max = 255)
            String email,

            @NotBlank
            @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
            String password,

            @NotBlank
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
            @Pattern(regexp = "^[가-힣a-zA-Z0-9_]+$", message = "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다.")
            String nickname
    ) {
        public Signup {
            email = trim(email);
        }
    }

    public record Login(@NotBlank String email, @NotBlank String password) {
        public Login {
            email = trim(email);
        }
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    /** 형식 검증 전에 앞뒤 공백을 제거해서, 복사·붙여넣기로 들어간 공백 때문에 가입이 거절되지 않게 한다. */
    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
