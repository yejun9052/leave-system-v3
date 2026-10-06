package com.company.leave.mail;

import com.company.leave.mail.MailLayout.Content;
import com.company.leave.mail.MailLayout.Row;
import java.util.ArrayList;
import java.util.List;

/**
 * 휴가 결재 메일 내용. 모양은 {@link MailLayout}(제목 → 안내 문장 → 표 → 바로가기, HTML + 일반 텍스트).
 *
 * <p>결재는 한 번(팀장·인사관리자·시스템 관리자 중 한 명)으로 확정된다. 신청 건 하나에 대화(Thread)가 둘 있다.
 * 각 대화의 첫 메일은 신청 번호로 정한 Message-ID 를 쓰고, 이후 메일은 그 ID 에 답장(RE: 같은 제목)으로 붙는다.
 * <ul>
 *   <li>APPLICANT: 신청 접수(또는 인사관리자 강제 등록) → 승인·반려·취소 결과.
 *       담당 팀장이 아닌 사람이 처리한 결과는 담당 팀장을 참조(CC)로 걸어 한 통으로 보낸다</li>
 *   <li>APPROVER: 결재 요청 → 신청 철회·취소 요청</li>
 * </ul>
 * 제목은 신청 건마다 하나(받는 사람과 대화에 상관없이 같음)이고, 답장의 References 에는 두 대화의 첫 메일 ID 를 모두 넣는다.
 * Gmail 은 제목과 References 가 맞아야 같은 대화로 묶으므로, 참조로 받은 팀장의 메일함에서도 결재 요청 대화에 이어 붙는다.
 * 첫 메일이 보내지지 않은 대화에 답장하면(예: 팀장이 없어 결재 요청이 인사관리자에게 감) 새 대화처럼 보일 뿐 문제는 없다.
 * 표의 수신자 줄은 받는 사람마다 따로 보낼 때 넣는다({@link Content#withRecipient}).
 */
public final class LeaveMailTemplates {

    private static final String PREFIX = "[연차관리] ";
    private static final String MY_LEAVES = "내 휴가 확인하기";
    private static final String APPROVALS = "결재함 바로가기";
    private static final String CALENDAR = "캘린더 보기";
    private static final String DONE = "아래 휴가 내역은 승인 완료 상태입니다.";
    private static final String RESTORED = "차감된 연차는 돌아갑니다.";

    private LeaveMailTemplates() {
    }

    public enum Thread { APPLICANT, APPROVER }

    /**
     * 메일에 쓰는 신청 정보.
     *
     * @param createdEpochMillis 신청 시각. 신청 번호와 함께 Message-ID 에 넣어 DB 를 초기화해도 대화가 섞이지 않게 한다
     * @param leaveLabel         휴가 종류(경조사 규정 포함, 예: "경조사(본인 결혼)")
     * @param period             기간(예: "2026-10-14 ~ 2026-10-15", 하루면 날짜 하나)
     * @param amount             일수·시간(예: "2일", "0.5일", "2시간")
     */
    public record Info(long requestId, long createdEpochMillis, String applicantName, String departmentName,
                       String leaveLabel, String period, String amount, String reason) {
    }

    /** 대화 첫 메일이면 messageId, 답장이면 inReplyTo 와 references(두 대화의 첫 메일 ID)가 채워진다. */
    public record Mail(String subject, Content content, String messageId, String inReplyTo, List<String> references) {

        /** 일반 텍스트 본문(수신자 줄 없이). */
        public String body() {
            return content.text();
        }
    }

    /**
     * 처리한 사람. 메일 표에 "처리자: 홍길동 (팀장)" 으로 들어간다.
     *
     * @param role 처리한 자격(예: "팀장", "인사관리자", "시스템 관리자", "신청자")
     */
    public record Handler(String role, String name) {

        /** 앱 알림에 쓰는 이름: "팀장 홍길동님" */
        public String title() {
            return role + " " + name + "님";
        }

        /** 메일 안내 문장에 쓰는 이름: "홍길동님" (자격은 표의 처리자 줄에 나온다) */
        public String honorific() {
            return name + "님";
        }

        /** 표에 쓰는 이름: "홍길동 (팀장)" */
        public String display() {
            return name + " (" + role + ")";
        }
    }

    // --- 신청자 대화 ---

    /** @param route 결재 경로 안내(예: "팀장 홍길동님 승인(인사관리자도 승인 가능)") */
    public static Mail submitted(Info info, String domain, String baseUrl, String route) {
        Content content = content("휴가 신청 접수 안내",
                List.of("휴가 신청이 접수되었습니다.", "결재가 끝나면 결과를 메일로 알려 드립니다."),
                null, info, List.of(Row.of("결재", route)), "결재 대기", MY_LEAVES, baseUrl, "/my-leaves");
        return root(info, Thread.APPLICANT, domain, content);
    }

    /**
     * 인사관리자가 대신 등록(바로 승인됨). 신청 접수 대신 이 메일이 대화의 첫 메일이 된다.
     * 담당 팀장이 참조로 함께 받을 수 있어 문장은 신청자 이름으로 쓴다(이하 결과 메일 모두 같음).
     */
    public static Mail registered(Info info, String domain, String baseUrl, Handler by) {
        Content content = content("휴가 등록 안내",
                List.of(by.honorific() + "이 " + info.applicantName() + "님의 휴가를 등록했습니다.", DONE),
                by, info, List.of(), "승인 완료", MY_LEAVES, baseUrl, "/my-leaves");
        return root(info, Thread.APPLICANT, domain, content);
    }

    /** @param self 신청자 본인의 자가 승인이면 true */
    public static Mail approved(Info info, String domain, String baseUrl, Handler by, boolean self) {
        String headline = info.applicantName() + (self ? "님의 휴가가 자가 승인으로 확정되었습니다." : "님의 휴가가 승인되었습니다.");
        return applicantReply(info, domain, baseUrl, "휴가 승인 안내", List.of(headline, DONE), by, List.of(), "승인 완료");
    }

    public static Mail rejected(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, "휴가 반려 안내",
                List.of(by.honorific() + "이 휴가 신청을 반려했습니다."), by,
                List.of(Row.of("반려 사유", orNone(reason))), "반려");
    }

    /** 취소 요청 승인(또는 인사관리자의 취소 요청 건 확정). */
    public static Mail cancelApproved(Info info, String domain, String baseUrl, Handler by) {
        return applicantReply(info, domain, baseUrl, "휴가 취소 승인 안내",
                List.of(info.applicantName() + "님의 휴가 취소 요청이 승인되어 휴가가 취소되었습니다.", RESTORED), by,
                List.of(), "취소 완료");
    }

    public static Mail cancelRejected(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, "휴가 취소 반려 안내",
                List.of(by.honorific() + "이 휴가 취소 요청을 반려했습니다.", "휴가는 승인 상태로 유지됩니다."), by,
                List.of(Row.of("반려 사유", orNone(reason))), "승인 유지");
    }

    /** 결재 대기 중인 신청을 인사관리자가 취소. */
    public static Mail cancelledByHr(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, "휴가 신청 취소 안내",
                List.of(by.honorific() + "이 휴가 신청을 취소했습니다."), by,
                reason != null && !reason.isBlank() ? List.of(Row.of("취소 사유", reason)) : List.of(), "신청 취소");
    }

    /** 승인된 휴가를 인사관리자가 강제 취소(시작 후 포함). */
    public static Mail forceCancelled(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, "휴가 취소 안내",
                List.of(by.honorific() + "이 " + info.applicantName() + "님의 승인된 휴가를 취소했습니다.", RESTORED), by,
                List.of(Row.of("취소 사유", orNone(reason))), "취소 완료");
    }

    // --- 결재자 대화 ---

    public static Mail approvalRequest(Info info, String domain, String baseUrl) {
        Content content = content("휴가 결재 요청",
                List.of(info.applicantName() + "님이 휴가를 신청했습니다.", "결재를 부탁드립니다."),
                null, info, List.of(), "결재 대기", APPROVALS, baseUrl, "/approvals");
        return root(info, Thread.APPROVER, domain, content);
    }

    public static Mail withdrawn(Info info, String domain, String baseUrl) {
        Content content = content("휴가 신청 철회 안내",
                List.of(info.applicantName() + "님이 결재 대기 중이던 휴가 신청을 취소했습니다.", "더 결재하지 않아도 됩니다."),
                new Handler("신청자", info.applicantName()), info, List.of(), "신청 취소",
                APPROVALS, baseUrl, "/approvals");
        return reply(info, Thread.APPROVER, domain, content);
    }

    public static Mail cancelRequested(Info info, String domain, String baseUrl, String cancelReason) {
        Content content = content("휴가 취소 결재 요청",
                List.of(info.applicantName() + "님이 승인된 휴가의 취소를 요청했습니다.", "결재를 부탁드립니다."),
                null, info, List.of(Row.of("취소 사유", orNone(cancelReason))), "취소 요청",
                APPROVALS, baseUrl, "/approvals");
        return reply(info, Thread.APPROVER, domain, content);
    }

    // --- 담당 팀장에게만 ---

    /**
     * 신청자 본인이 처리한 확정 취소(인사관리자의 본인 휴가 취소 등)를 담당 팀장에게 알린다. 신청자에게 갈 메일이 없어
     * 참조로 걸 수 없을 때만 쓴다. 결재 요청 대화에 답장으로 붙는다.
     */
    public static Mail leadCancelledInfo(Info info, String domain, String baseUrl, Handler by) {
        Content content = content("팀원 휴가 취소 안내", List.of(info.applicantName() + "님의 승인된 휴가가 취소되었습니다."),
                by, info, List.of(), "취소 완료", CALENDAR, baseUrl, "/calendar");
        return reply(info, Thread.APPROVER, domain, content);
    }

    /** 담당 팀장이 참조로 함께 받는 메일에 팀장용 바로가기(캘린더)를 덧붙인다. */
    public static Content withLeadLink(Content content, String baseUrl) {
        return content.withLink(CALENDAR, MailLayout.url(baseUrl, "/calendar"));
    }

    // --- 대화(Thread) 공통 ---

    /**
     * 신청 건의 메일 제목(첫 메일). 답장은 "RE: " 를 붙인다. 받는 사람·대화에 상관없이 같아서, 참조로 함께 받은 메일도
     * 각자의 기존 대화에 붙는다. 예: "[연차관리] 휴가 - 홍길동 연차 2026-10-14"
     */
    public static String rootSubject(Info info) {
        return PREFIX + "휴가 - " + info.applicantName() + " " + info.leaveLabel() + " " + info.period();
    }

    /** 대화의 첫 메일 Message-ID: &lt;leave-{신청번호}-{신청시각}.{대화}@{도메인}&gt; */
    public static String threadId(Info info, Thread thread, String domain) {
        return "<leave-" + info.requestId() + "-" + info.createdEpochMillis() + "."
                + thread.name().toLowerCase() + "@" + domain + ">";
    }

    /** 보내는 주소의 도메인(Message-ID 용). 없으면 annual-leave.local */
    public static String domainOf(String from) {
        if (from == null) {
            return "annual-leave.local";
        }
        String address = from.contains("<") ? from.substring(from.indexOf('<') + 1).replace(">", "") : from;
        int at = address.lastIndexOf('@');
        String domain = at >= 0 ? address.substring(at + 1).trim() : "";
        return domain.isEmpty() ? "annual-leave.local" : domain;
    }

    private static Mail applicantReply(Info info, String domain, String baseUrl, String title, List<String> intro,
                                       Handler by, List<Row> extra, String status) {
        return reply(info, Thread.APPLICANT, domain,
                content(title, intro, by, info, extra, status, MY_LEAVES, baseUrl, "/my-leaves"));
    }

    /** 표: [처리자] | 신청자·소속·휴가 종류·기간·사유 + 추가 줄(반려 사유 등) + 처리 상태. */
    private static Content content(String title, List<String> intro, Handler by, Info info, List<Row> extra,
                                   String status, String linkLabel, String baseUrl, String path) {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("신청자", info.applicantName()));
        rows.add(Row.of("소속", info.departmentName() != null ? info.departmentName() : "-"));
        rows.add(Row.of("휴가 종류", info.leaveLabel()));
        rows.add(Row.of("휴가 기간", info.period() + " (" + info.amount() + ")"));
        rows.add(Row.of("신청 사유", orNone(info.reason())));
        rows.addAll(extra);
        rows.add(Row.strong("처리 상태", status));
        List<Row> head = by != null ? List.of(Row.of("처리자", by.display())) : List.of();
        return new Content(title, intro, head, rows, linkLabel, MailLayout.url(baseUrl, path));
    }

    private static Mail root(Info info, Thread thread, String domain, Content content) {
        return new Mail(rootSubject(info), content, threadId(info, thread, domain), null, List.of());
    }

    /** 답장: thread 대화의 첫 메일에 붙고, References 에는 두 대화의 첫 메일 ID 를 모두 넣는다. */
    private static Mail reply(Info info, Thread thread, String domain, Content content) {
        return new Mail("RE: " + rootSubject(info), content, null, threadId(info, thread, domain),
                List.of(threadId(info, Thread.APPLICANT, domain), threadId(info, Thread.APPROVER, domain)));
    }

    private static String orNone(String text) {
        return text == null || text.isBlank() ? "미기재" : text;
    }
}
