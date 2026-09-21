package com.leavesystem.leave.domain.employee;

/** 사원 역할. 설계 문서 3.1 역할별 권한. */
public enum Role {

    /** 사원: 본인 신청·조회. */
    MEMBER,

    /** 팀장: 사원 기능 + 자기 팀 1차 확인·반려. */
    LEADER,

    /** 휴가관리자: 최종 승인·연차 조정 및 시스템 관리. */
    HR_ADMIN,

    /** 시스템관리자: 계정·부서·운영 설정 관리. */
    SYS_ADMIN,

    /** V01 기존 데이터 호환용. 신규 관리자는 HR_ADMIN / SYS_ADMIN을 사용한다. */
    ADMIN
}
