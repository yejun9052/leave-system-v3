package com.company.leave.backup;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 점검 모드(복원 중) 필터. 로그인 세션을 읽기 전(세션 필터보다 먼저)에 실행한다. 복원 중에는 로그인 정보가 든 표도
 * 바뀌고 있어 세션을 읽으면 복원이 끝날 때까지 응답이 멈추기 때문이다.
 * <ul>
 *   <li>GET /api/backups/status: 여기서 바로 답한다(로그인 없이, DB 를 읽지 않음). 점검 중인지 여부만 돌려준다</li>
 *   <li>점검 중: 그 밖의 모든 API 는 503 MAINTENANCE. 화면 파일(/api 밖)은 그대로 보낸다(점검 안내 화면용)</li>
 *   <li>평소: 처리 중인 API 요청 수를 센다(복원 전에 끝나기를 기다리는 데 씀)</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class MaintenanceFilter extends OncePerRequestFilter {

    static final String STATUS_PATH = "/api/backups/status";

    private final MaintenanceMode maintenance;
    private final ObjectMapper objectMapper;

    public MaintenanceFilter(MaintenanceMode maintenance, ObjectMapper objectMapper) {
        this.maintenance = maintenance;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        if (STATUS_PATH.equals(path) && "GET".equals(request.getMethod())) {
            write(response, HttpStatus.OK, ApiResponse.ok(Map.of("maintenance", maintenance.isActive())));
            return;
        }
        if (maintenance.isActive()) {
            write(response, ErrorCode.MAINTENANCE.status(),
                    ApiResponse.error(ErrorCode.MAINTENANCE.name(), ErrorCode.MAINTENANCE.defaultMessage()));
            return;
        }
        maintenance.requestStarted();
        try {
            chain.doFilter(request, response);
        } finally {
            maintenance.requestFinished();
        }
    }

    private void write(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
