package com.leavesystem.leave.domain.auth;

import com.leavesystem.leave.common.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 개발용 로그인 결과 및 세션 유지 확인 API. */
@RestController
@Profile("dev & !prod")
public class DevAuthController {

    @GetMapping("/api/auth/me")
    public ApiResponse<CurrentUser> me(Authentication authentication) {
        return ApiResponse.success(new CurrentUser(authentication.getName(),
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority()).toList()));
    }

    public record CurrentUser(String email, List<String> roles) {
    }
}
