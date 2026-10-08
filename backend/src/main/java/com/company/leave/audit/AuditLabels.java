package com.company.leave.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이벤트 로그의 동작·대상 한글 표시 이름. 화면 표시(응답의 actionLabel·entityLabel)와 검색이 같은 이름을 쓴다.
 * 동작 코드는 AuditAspect(HTTP 메서드·행위 동사)와 서비스의 직접 기록(LOGIN·self_approve 등)에서 온다.
 */
public final class AuditLabels {

    private AuditLabels() {
    }

    public static final Map<String, String> ACTIONS = new LinkedHashMap<>();
    public static final Map<String, String> RESOURCES = new LinkedHashMap<>();

    static {
        ACTIONS.put("POST", "생성");
        ACTIONS.put("PUT", "수정");
        ACTIONS.put("PATCH", "변경");
        ACTIONS.put("DELETE", "삭제");
        ACTIONS.put("LOGIN", "로그인");
        ACTIONS.put("LOGOUT", "로그아웃");
        ACTIONS.put("CALL", "실행");
        ACTIONS.put("approve", "승인");
        ACTIONS.put("reject", "반려");
        ACTIONS.put("cancel", "취소 요청");
        ACTIONS.put("cancel_approve", "취소 승인");
        ACTIONS.put("cancel_reject", "취소 반려");
        ACTIONS.put("self_approve", "자가 승인");
        ACTIONS.put("force_cancel", "강제 취소");
        ACTIONS.put("register", "강제 등록");
        ACTIONS.put("move", "이동");
        ACTIONS.put("reactivate", "복원");
        ACTIONS.put("password", "비밀번호 변경");
        ACTIONS.put("grant", "연차 부여");
        ACTIONS.put("run", "촉진 발송"); // 예전 일괄 촉진(/api/leave/promotion/run, 삭제됨) 기록 표시용
        ACTIONS.put("send", "촉진 안내 발송");
        ACTIONS.put("import", "일괄 등록");
        ACTIONS.put("export", "내보내기");
        ACTIONS.put("download", "내려받기");
        ACTIONS.put("restore", "데이터 복원");
        ACTIONS.put("read-all", "알림 읽음");

        RESOURCES.put("auth", "인증");
        RESOURCES.put("employees", "사용자");
        RESOURCES.put("departments", "부서");
        RESOURCES.put("leave-requests", "휴가 신청");
        RESOURCES.put("leave-types", "휴가 종류");
        RESOURCES.put("policy", "정책");
        RESOURCES.put("calendar", "캘린더");
        RESOURCES.put("leave", "연차 운영");
        RESOURCES.put("notifications", "알림");
        RESOURCES.put("reports", "리포트");
        RESOURCES.put("backups", "백업");
    }

    public static String action(String code) {
        return ACTIONS.getOrDefault(code, code);
    }

    public static String resource(String code) {
        return code == null ? null : RESOURCES.getOrDefault(code, code);
    }
}
