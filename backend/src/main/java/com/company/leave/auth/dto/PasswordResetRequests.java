package com.company.leave.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class PasswordResetRequests {

    private PasswordResetRequests() {
    }

    /** 비밀번호 찾기: 재설정 링크 메일 요청. */
    public record Request(@NotBlank @Email String email) {
    }

    /** 재설정 링크로 새 비밀번호 설정. 세부 규칙은 PasswordPolicy(2종류 이상·72바이트 이하 등). */
    public record Confirm(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
