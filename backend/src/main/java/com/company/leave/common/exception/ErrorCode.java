package com.company.leave.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전역 오류 코드.
 */
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // 인증/인가
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    TOO_MANY_LOGIN_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS,
            "로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 변경한 뒤 이용할 수 있습니다."),
    HOLIDAY_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "공휴일 동기화에 실패했습니다. 잠시 후 다시 시도하세요."),
    // 400: 로그인 없이 쓰는 재설정 화면에서 401(→ 로그인 화면 이동)과 구분
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST,
            "링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 요청하세요."),

    // 사용자
    EMPLOYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // 부서
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."),
    DEPARTMENT_HAS_CHILDREN(HttpStatus.CONFLICT, "하위 부서가 있어 삭제할 수 없습니다."),
    DEPARTMENT_HAS_MEMBERS(HttpStatus.CONFLICT, "소속 사용자가 있어 삭제할 수 없습니다."),
    DEPARTMENT_CYCLE(HttpStatus.BAD_REQUEST, "부서를 자신의 하위로 이동할 수 없습니다."),

    // 정책
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "연차 정책을 찾을 수 없습니다."),

    // 휴가
    LEAVE_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "휴가 종류를 찾을 수 없습니다."),
    LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "휴가 신청을 찾을 수 없습니다."),
    LEAVE_BALANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "연차 잔액 정보를 찾을 수 없습니다."),
    INSUFFICIENT_LEAVE_BALANCE(HttpStatus.CONFLICT, "잔여 연차가 부족합니다."),
    LEAVE_DATE_OVERLAP(HttpStatus.CONFLICT, "이미 신청된 기간과 겹칩니다."),
    LEAVE_INVALID_PERIOD(HttpStatus.BAD_REQUEST, "휴가 기간이 올바르지 않습니다."),
    LEAVE_NOT_PENDING(HttpStatus.CONFLICT, "대기 상태의 신청만 처리할 수 있습니다."),
    LEAVE_ALREADY_STARTED(HttpStatus.CONFLICT, "이미 시작된 휴가는 취소할 수 없습니다."),
    LEAVE_NO_APPROVAL_PERMISSION(HttpStatus.FORBIDDEN, "해당 신청을 결재할 권한이 없습니다."),
    LEAVE_MIN_ADVANCE(HttpStatus.BAD_REQUEST, "사전 신청 기한을 지켜야 합니다."),
    LEAVE_MAX_CONSECUTIVE(HttpStatus.BAD_REQUEST, "최대 연속 사용일을 초과했습니다."),
    LEAVE_BLACKOUT(HttpStatus.CONFLICT, "연차 사용이 제한된 기간입니다."),
    LEAVE_TEAM_LIMIT(HttpStatus.CONFLICT, "같은 기간 팀 내 휴가 인원 제한을 초과했습니다."),

    // 캘린더
    CALENDAR_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "캘린더 일정을 찾을 수 없습니다."),

    // 라이선스
    LICENSE_INVALID(HttpStatus.FORBIDDEN, "유효한 라이선스가 없습니다. 관리자에게 문의하세요."),
    LICENSE_USER_LIMIT(HttpStatus.CONFLICT, "라이선스 최대 사용자 수에 도달했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return message;
    }
}
