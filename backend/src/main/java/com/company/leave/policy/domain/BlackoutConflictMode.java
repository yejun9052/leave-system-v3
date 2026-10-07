package com.company.leave.policy.domain;

/**
 * 연차 사용 금지 기간을 등록하거나 기간을 늘려 수정할 때, 겹치는 기존 휴가 처리 방식.
 * 결재 대기 휴가는 두 방식 모두 자동 반려한다.
 */
public enum BlackoutConflictMode {
    /** 승인된 휴가(취소 요청 중 포함)는 그대로 둔다. 기본값 */
    KEEP_APPROVED,
    /** 승인된 휴가(취소 요청 중 포함)도 자동 취소하고 연차를 돌려준다 */
    CANCEL_ALL
}
