package com.company.leave.mail;

import com.company.leave.mail.LeaveMailTemplates.Handler;
import java.time.LocalDate;

/**
 * 공지 메일 본문(일반 텍스트): 연차 사용 금지 기간(블랙아웃)과 캘린더 일정의 추가·변경·삭제.
 * 받는 사람이 여럿이라 한 통을 숨은 참조로 보낸다({@link AnnouncementMailService}).
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

    public record Mail(String subject, String body) {
    }

    /** @param before 변경 전 내용(변경일 때만) */
    public static Mail blackout(Change change, Schedule now, Schedule before, Handler by, String baseUrl) {
        String headline = switch (change) {
            case CREATED -> "연차 사용 금지 기간이 추가되었습니다. 이 기간에는 연차를 신청할 수 없습니다.";
            case UPDATED -> "연차 사용 금지 기간이 변경되었습니다. 이 기간에는 연차를 신청할 수 없습니다.";
            case DELETED -> "연차 사용 금지 기간이 삭제되었습니다. 이 기간에도 연차를 신청할 수 있습니다.";
        };
        return mail("연차 사용 금지 기간 " + change.label(), change, now, before, by, headline, baseUrl);
    }

    /** @param before 변경 전 내용(변경일 때만) */
    public static Mail event(Change change, Schedule now, Schedule before, Handler by, String baseUrl) {
        return mail("일정 " + change.label(), change, now, before, by,
                now.scopeLabel() + "이 " + change.label() + "되었습니다.", baseUrl);
    }

    private static Mail mail(String what, Change change, Schedule now, Schedule before, Handler by, String headline,
                             String baseUrl) {
        StringBuilder body = new StringBuilder(headline).append("\n\n")
                .append(by.line())
                .append("이름: ").append(now.name()).append('\n')
                .append("기간: ").append(now.period()).append('\n');
        if (now.scopeLabel() != null) {
            body.append("범위: ").append(now.scopeLabel()).append('\n');
        }
        if (change == Change.UPDATED && before != null) {
            body.append("\n변경 전\n")
                    .append("이름: ").append(before.name()).append('\n')
                    .append("기간: ").append(before.period()).append('\n');
            if (before.scopeLabel() != null) {
                body.append("범위: ").append(before.scopeLabel()).append('\n');
            }
        }
        body.append("\n캘린더: ").append(url(baseUrl, "/calendar")).append('\n');
        return new Mail(PREFIX + what + " - " + now.name() + " (" + now.period() + ")", body.toString());
    }

    private static String url(String baseUrl, String path) {
        String base = baseUrl == null ? "" : baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
