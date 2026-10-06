package com.company.leave.leave;

import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.LeaveMail;
import com.company.leave.mail.LeaveMailTemplates;
import com.company.leave.mail.LeaveMailTemplates.Handler;
import com.company.leave.mail.LeaveMailTemplates.Info;
import com.company.leave.mail.MailLayout;
import com.company.leave.notification.NotificationService;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 휴가 결재 단계별 앱 알림 + 메일 발송. 받는 사람은 결재 규칙을 아는 {@link LeaveRequestService} 가 정해서 넘긴다.
 * 메일은 이벤트로 발행되어 트랜잭션 커밋 뒤 보내진다(실패해도 결재에 영향 없음).
 * 알림은 모든 받는 사람에게, 메일은 이메일 주소가 있는 사람에게만(관리 전용 계정 제외) 간다.
 * <p>승인·직접 등록·강제 취소·취소 요청 승인 결과는 담당 팀장이 아닌 사람이 처리하면 신청자에게 한 통을 보내며
 * 담당 팀장을 참조(CC)로 건다. 담당 팀장 본인이 처리했거나 담당 팀장이 없으면 신청자에게만 간다(lead == null).
 * 앱 알림은 지금처럼 신청자·팀장에게 따로 간다.
 */
@Component
public class LeaveMessenger {

    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountMailProperties mailProperties;

    public LeaveMessenger(NotificationService notificationService, ApplicationEventPublisher eventPublisher,
                          AccountMailProperties mailProperties) {
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.mailProperties = mailProperties;
    }

    /** 신청 접수: 신청자에게 접수 안내, 결재자에게 결재 요청. */
    public void submitted(LeaveRequest r, List<Employee> approvers, String route) {
        Info info = info(r);
        deliver(List.of(r.getEmployee()), "LEAVE_SUBMITTED", "휴가 신청이 접수되었습니다.",
                summary(r) + " · " + route, "/my-leaves",
                LeaveMailTemplates.submitted(info, domain(), baseUrl(), route));
        deliver(approvers, "LEAVE_REQUESTED", "새 휴가 결재 요청", withApplicant(r), "/approvals",
                LeaveMailTemplates.approvalRequest(info, domain(), baseUrl()));
    }

    /** 승인: 신청자, 담당 팀장(결재한 사람이 아니면)은 참조. */
    public void approved(LeaveRequest r, Employee approver, Employee lead) {
        Handler by = handler(approver);
        boolean self = approver.getId().equals(r.getEmployee().getId());
        notify(r.getEmployee(), "LEAVE_APPROVED", "휴가가 승인되었습니다.",
                summary(r) + " · " + (self ? "자가 승인" : by.title()), "/my-leaves");
        notify(lead, "LEAVE_APPROVED_INFO", "팀원 휴가 승인",
                r.getEmployee().getName() + "님의 " + summary(r) + " · " + by.title() + " 승인", "/calendar");
        mailWithLeadCc(r.getEmployee(), lead, LeaveMailTemplates.approved(info(r), domain(), baseUrl(), by, self));
    }

    /** 반려: 신청자. */
    public void rejected(LeaveRequest r, Employee rejector, String reason) {
        Handler by = handler(rejector);
        deliver(List.of(r.getEmployee()), "LEAVE_REJECTED", "휴가가 반려되었습니다.",
                summary(r) + " · " + by.title() + " · " + orNone(reason), "/my-leaves",
                LeaveMailTemplates.rejected(info(r), domain(), baseUrl(), by, reason));
    }

    /** 결재 대기 중 신청자가 철회: 결재 요청을 받았던 사람. */
    public void withdrawn(LeaveRequest r, Collection<Employee> approvers) {
        deliver(approvers, "LEAVE_WITHDRAWN", "휴가 신청 취소", withApplicant(r) + " · 신청자가 취소함", "/approvals",
                LeaveMailTemplates.withdrawn(info(r), domain(), baseUrl()));
    }

    /** 결재 대기 중인 신청을 인사관리자가 취소: 신청자. */
    public void cancelledByHr(LeaveRequest r, Employee hr, String reason) {
        Handler by = handler(hr);
        deliver(List.of(r.getEmployee()), "LEAVE_CANCELLED_BY_HR", "인사관리자가 휴가 신청을 취소했습니다.",
                summary(r) + " · " + by.title(), "/my-leaves",
                LeaveMailTemplates.cancelledByHr(info(r), domain(), baseUrl(), by, reason));
    }

    /** 승인된 휴가의 취소 요청: 결재자. */
    public void cancelRequested(LeaveRequest r, List<Employee> approvers) {
        deliver(approvers, "LEAVE_CANCEL_REQUESTED", "휴가 취소 요청", withApplicant(r), "/approvals",
                LeaveMailTemplates.cancelRequested(info(r), domain(), baseUrl(), r.getCancelReason()));
    }

    /** 취소 요청 승인(또는 인사관리자의 취소 요청 건 확정): 신청자, 담당 팀장(처리한 사람이 아니면)은 참조. */
    public void cancelApproved(LeaveRequest r, Employee approver, Employee lead) {
        Handler by = handler(approver);
        notify(r.getEmployee(), "LEAVE_CANCEL_APPROVED", "휴가 취소가 승인되었습니다.",
                summary(r) + " · " + by.title(), "/my-leaves");
        notify(lead, "LEAVE_CANCELLED_INFO", "팀원 휴가 취소", cancelledInfo(r, by), "/calendar");
        mailWithLeadCc(r.getEmployee(), lead, LeaveMailTemplates.cancelApproved(info(r), domain(), baseUrl(), by));
    }

    /** 취소 요청 반려: 신청자. */
    public void cancelRejected(LeaveRequest r, Employee rejector, String reason) {
        Handler by = handler(rejector);
        deliver(List.of(r.getEmployee()), "LEAVE_CANCEL_REJECTED", "휴가 취소 요청이 반려되었습니다.",
                by.title() + " · " + orNone(reason), "/my-leaves",
                LeaveMailTemplates.cancelRejected(info(r), domain(), baseUrl(), by, reason));
    }

    /** 신청자 본인이 처리한 확정 취소(신청자에게 갈 메일이 없음): 담당 팀장에게만 안내. */
    public void leadCancelledInfo(LeaveRequest r, Employee lead, Employee actor) {
        Handler by = handler(actor);
        deliver(List.of(lead), "LEAVE_CANCELLED_INFO", "팀원 휴가 취소", cancelledInfo(r, by),
                "/calendar", LeaveMailTemplates.leadCancelledInfo(info(r), domain(), baseUrl(), by));
    }

    /** 인사관리자 강제 취소: 신청자, 담당 팀장은 참조. */
    public void forceCancelled(LeaveRequest r, Employee hr, String reason, Employee lead) {
        Handler by = handler(hr);
        notify(r.getEmployee(), "LEAVE_FORCE_CANCELLED", "휴가가 취소되었습니다.",
                summary(r) + " · " + by.title() + " · 사유: " + reason, "/my-leaves");
        notify(lead, "LEAVE_CANCELLED_INFO", "팀원 휴가 취소",
                r.getEmployee().getName() + "님의 " + summary(r) + " · " + by.title() + " 취소", "/calendar");
        mailWithLeadCc(r.getEmployee(), lead,
                LeaveMailTemplates.forceCancelled(info(r), domain(), baseUrl(), by, reason));
    }

    /** 인사관리자 강제 등록(바로 승인): 신청자, 담당 팀장은 참조. */
    public void registered(LeaveRequest r, Employee hr, Employee lead) {
        Handler by = handler(hr);
        notify(r.getEmployee(), "LEAVE_REGISTERED", "휴가가 등록되었습니다.",
                summary(r) + " · " + by.title() + " 등록", "/my-leaves");
        notify(lead, "LEAVE_APPROVED_INFO", "팀원 휴가 등록",
                r.getEmployee().getName() + "님의 " + summary(r) + " · " + by.title() + " 등록", "/calendar");
        mailWithLeadCc(r.getEmployee(), lead, LeaveMailTemplates.registered(info(r), domain(), baseUrl(), by));
    }

    // --- 공통 ---

    /**
     * 처리한 사람: 자격(인사관리자 > 시스템 관리자 > 팀장) + 이름.
     * 메일 표에는 "홍길동 (팀장)", 문장·알림에는 "팀장 홍길동님" 으로 쓴다.
     */
    public static Handler handler(Employee e) {
        String role = e.hasRole(Role.HR_ADMIN) ? "인사관리자"
                : e.hasRole(Role.SYSTEM_ADMIN) ? "시스템 관리자" : "팀장";
        return new Handler(role, e.getName());
    }

    /** 알림은 받는 사람 모두, 메일은 표의 수신자 줄이 사람마다 달라 한 사람씩 따로 보낸다. */
    private void deliver(Collection<Employee> recipients, String type, String title, String message, String link,
                         LeaveMailTemplates.Mail mail) {
        Map<Long, Employee> unique = new LinkedHashMap<>();
        recipients.stream().filter(Objects::nonNull).forEach(e -> unique.putIfAbsent(e.getId(), e));
        if (unique.isEmpty()) {
            return;
        }
        unique.values().forEach(e -> notificationService.notify(e.getId(), type, title, message, link));
        Set<String> sent = new HashSet<>();
        unique.values().stream()
                .filter(LeaveMessenger::mailable)
                .filter(e -> sent.add(e.getEmail()))
                .forEach(e -> publish(List.of(e.getEmail()), List.of(), mail,
                        mail.content().withRecipient(e.getName())));
    }

    /** 앱 알림 한 건(받는 사람이 없으면 생략). */
    private void notify(Employee recipient, String type, String title, String message, String link) {
        if (recipient != null) {
            notificationService.notify(recipient.getId(), type, title, message, link);
        }
    }

    /**
     * 결과 메일 한 통: 받는 사람 신청자, 참조 담당 팀장. 팀장이 없거나 메일을 받을 수 없으면(이메일 없음·관리 전용 계정)
     * 신청자에게만. 신청자가 메일을 받을 수 없으면 팀장에게만 보낸다.
     */
    private void mailWithLeadCc(Employee applicant, Employee lead, LeaveMailTemplates.Mail mail) {
        Employee to = mailable(applicant) ? applicant : null;
        Employee cc = lead != null && mailable(lead) && (to == null || !lead.getEmail().equals(to.getEmail()))
                ? lead : null;
        if (to == null) {
            to = cc;
            cc = null;
        }
        if (to == null) {
            return;
        }
        MailLayout.Content content = cc != null
                ? LeaveMailTemplates.withLeadLink(mail.content().withRecipient(to.getName(), cc.getName()), baseUrl())
                : mail.content().withRecipient(to.getName());
        publish(List.of(to.getEmail()), cc != null ? List.of(cc.getEmail()) : List.of(), mail, content);
    }

    private void publish(List<String> to, List<String> cc, LeaveMailTemplates.Mail mail, MailLayout.Content content) {
        eventPublisher.publishEvent(new LeaveMail(to, cc, mail.subject(), content.text(), content.html(),
                mail.messageId(), mail.inReplyTo(), mail.references()));
    }

    private static boolean mailable(Employee e) {
        return !e.isSystemAccount() && e.getEmail() != null && e.getEmail().contains("@");
    }

    private static String cancelledInfo(LeaveRequest r, Handler by) {
        return r.getEmployee().getName() + "님의 " + r.getLeaveType().getName() + "(" + period(r) + ") 사용이 취소되었습니다. · "
                + by.title();
    }

    private Info info(LeaveRequest r) {
        Employee e = r.getEmployee();
        String label = r.getLeaveType().getName()
                + (r.getSpecialRuleName() != null ? "(" + r.getSpecialRuleName() + ")" : "");
        long created = r.getCreatedAt() != null ? r.getCreatedAt().toEpochMilli() : 0L;
        return new Info(r.getId() != null ? r.getId() : 0L, created, e.getName(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                label, period(r), amount(r), r.getReason());
    }

    private static String summary(LeaveRequest r) {
        return r.getLeaveType().getName() + " " + period(r);
    }

    private static String withApplicant(LeaveRequest r) {
        return r.getEmployee().getName() + " - " + summary(r);
    }

    private static String period(LeaveRequest r) {
        return r.getStartDate().equals(r.getEndDate())
                ? r.getStartDate().toString()
                : r.getStartDate() + " ~ " + r.getEndDate();
    }

    private static String amount(LeaveRequest r) {
        if (r.getLeaveType().getPortion() == DayPortion.HOURLY) {
            return WorkdayCalculator.hoursOf(r.getDays()) + "시간";
        }
        String days = r.getDays().stripTrailingZeros().toPlainString() + "일";
        return r.getHalfDayPart() != null ? days + ", " + r.getHalfDayPart().label() + " 반차" : days;
    }

    private static String orNone(String text) {
        return text != null && !text.isBlank() ? text : "사유 미기재";
    }

    private String domain() {
        return LeaveMailTemplates.domainOf(mailProperties.from());
    }

    private String baseUrl() {
        return mailProperties.linkBaseUrl() != null ? mailProperties.linkBaseUrl() : "";
    }
}
