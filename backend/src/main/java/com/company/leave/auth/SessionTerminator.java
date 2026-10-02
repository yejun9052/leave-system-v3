package com.company.leave.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * 사용자별 서버 세션 강제 종료(퇴사·비밀번호 변경 등).
 * 세션의 principal 인덱스는 이메일(변경 가능) 대신 직원 ID 로 둔다 → {@link #principalName(Long)}.
 */
@Service
public class SessionTerminator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionTerminator(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /** 세션 인덱스(PRINCIPAL_NAME)에 기록하는 값. 로그인 시 세션 속성으로 명시 저장한다. */
    public static String principalName(Long employeeId) {
        return String.valueOf(employeeId);
    }

    /** 해당 사용자의 모든 세션 삭제. */
    public void terminateAll(Long employeeId) {
        terminateAllExcept(employeeId, null);
    }

    /**
     * 현재 세션은 새 ID 로 재발급해 유지하고, 같은 사용자의 다른 세션(다른 기기)은 모두 삭제.
     * 순서 주의: ID 를 먼저 바꾸면 저장소(DB)에는 아직 옛 ID 로 남아 있어 현재 세션까지 지우게 되므로,
     * 현재 ID 기준으로 다른 세션을 먼저 지운 뒤 ID 를 바꾼다.
     */
    public void renewCurrentAndTerminateOthers(Long employeeId, HttpServletRequest request) {
        terminateAllExcept(employeeId, request.getSession().getId());
        request.changeSessionId();
    }

    /** 해당 사용자의 세션 중 keepSessionId 를 제외하고 모두 삭제. */
    public void terminateAllExcept(Long employeeId, String keepSessionId) {
        sessionRepository.findByPrincipalName(principalName(employeeId)).keySet().stream()
                .filter(id -> !id.equals(keepSessionId))
                .forEach(sessionRepository::deleteById);
    }
}
