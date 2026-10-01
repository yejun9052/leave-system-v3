package com.company.leave.mail;

import java.util.List;

/**
 * 공지 메일 발송 요청 이벤트(블랙아웃·일정 변경). 발행한 트랜잭션이 커밋된 뒤에만 발송된다({@link AnnouncementMailService}).
 *
 * @param bcc 받는 사람 주소. 서로 주소가 보이지 않게 숨은 참조로 한 통에 보낸다
 */
public record AnnouncementMail(List<String> bcc, String subject, String body) {
}
