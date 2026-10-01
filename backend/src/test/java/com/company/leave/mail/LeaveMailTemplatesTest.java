package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.mail.LeaveMailTemplates.Info;
import com.company.leave.mail.LeaveMailTemplates.Mail;
import com.company.leave.mail.LeaveMailTemplates.Thread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("휴가 결재 메일 문구")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveMailTemplatesTest {

    private static final String DOMAIN = "company.com";
    private static final String BASE = "https://leave.company.com/";

    private final Info viaLead = info(true, null);
    private final Info direct = info(false, "팀장 휴가 중");

    @Test
    void 대화의_첫_메일은_신청_번호와_신청_시각으로_정한_Message_ID_를_쓴다() {
        Mail mail = LeaveMailTemplates.submitted(viaLead, DOMAIN, BASE, "인사관리자 승인");

        assertThat(mail.messageId()).isEqualTo("<leave-42-1700000000000.applicant@company.com>");
        assertThat(mail.inReplyTo()).isNull();
        assertThat(mail.subject()).isEqualTo("[연차관리] 휴가 신청 접수 - 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
    }

    @Test
    void 답장은_첫_메일_ID_에_붙고_제목은_RE_와_첫_메일_제목이다() {
        Mail root = LeaveMailTemplates.submitted(viaLead, DOMAIN, BASE, "인사관리자 승인");
        Mail reply = LeaveMailTemplates.approved(viaLead, DOMAIN, BASE);

        assertThat(reply.messageId()).isNull();
        assertThat(reply.inReplyTo()).isEqualTo(root.messageId());
        assertThat(reply.subject()).isEqualTo("RE: " + root.subject());
    }

    @Test
    void 팀장_대화는_팀장_단계를_거치면_결재_요청에_답장하고_아니면_승인_안내가_첫_메일이다() {
        Mail request = LeaveMailTemplates.leadRequest(viaLead, DOMAIN, BASE);
        Mail approvedViaLead = LeaveMailTemplates.leadApprovedInfo(viaLead, DOMAIN, BASE);
        Mail approvedDirect = LeaveMailTemplates.leadApprovedInfo(direct, DOMAIN, BASE);

        assertThat(approvedViaLead.inReplyTo()).isEqualTo(request.messageId());
        assertThat(approvedViaLead.subject()).isEqualTo("RE: " + request.subject());
        assertThat(approvedDirect.messageId()).isEqualTo("<leave-42-1700000000000.lead@company.com>");
        assertThat(approvedDirect.subject()).isEqualTo("[연차관리] 팀원 휴가 승인 - 홍길동 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
    }

    @Test
    void 인사_결재_요청은_팀장_승인_뒤면_최종_결재_요청이고_직행이면_사유를_싣는다() {
        Mail afterLead = LeaveMailTemplates.hrRequest(viaLead, DOMAIN, BASE, "김팀장");
        Mail directRequest = LeaveMailTemplates.hrRequest(direct, DOMAIN, BASE, null);

        assertThat(afterLead.subject()).startsWith("[연차관리] 휴가 최종 결재 요청 - 홍길동");
        assertThat(afterLead.body()).contains("팀장 김팀장님이 1차 승인한 휴가입니다");
        assertThat(directRequest.subject()).startsWith("[연차관리] 휴가 결재 요청 - 홍길동");
        assertThat(directRequest.body()).contains("팀장 부재로 인사 직행: 팀장 휴가 중");
    }

    @Test
    void 본문에는_신청_정보와_바로가기_링크가_들어간다() {
        Mail mail = LeaveMailTemplates.leadRequest(viaLead, DOMAIN, BASE);

        assertThat(mail.body())
                .contains("신청자: 홍길동 (개발팀)")
                .contains("종류: 경조사(본인 결혼)")
                .contains("기간: 2026-10-14 ~ 2026-10-16 (3일)")
                .contains("사유: 결혼식")
                .contains("결재함: https://leave.company.com/approvals");
    }

    @Test
    void 반려_메일에는_반려한_사람과_사유가_들어가고_사유가_없으면_미기재다() {
        Mail withReason = LeaveMailTemplates.rejected(viaLead, DOMAIN, BASE, "팀장 김팀장님", "마감 주간");
        Mail noReason = LeaveMailTemplates.rejected(viaLead, DOMAIN, BASE, "인사관리자 이인사님", " ");

        assertThat(withReason.body()).contains("팀장 김팀장님이 휴가 신청을 반려했습니다").contains("반려 사유: 마감 주간");
        assertThat(noReason.body()).contains("반려 사유: 미기재");
    }

    @Test
    void 결재_대기_중_철회는_결재하던_대화에_답장한다() {
        Mail toLead = LeaveMailTemplates.withdrawn(viaLead, Thread.LEAD, DOMAIN, BASE);
        Mail toHr = LeaveMailTemplates.withdrawn(direct, Thread.HR, DOMAIN, BASE);

        assertThat(toLead.inReplyTo()).isEqualTo(LeaveMailTemplates.threadId(viaLead, Thread.LEAD, DOMAIN));
        assertThat(toHr.inReplyTo()).isEqualTo(LeaveMailTemplates.threadId(direct, Thread.HR, DOMAIN));
        assertThat(toHr.subject()).isEqualTo("RE: [연차관리] 휴가 결재 요청 - 홍길동 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
    }

    @Test
    void Message_ID_도메인은_보내는_주소에서_가져오고_없으면_기본값이다() {
        assertThat(LeaveMailTemplates.domainOf("no-reply@company.com")).isEqualTo("company.com");
        assertThat(LeaveMailTemplates.domainOf("연차관리 <hr@corp.co.kr>")).isEqualTo("corp.co.kr");
        assertThat(LeaveMailTemplates.domainOf(null)).isEqualTo("annual-leave.local");
        assertThat(LeaveMailTemplates.domainOf("no-at-sign")).isEqualTo("annual-leave.local");
    }

    private static Info info(boolean viaLead, String hrDirectReason) {
        return new Info(42L, 1_700_000_000_000L, "홍길동", "개발팀", "경조사(본인 결혼)",
                "2026-10-14 ~ 2026-10-16", "3일", "결혼식", hrDirectReason, viaLead);
    }
}
