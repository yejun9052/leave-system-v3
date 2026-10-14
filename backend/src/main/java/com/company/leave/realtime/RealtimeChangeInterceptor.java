package com.company.leave.realtime;

import com.company.leave.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 데이터를 바꾸는 API(POST·PUT·PATCH·DELETE)가 성공하면 실시간 신호를 보낸다.
 * 서비스의 트랜잭션은 컨트롤러가 돌아오기 전에 커밋되므로, 신호를 받은 화면은 바뀐 데이터를 읽는다.
 * <ul>
 *   <li>나만 보는 데이터(알림 읽음, 비밀번호 변경): 내 다른 탭·기기에만</li>
 *   <li>그 외: 모두에게</li>
 * </ul>
 * 요청 헤더 {@value #CLIENT_ID_HEADER}(탭 ID)를 신호에 실어, 바꾼 탭은 신호를 무시한다(이미 스스로 다시 불러옴).
 */
public class RealtimeChangeInterceptor implements HandlerInterceptor {

    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final List<String> PERSONAL = List.of("/api/notifications", "/api/employees/me/password");

    private final RealtimeHub hub;

    public RealtimeChangeInterceptor(RealtimeHub hub) {
        this.hub = hub;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (ex != null || response.getStatus() >= 400 || !MUTATING.contains(request.getMethod())) {
            return;
        }
        String origin = request.getHeader(CLIENT_ID_HEADER);
        String path = request.getRequestURI();
        if (PERSONAL.stream().anyMatch(path::startsWith)) {
            Long me = currentEmployeeIdOrNull();
            if (me != null) {
                hub.sendTo(me, origin);
            }
            return;
        }
        hub.broadcast(origin);
    }

    private static Long currentEmployeeIdOrNull() {
        try {
            return SecurityUtils.currentEmployeeId();
        } catch (RuntimeException notLoggedIn) {
            return null;
        }
    }
}
