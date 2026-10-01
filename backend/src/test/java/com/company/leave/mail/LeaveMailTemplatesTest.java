package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.mail.LeaveMailTemplates.Handler;
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

    private final Info info = new Info(42L, 1_700_000_000_000L, "홍길동", "개발팀", "경조사(본인 결혼)",
            "2026-10-14 ~ 2026-10-16", "3일", "결혼식");
    private final Handler 팀장 = new Handler("팀장", "김팀장");
    private final Handler 인사 = new Handler("인사관리자", "이인사");

    @Test
    void 대화의_첫_메일은_신청_번호와_신청_시각으로_정한_Message_ID_를_쓴다() {
        Mail mail = LeaveMailTemplates.submitted(info, DOMAIN, BASE, "팀장 김팀장님 승인(인사관리자도 승인 가능)");

        assertThat(mail.messageId()).isEqualTo("<leave-42-1700000000000.applicant@company.com>");
        assertThat(mail.inReplyTo()).isNull();
        assertThat(mail.subject()).isEqualTo("[연차관리] 내 휴가 - 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
        assertThat(mail.body()).contains("결재: 팀장 김팀장님 승인(인사관리자도 승인 가능)");
    }

    @Test
    void 답장은_첫_메일_ID_에_붙고_제목은_RE_와_첫_메일_제목이다() {
        Mail root = LeaveMailTemplates.submitted(info, DOMAIN, BASE, "인사관리자 승인");
        Mail reply = LeaveMailTemplates.approved(info, DOMAIN, BASE, 팀장, false);

        assertThat(reply.messageId()).isNull();
        assertThat(reply.inReplyTo()).isEqualTo(root.messageId());
        assertThat(reply.subject()).isEqualTo("RE: " + root.subject());
        assertThat(reply.body()).contains("김팀장님이 휴가를 승인했습니다");
    }

    @Test
    void 자가_승인이면_자가_승인으로_안내한다() {
        assertThat(LeaveMailTemplates.approved(info, DOMAIN, BASE, 팀장, true).body())
                .contains("자가 승인으로 휴가가 확정되었습니다");
    }

    @Test
    void 강제_등록은_신청자_대화의_첫_메일이다() {
        Mail mail = LeaveMailTemplates.registered(info, DOMAIN, BASE, 인사);

        assertThat(mail.messageId()).isEqualTo(LeaveMailTemplates.threadId(info, Thread.APPLICANT, DOMAIN));
        assertThat(mail.body()).startsWith("휴가 등록 안내\n\n이인사님이 휴가를 등록했습니다.\n아래 휴가 내역은 승인 완료 상태입니다.");
    }

    @Test
    void 결재_요청에_철회와_취소_요청이_답장으로_붙는다() {
        Mail request = LeaveMailTemplates.approvalRequest(info, DOMAIN, BASE);
        Mail withdrawn = LeaveMailTemplates.withdrawn(info, DOMAIN, BASE);
        Mail cancel = LeaveMailTemplates.cancelRequested(info, DOMAIN, BASE, "일정 변경");

        assertThat(request.subject()).isEqualTo("[연차관리] 휴가 결재 요청 - 홍길동 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
        assertThat(request.body()).contains("결재함 바로가기: https://leave.company.com/approvals");
        assertThat(withdrawn.inReplyTo()).isEqualTo(request.messageId());
        assertThat(cancel.inReplyTo()).isEqualTo(request.messageId());
        assertThat(cancel.body()).contains("취소 사유: 일정 변경");
    }

    @Test
    void 팀장_안내는_승인이나_등록이_첫_메일이고_취소_안내가_답장이다() {
        Mail approvedInfo = LeaveMailTemplates.leadApprovedInfo(info, DOMAIN, BASE, 인사);
        Mail cancelled = LeaveMailTemplates.leadCancelledInfo(info, DOMAIN, BASE, 팀장);
        Mail forced = LeaveMailTemplates.leadForceCancelledInfo(info, DOMAIN, BASE, 인사, "근태 정정");

        assertThat(approvedInfo.subject()).isEqualTo("[연차관리] 팀원 휴가 - 홍길동 경조사(본인 결혼) 2026-10-14 ~ 2026-10-16");
        assertThat(approvedInfo.body()).contains("이인사님이 홍길동님의 휴가를 승인했습니다");
        assertThat(cancelled.inReplyTo()).isEqualTo(approvedInfo.messageId());
        assertThat(forced.inReplyTo()).isEqualTo(approvedInfo.messageId());
        assertThat(forced.body()).contains("취소 사유: 근태 정정");
    }

    @Test
    void 본문에는_신청_정보와_바로가기_링크가_들어간다() {
        Mail mail = LeaveMailTemplates.approvalRequest(info, DOMAIN, BASE);

        assertThat(mail.body())
                .contains("신청자: 홍길동")
                .contains("소속: 개발팀")
                .contains("휴가 종류: 경조사(본인 결혼)")
                .contains("휴가 기간: 2026-10-14 ~ 2026-10-16 (3일)")
                .contains("신청 사유: 결혼식")
                .contains("처리 상태: 결재 대기");
    }

    @Test
    void 반려와_강제_취소에는_사유가_들어가고_없으면_미기재다() {
        Mail rejected = LeaveMailTemplates.rejected(info, DOMAIN, BASE, 팀장, "마감 주간");
        Mail noReason = LeaveMailTemplates.rejected(info, DOMAIN, BASE, 인사, " ");
        Mail forced = LeaveMailTemplates.forceCancelled(info, DOMAIN, BASE, 인사, "근태 정정");

        assertThat(rejected.body()).contains("김팀장님이 휴가 신청을 반려했습니다").contains("반려 사유: 마감 주간");
        assertThat(noReason.body()).contains("반려 사유: 미기재");
        assertThat(forced.body()).contains("이인사님이 승인된 휴가를 취소했습니다").contains("취소 사유: 근태 정정");
    }

    @Test
    void 처리_결과_메일에는_처리자의_이름_자격_부서가_들어간다() {
        Handler 시스템관리자 = new Handler("시스템 관리자", "관리자");

        assertThat(LeaveMailTemplates.approved(info, DOMAIN, BASE, 팀장, false).body())
                .contains("처리자: 김팀장 (팀장)");
        assertThat(LeaveMailTemplates.cancelApproved(info, DOMAIN, BASE, 인사).body())
                .contains("이인사님이 휴가 취소 요청을 승인해 휴가가 취소되었습니다")
                .contains("처리자: 이인사 (인사관리자)");
        assertThat(LeaveMailTemplates.cancelRejected(info, DOMAIN, BASE, 팀장, "인력 부족").body())
                .contains("김팀장님이 휴가 취소 요청을 반려했습니다")
                .contains("처리자: 김팀장 (팀장)");
        assertThat(LeaveMailTemplates.cancelledByHr(info, DOMAIN, BASE, 시스템관리자, null).body())
                .contains("처리자: 관리자 (시스템 관리자)");
        assertThat(LeaveMailTemplates.leadCancelledInfo(info, DOMAIN, BASE, 인사).body())
                .contains("처리자: 이인사 (인사관리자)");
        assertThat(LeaveMailTemplates.withdrawn(info, DOMAIN, BASE).body())
                .contains("처리자: 홍길동 (신청자)");
    }

    @Test
    void 신청과_취소_요청_메일에는_처리자_줄이_없다() {
        assertThat(LeaveMailTemplates.submitted(info, DOMAIN, BASE, "인사관리자 승인").body()).doesNotContain("처리자:");
        assertThat(LeaveMailTemplates.approvalRequest(info, DOMAIN, BASE).body()).doesNotContain("처리자:");
        assertThat(LeaveMailTemplates.cancelRequested(info, DOMAIN, BASE, "일정 변경").body()).doesNotContain("처리자:");
    }

    @Test
    void Message_ID_도메인은_보내는_주소에서_가져오고_없으면_기본값이다() {
        assertThat(LeaveMailTemplates.domainOf("no-reply@company.com")).isEqualTo("company.com");
        assertThat(LeaveMailTemplates.domainOf("연차관리 <hr@corp.co.kr>")).isEqualTo("corp.co.kr");
        assertThat(LeaveMailTemplates.domainOf(null)).isEqualTo("annual-leave.local");
        assertThat(LeaveMailTemplates.domainOf("no-at-sign")).isEqualTo("annual-leave.local");
    }
}
