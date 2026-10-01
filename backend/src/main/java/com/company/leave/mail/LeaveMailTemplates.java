package com.company.leave.mail;

/**
 * 휴가 결재 메일 본문(일반 텍스트). 템플릿 엔진 없이 문자열로 만든다.
 *
 * <p>신청 건 하나에 받는 쪽별 대화(Thread)가 셋 있다. 각 대화의 첫 메일은 신청 번호로 정한 Message-ID 를 쓰고,
 * 이후 메일은 그 ID 에 답장(RE: 같은 제목)으로 붙어 메일함에서 한 대화로 보인다.
 * <ul>
 *   <li>APPLICANT: 신청 접수 → 승인·반려·취소 결과</li>
 *   <li>LEAD: 1차 결재 요청(또는 팀장 단계가 없으면 승인 안내) → 승인·취소 안내</li>
 *   <li>HR: 결재 요청(팀장 승인 뒤면 최종 결재 요청) → 신청 철회·취소 요청</li>
 * </ul>
 * 첫 메일 제목은 대화마다 고정이라, 답장 제목은 같은 정보로 다시 계산해 "RE: " 를 붙인다.
 */
public final class LeaveMailTemplates {

    private static final String PREFIX = "[연차관리] ";

    private LeaveMailTemplates() {
    }

    public enum Thread { APPLICANT, LEAD, HR }

    /**
     * 메일에 쓰는 신청 정보.
     *
     * @param createdEpochMillis 신청 시각. 신청 번호와 함께 Message-ID 에 넣어 DB 를 초기화해도 대화가 섞이지 않게 한다
     * @param leaveLabel         휴가 종류(경조사 규정 포함, 예: "경조사(본인 결혼)")
     * @param period             기간(예: "2026-10-14 ~ 2026-10-15", 하루면 날짜 하나)
     * @param amount             일수·시간(예: "2일", "0.5일", "2시간")
     * @param viaLead            팀장 1차 결재 단계를 거치는 건인지(대화 첫 메일 제목이 달라진다)
     */
    public record Info(long requestId, long createdEpochMillis, String applicantName, String departmentName,
                       String leaveLabel, String period, String amount, String reason, String hrDirectReason,
                       boolean viaLead) {
    }

    /** 대화 첫 메일이면 messageId, 답장이면 inReplyTo 가 채워진다. */
    public record Mail(String subject, String body, String messageId, String inReplyTo) {
    }

    // --- 신청자 대화 ---

    public static Mail submitted(Info info, String domain, String baseUrl, String route) {
        String body = info.applicantName() + "님, 휴가 신청이 접수되었습니다.\n\n"
                + details(info)
                + "결재 경로: " + route + "\n\n"
                + "내 휴가: " + url(baseUrl, "/my-leaves") + "\n";
        return root(info, Thread.APPLICANT, domain, body);
    }

    public static Mail approved(Info info, String domain, String baseUrl) {
        return applicantReply(info, domain, baseUrl, "휴가가 최종 승인되었습니다.", null);
    }

    /** @param rejectedBy 반려한 사람(예: "팀장 홍길동님", "인사관리자 김인사님") */
    public static Mail rejected(Info info, String domain, String baseUrl, String rejectedBy, String reason) {
        return applicantReply(info, domain, baseUrl, rejectedBy + "이 휴가 신청을 반려했습니다.",
                "반려 사유: " + orNone(reason));
    }

    public static Mail cancelApproved(Info info, String domain, String baseUrl) {
        return applicantReply(info, domain, baseUrl,
                "휴가 취소 요청이 승인되어 휴가가 취소되었습니다. 차감된 연차는 돌아갑니다.", null);
    }

    public static Mail cancelRejected(Info info, String domain, String baseUrl, String reason) {
        return applicantReply(info, domain, baseUrl, "휴가 취소 요청이 반려되었습니다. 휴가는 승인 상태로 유지됩니다.",
                "반려 사유: " + orNone(reason));
    }

    public static Mail cancelledByHr(Info info, String domain, String baseUrl) {
        return applicantReply(info, domain, baseUrl, "인사관리자가 이 휴가를 취소했습니다.", null);
    }

    // --- 팀장 대화 ---

    public static Mail leadRequest(Info info, String domain, String baseUrl) {
        String body = info.applicantName() + "님이 휴가를 신청했습니다. 1차 결재를 부탁드립니다.\n\n"
                + details(info)
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return root(info, Thread.LEAD, domain, body);
    }

    /** 최종 승인 안내. 팀장 단계를 거쳤으면 결재 요청 메일에 답장, 아니면 이 메일이 대화의 첫 메일. */
    public static Mail leadApprovedInfo(Info info, String domain, String baseUrl) {
        String body = info.applicantName() + "님의 휴가가 최종 승인되었습니다.\n\n"
                + details(info)
                + "캘린더: " + url(baseUrl, "/calendar") + "\n";
        return info.viaLead() ? reply(info, Thread.LEAD, domain, body) : root(info, Thread.LEAD, domain, body);
    }

    public static Mail leadCancelledInfo(Info info, String domain, String baseUrl) {
        String body = info.applicantName() + "님의 승인된 휴가가 취소되었습니다.\n\n"
                + details(info)
                + "캘린더: " + url(baseUrl, "/calendar") + "\n";
        return reply(info, Thread.LEAD, domain, body);
    }

    // --- 인사관리자 대화 ---

    public static Mail hrRequest(Info info, String domain, String baseUrl, String leadApproverName) {
        String head = info.viaLead()
                ? "팀장 " + leadApproverName + "님이 1차 승인한 휴가입니다. 최종 결재를 부탁드립니다.\n\n"
                : info.applicantName() + "님이 휴가를 신청했습니다. 결재를 부탁드립니다.\n\n";
        String direct = info.hrDirectReason() != null ? "팀장 부재로 인사 직행: " + info.hrDirectReason() + "\n\n" : "";
        String body = head + details(info) + direct + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return root(info, Thread.HR, domain, body);
    }

    public static Mail hrCancelRequested(Info info, String domain, String baseUrl, String cancelReason) {
        String body = info.applicantName() + "님이 승인된 휴가의 취소를 요청했습니다. 결재를 부탁드립니다.\n\n"
                + details(info)
                + "취소 사유: " + orNone(cancelReason) + "\n\n"
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return reply(info, Thread.HR, domain, body);
    }

    // --- 결재 대기 중 신청 철회(팀장·인사 대화 공통) ---

    public static Mail withdrawn(Info info, Thread thread, String domain, String baseUrl) {
        String body = info.applicantName() + "님이 결재 대기 중이던 휴가 신청을 취소했습니다. 더 결재하지 않아도 됩니다.\n\n"
                + details(info)
                + "결재함: " + url(baseUrl, "/approvals") + "\n";
        return reply(info, thread, domain, body);
    }

    // --- 대화(Thread) 공통 ---

    /** 대화의 첫 메일 제목. 답장 제목도 이것으로 만든다. */
    public static String rootSubject(Info info, Thread thread) {
        String what = info.leaveLabel() + " " + info.period();
        return PREFIX + switch (thread) {
            case APPLICANT -> "휴가 신청 접수 - " + what;
            case LEAD -> (info.viaLead() ? "휴가 1차 결재 요청 - " : "팀원 휴가 승인 - ")
                    + info.applicantName() + " " + what;
            case HR -> (info.viaLead() ? "휴가 최종 결재 요청 - " : "휴가 결재 요청 - ")
                    + info.applicantName() + " " + what;
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

    private static Mail applicantReply(Info info, String domain, String baseUrl, String headline, String extra) {
        String body = info.applicantName() + "님, " + headline + "\n\n"
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
