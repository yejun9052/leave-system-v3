package com.company.leave.leave;

import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.mail.AccountMailProperties;
import com.company.leave.mail.LeaveMail;
import com.company.leave.mail.LeaveMailTemplates;
import com.company.leave.mail.LeaveMailTemplates.Info;
import com.company.leave.mail.LeaveMailTemplates.Thread;
import com.company.leave.notification.NotificationService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 휴가 결재 단계별 앱 알림 + 메일 발송. 받는 사람은 결재 규칙을 아는 {@link LeaveRequestService} 가 정해서 넘긴다.
 * 메일은 이벤트로 발행되어 트랜잭션 커밋 뒤 보내진다(실패해도 결재에 영향 없음).
 * 알림은 모든 받는 사람에게, 메일은 이메일 주소가 있는 사람에게만(관리 전용 계정 제외) 간다.
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

    /** 신청 접수: 신청자 + (팀장 단계면 팀장, 아니면 인사관리자). */
    public void submitted(LeaveRequest r, Employee lead, List<Employee> hrs, boolean viaLead) {
        Info info = info(r, viaLead);
        String route = viaLead && lead != null
                ? "팀장 " + lead.getName() + "님 1차 승인 → 인사관리자 최종 승인"
                : "인사관리자 승인";
        deliver(List.of(r.getEmployee()), "LEAVE_SUBMITTED", "휴가 신청이 접수되었습니다.",
                summary(r) + " · " + route, "/my-leaves",
                LeaveMailTemplates.submitted(info, domain(), baseUrl(), route));
        if (viaLead && lead != null) {
            deliver(List.of(lead), "LEAVE_REQUESTED", "새 휴가 결재 요청", withApplicant(r), "/approvals",
                    LeaveMailTemplates.leadRequest(info, domain(), baseUrl()));
        } else {
            deliver(hrs, "LEAVE_REQUESTED", "새 휴가 결재 요청", withApplicant(r)
                            + (r.getHrDirectReason() != null ? " (팀장 부재로 인사 직행: " + r.getHrDirectReason() + ")" : ""),
                    "/approvals", LeaveMailTemplates.hrRequest(info, domain(), baseUrl(), null));
        }
    }

    /** 팀장 1차 승인: 인사관리자에게 최종 결재 요청(알림+메일), 신청자에게 진행 안내(알림만). */
    public void leadApproved(LeaveRequest r, List<Employee> hrs) {
        Info info = info(r, true);
        String leadName = r.getLeadApprover() != null ? r.getLeadApprover().getName() : "";
        deliver(hrs, "LEAVE_FINAL_APPROVAL_REQUESTED", "휴가 최종 승인 요청",
                withApplicant(r) + " (팀장 1차 승인 완료)", "/approvals",
                LeaveMailTemplates.hrRequest(info, domain(), baseUrl(), leadName));
        notificationService.notify(r.getEmployee().getId(), "LEAVE_LEAD_APPROVED", "팀장이 휴가를 1차 승인했습니다.",
                summary(r) + " · 인사관리자 최종 승인 대기", "/my-leaves");
    }

    /** 최종 승인: 신청자 + 팀장(없거나 본인이 승인했으면 생략). */
    public void finalApproved(LeaveRequest r, Employee lead, boolean viaLead) {
        Info info = info(r, viaLead);
        deliver(List.of(r.getEmployee()), "LEAVE_APPROVED", "휴가가 승인되었습니다.", summary(r), "/my-leaves",
                LeaveMailTemplates.approved(info, domain(), baseUrl()));
        if (lead != null) {
            deliver(List.of(lead), "LEAVE_APPROVED_INFO", "팀원 휴가 승인",
                    r.getEmployee().getName() + "님의 " + summary(r) + "가 최종 승인되었습니다.", "/calendar",
                    LeaveMailTemplates.leadApprovedInfo(info, domain(), baseUrl()));
        }
    }

    /** 반려: 신청자. */
    public void rejected(LeaveRequest r, Employee rejector, boolean byLead, String reason, boolean viaLead) {
        String by = (byLead ? "팀장 " : "인사관리자 ") + rejector.getName() + "님";
        deliver(List.of(r.getEmployee()), "LEAVE_REJECTED", "휴가가 반려되었습니다.",
                summary(r) + " · " + (reason != null && !reason.isBlank() ? reason : "사유 미기재"), "/my-leaves",
                LeaveMailTemplates.rejected(info(r, viaLead), domain(), baseUrl(), by, reason));
    }

    /** 결재 대기 중 신청자가 철회: 그 건을 결재하던 사람(팀장 또는 인사관리자). */
    public void withdrawn(LeaveRequest r, Collection<Employee> approvers, boolean leadStage, boolean viaLead) {
        deliver(approvers, "LEAVE_WITHDRAWN", "휴가 신청 취소", withApplicant(r) + " · 신청자가 취소함", "/approvals",
                LeaveMailTemplates.withdrawn(info(r, viaLead), leadStage ? Thread.LEAD : Thread.HR, domain(), baseUrl()));
    }

    /** 인사관리자가 휴가(대기·승인)를 직접 취소: 신청자. */
    public void cancelledByHr(LeaveRequest r, boolean viaLead) {
        deliver(List.of(r.getEmployee()), "LEAVE_CANCELLED_BY_HR", "인사관리자가 휴가를 취소했습니다.", summary(r),
                "/my-leaves", LeaveMailTemplates.cancelledByHr(info(r, viaLead), domain(), baseUrl()));
    }

    /** 승인된 휴가의 취소 요청: 인사관리자. */
    public void cancelRequested(LeaveRequest r, List<Employee> hrs, boolean viaLead) {
        deliver(hrs, "LEAVE_CANCEL_REQUESTED", "휴가 취소 요청", withApplicant(r), "/approvals",
                LeaveMailTemplates.hrCancelRequested(info(r, viaLead), domain(), baseUrl(), r.getCancelReason()));
    }

    /** 취소 요청 승인: 신청자. */
    public void cancelApproved(LeaveRequest r, boolean viaLead) {
        deliver(List.of(r.getEmployee()), "LEAVE_CANCEL_APPROVED", "휴가 취소가 승인되었습니다.", summary(r),
                "/my-leaves", LeaveMailTemplates.cancelApproved(info(r, viaLead), domain(), baseUrl()));
    }

    /** 취소 요청 반려: 신청자. */
    public void cancelRejected(LeaveRequest r, String reason, boolean viaLead) {
        deliver(List.of(r.getEmployee()), "LEAVE_CANCEL_REJECTED", "휴가 취소 요청이 반려되었습니다.",
                reason != null && !reason.isBlank() ? reason : "사유 미기재", "/my-leaves",
                LeaveMailTemplates.cancelRejected(info(r, viaLead), domain(), baseUrl(), reason));
    }

    /** 승인됐던 휴가가 취소 확정: 팀장에게 안내. */
    public void leadCancelledInfo(LeaveRequest r, Employee lead, boolean viaLead) {
        deliver(List.of(lead), "LEAVE_CANCELLED_INFO", "팀원 휴가 취소",
                r.getEmployee().getName() + "님의 " + r.getLeaveType().getName() + "(" + period(r) + ") 사용이 취소되었습니다.",
                "/calendar", LeaveMailTemplates.leadCancelledInfo(info(r, viaLead), domain(), baseUrl()));
    }

    // --- 공통 ---

    private void deliver(Collection<Employee> recipients, String type, String title, String message, String link,
                         LeaveMailTemplates.Mail mail) {
        Map<Long, Employee> unique = new LinkedHashMap<>();
        recipients.stream().filter(Objects::nonNull).forEach(e -> unique.putIfAbsent(e.getId(), e));
        if (unique.isEmpty()) {
            return;
        }
        unique.values().forEach(e -> notificationService.notify(e.getId(), type, title, message, link));
        List<String> to = unique.values().stream()
                .filter(e -> !e.isSystemAccount())
                .map(Employee::getEmail)
                .filter(email -> email != null && email.contains("@"))
                .distinct()
                .toList();
        if (!to.isEmpty()) {
            eventPublisher.publishEvent(new LeaveMail(to, mail.subject(), mail.body(), mail.messageId(), mail.inReplyTo()));
        }
    }

    private Info info(LeaveRequest r, boolean viaLead) {
        Employee e = r.getEmployee();
        String label = r.getLeaveType().getName()
                + (r.getSpecialRuleName() != null ? "(" + r.getSpecialRuleName() + ")" : "");
        long created = r.getCreatedAt() != null ? r.getCreatedAt().toEpochMilli() : 0L;
        return new Info(r.getId() != null ? r.getId() : 0L, created, e.getName(),
                e.getDepartment() != null ? e.getDepartment().getName() : null,
                label, period(r), amount(r), r.getReason(), r.getHrDirectReason(), viaLead);
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
        return r.getDays().stripTrailingZeros().toPlainString() + "일";
    }

    private String domain() {
        return LeaveMailTemplates.domainOf(mailProperties.from());
    }

    private String baseUrl() {
        return mailProperties.linkBaseUrl() != null ? mailProperties.linkBaseUrl() : "";
    }
}
