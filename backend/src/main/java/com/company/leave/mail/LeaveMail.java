package com.company.leave.mail;

import java.util.List;

/**
 * 휴가 결재 메일 발송 요청 이벤트. 발행한 트랜잭션이 커밋된 뒤에만 발송된다({@link LeaveMailService}).
 *
 * @param to        받는 사람 주소. 표의 수신자 줄이 사람마다 달라 보통 한 명씩 보낸다
 * @param text      일반 텍스트 본문(HTML 을 못 여는 메일 앱용)
 * @param html      HTML 본문
 * @param messageId 이 메일의 Message-ID(대화의 첫 메일일 때). null 이면 자동 생성
 * @param inReplyTo 답장 대상 Message-ID(같은 대화로 묶을 때). null 이면 새 대화
 */
public record LeaveMail(List<String> to, String subject, String text, String html, String messageId,
                        String inReplyTo) {
}
