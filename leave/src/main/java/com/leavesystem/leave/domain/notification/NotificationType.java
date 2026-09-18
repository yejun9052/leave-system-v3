package com.leavesystem.leave.domain.notification;

/**
 * 앱 알림·메일의 발생 사유. 설계 문서 9.1.
 *
 * <p>공지 게시 알림은 신청과 무관하므로 {@code requestId} 가 비어 있다.
 */
public enum NotificationType {

    /** 신청 접수: 팀장 또는 상위 부서장 수신. */
    REQUEST_SUBMITTED,

    /** 1차 확인 완료: 관리자 수신. */
    LEADER_CONFIRMED,

    /** 최종 승인: 신청자 수신. */
    APPROVED,

    /** 반려: 신청자 수신. */
    REJECTED,

    /** 취소요청 접수: 관리자 수신. */
    CANCEL_REQUESTED,

    /** 취소요청 승인: 신청자 수신. */
    CANCEL_APPROVED,

    /** 취소요청 거절: 신청자 수신. */
    CANCEL_REJECTED,

    /** 관리자 직접 취소: 대상 사원 수신. */
    ADMIN_CANCELED,

    /** 관리자 대리 등록: 대상 사원 수신. */
    PROXY_APPLIED,

    /** 관리자 대리 삭제: 대상 사원 수신. */
    PROXY_DELETED,

    /** 연차 사용 촉진 안내. */
    PROMOTION,

    /** 공지 게시. */
    NOTICE_PUBLISHED
}
