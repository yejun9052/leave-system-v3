package com.company.leave.mail;

/**
 * 휴가 결재 메일 본문(일반 텍스트). 템플릿 엔진 없이 문자열로 만든다.
 *
 * <p>결재는 한 번(팀장·인사관리자·시스템 관리자 중 한 명)으로 확정된다. 신청 건 하나에 받는 쪽별 대화(Thread)가 셋 있다.
 * 각 대화의 첫 메일은 신청 번호로 정한 Message-ID 를 쓰고, 이후 메일은 그 ID 에 답장(RE: 같은 제목)으로 붙는다.
 * <ul>
 *   <li>APPLICANT: 신청 접수(또는 인사관리자 강제 등록) → 승인·반려·취소 결과</li>
 *   <li>APPROVER: 결재 요청 → 신청 철회·취소 요청</li>
 *   <li>LEAD: 팀원 휴가 승인·등록 안내 → 취소 안내</li>
 * </ul>
 * 첫 메일 제목은 대화마다 고정이라, 답장 제목은 같은 정보로 다시 계산해 "RE: " 를 붙인다.
 * 첫 메일이 보내지지 않은 대화에 답장하면(예: 팀장이 직접 승인해 안내를 받지 않음) 새 대화처럼 보일 뿐 문제는 없다.
 */
public final class LeaveMailTemplates {

    private static final String PREFIX = "[연차관리] ";

    private LeaveMailTemplates() {
    }

    public enum Thread { APPLICANT, APPROVER, LEAD }

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

    /** 대화 첫 메일이면 messageId, 답장이면 inReplyTo 가 채워진다. */
    public record Mail(String subject, String body, String messageId, String inReplyTo) {
    }

    /**
     * 처리한 사람. 결과 메일마다 "처리자: 홍길동 (팀장 · 개발팀)" 줄로 들어간다.
     *
     * @param role 처리한 자격(예: "팀장", "인사관리자", "시스템 관리자", "신청자")
     */
    public record Handler(String role, String name, String departmentName) {

        /** 문장에 쓰는 이름: "팀장 홍길동님" */
        public String title() {
            return role + " " + name + "님";
        }

        String line() {
            return "처리자: " + name + " (" + role + (departmentName != null ? " · " + departmentName : "") + ")\n";
        }
    }

    // --- 신청자 대화 ---

    /** @param route 결재 경로 안내(예: "팀장 홍길동님 승인(인사관리자도 승인 가능)") */
    public static Mail submitted(Info info, String domain, String baseUrl, String route) {
        String body = info.applicantName() + "님, 휴가 신청이 접수되었습니다.\n\n"
                + details(info)
                + "결재: " + route + "\n\n"
                + "내 휴가: " + url(baseUrl, "/my-leaves") + "\n";
        return root(info, Thread.APPLICANT, domain, body);
    }

    /** 인사관리자가 대신 등록(바로 승인됨). 신청 접수 대신 이 메일이 대화의 첫 메일이 된다. */
    public static Mail registered(Info info, String domain, String baseUrl, Handler by) {
        String body = info.applicantName() + "님, " + by.title() + "이 휴가를 등록했습니다. 승인 완료 상태입니다.\n\n"
                + by.line()
                + details(info)
                + "내 휴가: " + url(baseUrl, "/my-leaves") + "\n";
        return root(info, Thread.APPLICANT, domain, body);
    }

    /** @param self 신청자 본인의 자가 승인이면 true */
    public static Mail approved(Info info, String domain, String baseUrl, Handler by, boolean self) {
        String headline = self
                ? "자가 승인으로 휴가가 확정되었습니다."
                : by.title() + "이 휴가를 승인했습니다.";
        return applicantReply(info, domain, baseUrl, headline, by, null);
    }

    public static Mail rejected(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, by.title() + "이 휴가 신청을 반려했습니다.", by,
                "반려 사유: " + orNone(reason));
    }

    /** 취소 요청 승인(또는 인사관리자의 취소 요청 건 확정). */
    public static Mail cancelApproved(Info info, String domain, String baseUrl, Handler by) {
        return applicantReply(info, domain, baseUrl,
                by.title() + "이 휴가 취소 요청을 승인해 휴가가 취소되었습니다. 차감된 연차는 돌아갑니다.", by, null);
    }

    public static Mail cancelRejected(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl,
                by.title() + "이 휴가 취소 요청을 반려했습니다. 휴가는 승인 상태로 유지됩니다.", by,
                "반려 사유: " + orNone(reason));
    }

    /** 결재 대기 중인 신청을 인사관리자가 취소. */
    public static Mail cancelledByHr(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl, by.title() + "이 휴가 신청을 취소했습니다.", by,
                reason != null && !reason.isBlank() ? "사유: " + reason : null);
    }

    /** 승인된 휴가를 인사관리자가 강제 취소(시작 후 포함). */
    public static Mail forceCancelled(Info info, String domain, String baseUrl, Handler by, String reason) {
        return applicantReply(info, domain, baseUrl,
                by.title() + "이 승인된 휴가를 취소했습니다. 차감된 연차는 돌아갑니다.", by,
                "취소 사유: " + orNone(reason));
    }

    // --- 결재자 대화 ---

    public static Mail approvalRequest(Info info, String domain, String baseUrl) {
        String body = info.applicantName() + "님이 휴가를 신청했습니다. 결재를 부탁드립니다.\n\n"
                + details(info)
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return root(info, Thread.APPROVER, domain, body);
    }

    public static Mail withdrawn(Info info, String domain, String baseUrl) {
        String body = info.applicantName() + "님이 결재 대기 중이던 휴가 신청을 취소했습니다. 더 결재하지 않아도 됩니다.\n\n"
                + new Handler("신청자", info.applicantName(), info.departmentName()).line()
                + details(info)
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return reply(info, Thread.APPROVER, domain, body);
    }

    public static Mail cancelRequested(Info info, String domain, String baseUrl, String cancelReason) {
        String body = info.applicantName() + "님이 승인된 휴가의 취소를 요청했습니다. 결재를 부탁드립니다.\n\n"
                + details(info)
                + "취소 사유: " + orNone(cancelReason) + "\n\n"
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return reply(info, Thread.APPROVER, domain, body);
    }

    // --- 담당 팀장 대화 ---

    public static Mail leadApprovedInfo(Info info, String domain, String baseUrl, Handler by) {
        return leadRoot(info, domain, baseUrl, by.title() + "이 " + info.applicantName() + "님의 휴가를 승인했습니다.", by);
    }

    public static Mail leadRegisteredInfo(Info info, String domain, String baseUrl, Handler by) {
        return leadRoot(info, domain, baseUrl,
                by.title() + "이 " + info.applicantName() + "님의 휴가를 등록했습니다(승인 완료).", by);
    }

    public static Mail leadCancelledInfo(Info info, String domain, String baseUrl, Handler by) {
        String body = info.applicantName() + "님의 승인된 휴가가 취소되었습니다.\n\n"
                + by.line()
                + details(info)
                + "캘린더: " + url(baseUrl, "/calendar") + "\n";
        return reply(info, Thread.LEAD, domain, body);
    }

    public static Mail leadForceCancelledInfo(Info info, String domain, String baseUrl, Handler by, String reason) {
        String body = by.title() + "이 " + info.applicantName() + "님의 승인된 휴가를 취소했습니다.\n\n"
                + by.line()
                + details(info)
                + "취소 사유: " + orNone(reason) + "\n\n"
                + "캘린더: " + url(baseUrl, "/calendar") + "\n";
        return reply(info, Thread.LEAD, domain, body);
    }

    // --- 대화(Thread) 공통 ---

    /** 대화의 첫 메일 제목. 답장 제목도 이것으로 만든다. */
    public static String rootSubject(Info info, Thread thread) {
        String what = info.leaveLabel() + " " + info.period();
        return PREFIX + switch (thread) {
            case APPLICANT -> "내 휴가 - " + what;
            case APPROVER -> "휴가 결재 요청 - " + info.applicantName() + " " + what;
            case LEAD -> "팀원 휴가 - " + info.applicantName() + " " + what;
        };
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

    private static Mail leadRoot(Info info, String domain, String baseUrl, String headline, Handler by) {
        String body = headline + "\n\n"
                + by.line()
                + details(info)
                + "캘린더: " + url(baseUrl, "/calendar") + "\n";
        return root(info, Thread.LEAD, domain, body);
    }

    private static Mail applicantReply(Info info, String domain, String baseUrl, String headline, Handler by,
                                       String extra) {
        String body = info.applicantName() + "님, " + headline + "\n\n"
                + by.line()
                + details(info)
                + (extra != null ? extra + "\n\n" : "")
                + "내 휴가: " + url(baseUrl, "/my-leaves") + "\n";
        return reply(info, Thread.APPLICANT, domain, body);
    }

    private static Mail root(Info info, Thread thread, String domain, String body) {
        return new Mail(rootSubject(info, thread), body, threadId(info, thread, domain), null);
    }

    private static Mail reply(Info info, Thread thread, String domain, String body) {
        return new Mail("RE: " + rootSubject(info, thread), body, null, threadId(info, thread, domain));
    }

    private static String details(Info info) {
        return "신청자: " + info.applicantName()
                + (info.departmentName() != null ? " (" + info.departmentName() + ")" : "") + "\n"
                + "종류: " + info.leaveLabel() + "\n"
                + "기간: " + info.period() + " (" + info.amount() + ")\n"
                + "사유: " + orNone(info.reason()) + "\n\n";
    }

    private static String orNone(String text) {
        return text == null || text.isBlank() ? "미기재" : text;
    }

    private static String url(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + path;
    }
}
