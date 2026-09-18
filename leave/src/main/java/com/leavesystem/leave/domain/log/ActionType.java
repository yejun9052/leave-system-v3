package com.leavesystem.leave.domain.log;

/**
 * 이벤트로그 행위 코드. 설계 문서 9.3의 30종.
 *
 * <p>로그인·로그아웃은 현재 제외한다. 배치 성공은 매번 남기지 않고
 * 부여·소멸 업무 결과만 사원별로 기록한다.
 */
public enum ActionType {

    // 신청
    APPLY,
    PROXY_APPLY,
    LEADER_CONFIRM,
    APPROVE,
    REJECT,
    SELF_CANCEL,
    CANCEL_REQUEST,
    CANCEL_APPROVE,
    CANCEL_REJECT,

    // 연차 변동
    GRANT,
    EXPIRE,
    ADJUST,
    EXCEL_IMPORT,

    // 사원
    EMPLOYEE_CREATE,
    EMPLOYEE_UPDATE,
    EMPLOYEE_DEACTIVATE,
    DEPARTMENT_CHANGE,
    ROLE_CHANGE,

    // 설정
    POLICY_CHANGE,
    HOLIDAY_ADD,
    HOLIDAY_DELETE,
    LEAVE_TYPE_CHANGE,

    // 공지
    NOTICE_CREATE,
    NOTICE_UPDATE,
    NOTICE_DELETE,

    // 알림
    NOTIFY_SENT,
    MAIL_SENT,
    MAIL_FAILED,
    NOTIFY_READ,

    // 배치
    BATCH_FAILED
}
