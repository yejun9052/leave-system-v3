package com.company.leave.mail;

import java.util.Collection;

/**
 * 메일 발송 결과를 이벤트 로그에 남긴다(대상 "메일"). 본문은 남기지 않는다(임시 비밀번호·토큰 보호).
 */
@FunctionalInterface
public interface MailRecorder {

    /** 기록하지 않음(테스트 등). */
    MailRecorder NONE = (kind, to, cc, subject, error) -> { };

    /**
     * @param kind    메일 종류(예: 휴가 메일, 계정 생성, 공지 메일)
     * @param to      받는 사람(공지 메일은 숨은 참조로 받는 사람)
     * @param cc      참조(없으면 빈 목록)
     * @param subject 제목
     * @param error   실패 사유(성공이면 null)
     */
    void record(String kind, Collection<String> to, Collection<String> cc, String subject, String error);
}
