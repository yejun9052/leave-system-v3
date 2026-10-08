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

    /**
     * 복원 완료. 복원 전 상태로 되돌릴 수 있는 백업 파일 이름을 함께 알린다.
     *
     * @param backupAt 복원한 백업의 시점
     */
    public static Mail restored(String actorName, String fileName, LocalDateTime backupAt, String preRestoreFile,
                                LocalDateTime at, String baseUrl) {
        String who = actorName + "님이 " + backupAt.format(TIME) + " 시점 백업으로 복원했습니다.";
        Content content = new Content("데이터 복원 완료",
                List.of(who, "이 시점 이후의 변경 사항은 사라졌고, 모든 사용자는 다시 로그인해야 합니다.",
                        "되돌리려면 정책 › 백업 화면에서 복원 전 백업으로 다시 복원하세요."),
                List.of(Row.of("수신자", AUDIENCE), Row.of("처리자", actorName)),
                List.of(Row.of("복원 시각", at.format(TIME)), Row.strong("복원한 백업", fileName),
                        Row.of("백업 시점", backupAt.format(TIME)), Row.of("복원 전 백업", preRestoreFile)),
                "백업 화면 열기", MailLayout.url(baseUrl, "/admin/policy"));
        return new Mail(PREFIX + "데이터 복원 완료 (" + backupAt.format(TIME) + " 시점)", content);
    }
}
