package com.leavesystem.leave.domain.notice;

/** 공지 유형. 설계 문서 9.2. */
public enum NoticeType {

    /** 관리자가 직접 작성한 공지. */
    MANUAL,

    /** 정책 변경으로 자동 생성된 공지. 초안({@code published=false}) 으로 저장한다. */
    SYSTEM
}
