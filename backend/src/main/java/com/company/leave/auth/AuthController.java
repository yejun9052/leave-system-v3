package com.company.leave.auth;

import com.company.leave.auth.dto.LoginRequest;
import com.company.leave.auth.dto.MeResponse;
import com.company.leave.auth.dto.PasswordResetRequests;
import com.company.leave.auth.password.PasswordResetService;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @Operation(summary = "로그인", description = "성공 시 세션 쿠키 발급. 이후 CSRF 토큰을 다시 받아야 한다(GET /api/auth/csrf).")
    @PostMapping("/login")
    public ApiResponse<MeResponse> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        return ApiResponse.ok(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "로그아웃", description = "현재 세션 삭제")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        authService.logout(httpRequest, httpResponse);
        return ApiResponse.ok();
    }

    /**
     * CSRF 토큰 쿠키(XSRF-TOKEN) 발급. csrf.spa() 가 모든 응답에서 토큰을 로드해 쿠키를 쓰므로
     * 본문 없이 응답만 주면 된다. 앱 시작·로그인·로그아웃 직후 호출.
     */
    @Operation(summary = "CSRF 토큰 발급")
    @GetMapping("/csrf")
    public ApiResponse<Void> csrf(CsrfToken csrfToken) {
        return ApiResponse.ok();
    }

    @Operation(summary = "비밀번호 재설정 링크 요청",
            description = "등록 여부와 관계없이 항상 같은 응답. 등록된 재직 계정이면 30분 유효 1회용 링크를 메일로 보낸다. "
                    + "요청만으로 기존 비밀번호는 바뀌지 않는다. 이메일당 1시간 3회 제한(초과해도 응답은 동일).")
    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequests.Request request) {
        passwordResetService.requestReset(request.email());
        return ApiResponse.ok();
    }

    @Operation(summary = "비밀번호 재설정", description = "링크의 토큰으로 새 비밀번호 설정. 성공 시 해당 사용자의 모든 세션 종료.")
    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetRequests.Confirm request) {
        passwordResetService.confirm(request.token(), request.newPassword());
        return ApiResponse.ok();
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(authService.me(SecurityUtils.currentEmployeeId()));
    }
}
