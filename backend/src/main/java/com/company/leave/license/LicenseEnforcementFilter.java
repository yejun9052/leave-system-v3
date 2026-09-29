package com.company.leave.license;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.ErrorCode;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 라이선스 집행 필터. 집행이 켜져 있고 라이선스가 유효하지 않으면
 * 라이선스 상태 조회를 제외한 모든 API 를 차단한다.
 * 보안 체인보다 먼저 실행되도록 최우선 순서로 등록한다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    public LicenseEnforcementFilter(LicenseService licenseService, ObjectMapper objectMapper) {
        this.licenseService = licenseService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean apiCall = path.startsWith("/api/");
        boolean whitelisted = path.startsWith("/api/license");

        if (apiCall && !whitelisted && !licenseService.valid()) {
            response.setStatus(ErrorCode.LICENSE_INVALID.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            String reason = licenseService.status().reason();
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(ErrorCode.LICENSE_INVALID.name(),
                            "라이선스가 유효하지 않습니다: " + reason));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
