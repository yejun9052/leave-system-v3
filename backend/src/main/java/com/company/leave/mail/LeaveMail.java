package com.company.leave.mail;

import java.util.List;

/**
 * 휴가 결재 메일 발송 요청 이벤트. 발행한 트랜잭션이 커밋된 뒤에만 발송된다({@link LeaveMailService}).
 *
 * @param to        받는 사람 주소(한 통에 함께 보낸다)
 * @param messageId 이 메일의 Message-ID(대화의 첫 메일일 때). null 이면 자동 생성
 * @param inReplyTo 답장 대상 Message-ID(같은 대화로 묶을 때). null 이면 새 대화
 */
public record LeaveMail(List<String> to, String subject, String body, String messageId, String inReplyTo) {
}
