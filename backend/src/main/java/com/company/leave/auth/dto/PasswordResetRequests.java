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

    /** 재설정 링크로 새 비밀번호 설정. 비밀번호 규칙은 기존과 동일(8~72자). */
    public record Confirm(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
