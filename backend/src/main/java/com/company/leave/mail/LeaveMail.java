package com.company.leave.mail;

import java.util.List;

/**
 * 휴가 결재 메일 발송 요청 이벤트. 발행한 트랜잭션이 커밋된 뒤에만 발송된다({@link LeaveMailService}).
 *
 * @param to         받는 사람 주소. 표의 수신자 줄이 사람마다 달라 보통 한 명씩 보낸다
 * @param cc         참조 주소(신청자에게 보내면서 담당 팀장을 참조로 걸 때). 보통 비어 있다
 * @param text       일반 텍스트 본문(HTML 을 못 여는 메일 앱용)
 * @param html       HTML 본문
 * @param messageId  이 메일의 Message-ID(대화의 첫 메일일 때). null 이면 자동 생성
 * @param inReplyTo  답장 대상 Message-ID(같은 대화로 묶을 때). null 이면 새 대화
 * @param references References 헤더에 넣을 Message-ID 들(받는 사람마다 대화가 달라도 각자의 대화에 붙게).
 *                   비어 있으면 inReplyTo 만 넣는다
 */
public record LeaveMail(List<String> to, List<String> cc, String subject, String text, String html,
                        String messageId, String inReplyTo, List<String> references) {

    /** 참조 없이 한 사람(또는 여럿)에게. References 는 inReplyTo 하나. */
    public LeaveMail(List<String> to, String subject, String text, String html, String messageId, String inReplyTo) {
        this(to, List.of(), subject, text, html, messageId, inReplyTo, List.of());
    }
}
