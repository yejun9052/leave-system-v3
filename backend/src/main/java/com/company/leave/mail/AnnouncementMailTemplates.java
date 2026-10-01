package com.company.leave.mail;

import com.company.leave.mail.LeaveMailTemplates.Handler;
import com.company.leave.mail.MailLayout.Content;
import com.company.leave.mail.MailLayout.Row;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 공지 메일 내용: 연차 사용 금지 기간(블랙아웃)과 캘린더 일정의 추가·변경·삭제.
 * 모양은 {@link MailLayout}. 받는 사람이 여럿이라 한 통을 숨은 참조로 보내므로({@link AnnouncementMailService})
 * 표의 수신자 줄에는 이름 대신 받는 범위(예: "재직 중인 전 직원")를 넣는다.
 */
public final class AnnouncementMailTemplates {

    private static final String PREFIX = "[연차관리] ";

    private AnnouncementMailTemplates() {
    }

    public enum Change {
        CREATED("추가"), UPDATED("변경"), DELETED("삭제");

        private final String label;

        Change(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 메일에 쓰는 기간 정보.
     *
     * @param scopeLabel 일정 범위(예: "전체 일정", "QA팀 일정"). 블랙아웃은 null
     */
    public record Schedule(String name, LocalDate start, LocalDate end, String scopeLabel) {

        String period() {
            return start.equals(end) ? start.toString() : start + " ~ " + end;
        }
    }

    public record Mail(String subject, Content content) {

        /** 일반 텍스트 본문. */
        public String body() {
            return content.text();
        }
    }

    /**
     * @param before    변경 전 내용(변경일 때만)
     * @param audience  받는 범위(예: "재직 중인 전 직원")
     */
    public static Mail blackout(Change change, Schedule now, Schedule before, Handler by, String audience,
                                String baseUrl) {
        List<String> intro = switch (change) {
            case CREATED -> List.of("연차 사용 금지 기간이 추가되었습니다.", "이 기간에는 연차를 신청할 수 없습니다.");
            case UPDATED -> List.of("연차 사용 금지 기간이 변경되었습니다.", "이 기간에는 연차를 신청할 수 없습니다.");
            case DELETED -> List.of("연차 사용 금지 기간이 삭제되었습니다.", "이 기간에도 연차를 신청할 수 있습니다.");
        };
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("이름", now.name()));
        rows.add(Row.of("기간", now.period()));
        if (change == Change.UPDATED && before != null) {
            rows.add(Row.of("변경 전 이름", before.name()));
            rows.add(Row.of("변경 전 기간", before.period()));
        }
        rows.add(Row.strong("연차 신청", change == Change.DELETED ? "가능" : "불가"));
        return mail("연차 사용 금지 기간 " + change.label(), now, "연차 사용 금지 기간 " + change.label() + " 안내",
                intro, by, audience, rows, baseUrl);
    }

    /**
     * @param before    변경 전 내용(변경일 때만)
     * @param audience  받는 범위(예: "QA팀 소속 직원")
     */
    public static Mail event(Change change, Schedule now, Schedule before, Handler by, String audience,
                             String baseUrl) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("일정", now.name()));
        rows.add(Row.of("기간", now.period()));
        rows.add(Row.of("범위", now.scopeLabel()));
        if (change == Change.UPDATED && before != null) {
            rows.add(Row.of("변경 전 일정", before.name()));
            rows.add(Row.of("변경 전 기간", before.period()));
            rows.add(Row.of("변경 전 범위", before.scopeLabel()));
        }
        rows.add(Row.strong("처리 상태", change.label() + "됨"));
        return mail("일정 " + change.label(), now, "일정 " + change.label() + " 안내",
                List.of(now.scopeLabel() + "이 " + change.label() + "되었습니다."), by, audience, rows, baseUrl);
    }

    private static Mail mail(String what, Schedule now, String title, List<String> intro, Handler by, String audience,
                             List<Row> rows, String baseUrl) {
        Content content = new Content(title, intro,
                List.of(Row.of("수신자", audience), Row.of("처리자", by.display())), rows,
                "캘린더 보기", MailLayout.url(baseUrl, "/calendar"));
        return new Mail(PREFIX + what + " - " + now.name() + " (" + now.period() + ")", content);
    }
}
