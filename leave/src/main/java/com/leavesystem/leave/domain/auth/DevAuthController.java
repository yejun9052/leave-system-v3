package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.common.response.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 세션 로그인 상태와 폼/API 요청용 CSRF 토큰을 제공한다. */
@RestController
public class DevAuthController {

    @GetMapping("/api/auth/me")
    public ApiResponse<CurrentUser> me(Authentication authentication) {
        return ApiResponse.success(new CurrentUser(authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority()).toList()));
    }

    @GetMapping("/api/auth/csrf")
    public ApiResponse<CsrfValue> csrf(CsrfToken token) {
        return ApiResponse.success(new CsrfValue(token.getHeaderName(), token.getParameterName(),
                token.getToken()));
    }

    public record CurrentUser(String loginId, List<String> roles) {
    }

    public record CsrfValue(String headerName, String parameterName, String token) {
    }
}
