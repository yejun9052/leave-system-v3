package com.company.leave.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param email      직원은 회사 이메일, 관리 전용 계정은 아이디(admin). 형식 검사 없이 계정 조회로만 판단한다.
 * @param rememberMe 로그인 유지(30일). 빠지면 false
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank String password,
        Boolean rememberMe) {

    /** 예전 화면처럼 rememberMe 를 보내지 않으면(null) 유지하지 않음 */
    public LoginRequest {
        rememberMe = Boolean.TRUE.equals(rememberMe);
    }

    public LoginRequest(String email, String password) {
        this(email, password, false);
    }
}
