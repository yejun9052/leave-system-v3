package com.company.leave.mail;

import com.company.leave.mail.MailLayout.Content;
import com.company.leave.mail.MailLayout.Row;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 백업·복원 안내 메일(받는 사람: 시스템 관리자·인사관리자). */
public final class BackupMailTemplates {

    private static final String PREFIX = "[연차관리] ";
    private static final String AUDIENCE = "시스템 관리자·인사관리자";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private BackupMailTemplates() {
    }

    public record Mail(String subject, Content content) {
    }

    /** 자동 백업 실패. 보관 중인 백업은 지우지 않았다는 안내를 함께 넣는다. */
    public static Mail autoBackupFailed(LocalDateTime at, String reason, String baseUrl) {
        Content content = new Content("자동 백업 실패",
                List.of("예약된 자동 백업이 실패했습니다. 보관 중인 백업은 지우지 않았습니다.",
                        "정책 › 백업 화면에서 서버 상태를 확인한 뒤 \"지금 백업\"으로 다시 시도해 주세요."),
                List.of(Row.of("수신자", AUDIENCE)),
                List.of(Row.of("시각", at.format(TIME)), Row.of("오류", reason)),
                "백업 화면 열기", MailLayout.url(baseUrl, "/admin/policy"));
        return new Mail(PREFIX + "자동 백업 실패 (" + at.format(TIME) + ")", content);
    }
}
