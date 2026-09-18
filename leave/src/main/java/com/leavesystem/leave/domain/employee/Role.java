package com.leavesystem.leave.domain.employee;

/** 사원 역할. 설계 문서 3.1 역할별 권한. */
public enum Role {

    /** 사원: 본인 신청·조회. */
    MEMBER,

    /** 팀장: 사원 기능 + 자기 팀 1차 확인·반려. */
    LEADER,

    /** 관리자: 전체 조회·최종 승인·설정 관리. */
    ADMIN
}
