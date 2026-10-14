package com.company.leave.security;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.ErrorCode;
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
import tools.jackson.databind.ObjectMapper;

/**
 * 경로에 영문·숫자·{@code -._~} 를 퍼센트 인코딩해 보낸 요청(예: {@code /%61pi/employees}, {@code /%65xport})은 400.
 * 이 글자들은 인코딩할 이유가 없어 화면·브라우저는 그대로 보낸다. 인코딩해 보내는 것은 경로 검사를 피하려는 시도뿐이다.
 * 경로 검사는 디코딩한 값({@link com.company.leave.common.web.RequestPaths})으로 하므로 이 필터는 한 겹 더 막는 것이다.
 * 한글 등 다른 문자의 인코딩과 쿼리 문자열은 그대로 둔다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class EncodedPathRejectFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    public EncodedPathRejectFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (hasEncodedUnreserved(request.getRequestURI())) {
            response.setStatus(ErrorCode.INVALID_INPUT.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(ErrorCode.INVALID_INPUT.name(), "올바르지 않은 요청 주소입니다."));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 디코딩 전 경로(쿼리 제외)에 인코딩할 필요가 없는 글자(RFC 3986 unreserved)의 %XX 가 있는지 */
    static boolean hasEncodedUnreserved(String rawPath) {
        if (rawPath == null) {
            return false;
        }
        for (int i = rawPath.indexOf('%'); i >= 0 && i + 2 < rawPath.length(); i = rawPath.indexOf('%', i + 1)) {
            int hi = Character.digit(rawPath.charAt(i + 1), 16);
            int lo = Character.digit(rawPath.charAt(i + 2), 16);
            if (hi >= 0 && lo >= 0 && unreserved((char) (hi * 16 + lo))) {
                return true;
            }
        }
        return false;
    }

    private static boolean unreserved(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}
