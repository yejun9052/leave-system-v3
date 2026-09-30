package com.company.leave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email 직원은 회사 이메일, 관리 전용 계정은 아이디(admin). 형식 검사 없이 계정 조회로만 판단한다.
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank String password) {
}
