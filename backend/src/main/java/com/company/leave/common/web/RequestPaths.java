package com.company.leave.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * 요청 경로를 판단할 때 쓰는 값. 필터·감사·실시간 신호가 "어느 API 인지" 볼 때는 반드시 이것을 쓴다.
 * <p>{@code getRequestURI()} 는 디코딩 전 값이라 {@code /%61pi/employees} 처럼 글자 하나만 인코딩해도
 * "/api/" 로 시작하지 않는 것으로 보인다. 반면 Spring Security·MVC 는 디코딩한 경로로 맞추므로
 * 그대로 두면 검사만 건너뛰고 요청은 처리된다. 여기서는 디코딩하고 {@code ;} 뒤 값을 지운,
 * 컨텍스트 경로를 뺀 경로를 돌려준다(MVC 가 맞추는 값과 같다).
 */
public final class RequestPaths {

    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private RequestPaths() {
    }

    /** 예: {@code /%61pi/employees} → {@code /api/employees} */
    public static String of(HttpServletRequest request) {
        return PATHS.getPathWithinApplication(request);
    }
}
