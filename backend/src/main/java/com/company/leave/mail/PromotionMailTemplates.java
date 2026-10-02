package com.company.leave.mail;

import com.company.leave.mail.MailLayout.Content;
import com.company.leave.mail.MailLayout.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 연차 사용 촉진 안내 메일. 사용 기한 전에 아직 사용 계획이 없는 연차를 알린다.
 * 남은 기간("2개월 29일")과 D-day 는 보내는 날 서버가 계산한다.
 */
public final class PromotionMailTemplates {

    private PromotionMailTemplates() {
    }

    /**
     * @param granted   부여 연차(이월 포함)
     * @param remaining 남은 연차(승인된 휴가만 뺀 값)
     * @param pending   결재 대기 중인 차감 예정
     * @param unplanned 사용 계획이 없는 연차 = 남은 연차 − 결재 대기
     * @param timeLeft  사용 기한까지 남은 기간("2개월 29일")
     * @param daysLeft  사용 기한까지 남은 날(D-day, 기한 당일이면 0)
     */
    public record Notice(String name, String departmentName, LocalDate periodStart, LocalDate periodEnd,
                         BigDecimal granted, BigDecimal used, BigDecimal remaining, BigDecimal pending,
                         BigDecimal unplanned, String timeLeft, long daysLeft) {
    }

    public record Mail(String subject, Content content) {
    }

    /**
     * @param by        보낸 관리자(자동 발송이면 null)
     * @param carryOver 정책에서 이월을 허용하면 true(소멸 문구가 달라진다)
     */
    public static Mail promotion(Notice n, LeaveMailTemplates.Handler by, boolean carryOver, String baseUrl) {
        List<String> intro = List.of(
                n.name() + "님, 아직 사용 계획이 없는 연차가 " + days(n.unplanned()) + " 남아 있습니다.",
                "사용 기한(" + n.periodEnd() + ")이 지나면 " + (carryOver ? "이월 한도를 넘는 연차는" : "남은 연차는")
                        + " 소멸됩니다. 사용 계획을 세워 휴가를 신청해 주세요.");
        List<Row> head = by != null ? List.of(Row.of("발송자", by.display())) : List.of();
        List<Row> rows = new ArrayList<>();
        rows.add(Row.of("이름", n.name()));
        rows.add(Row.of("소속", n.departmentName() != null ? n.departmentName() : "-"));
        rows.add(Row.of("연차 사용 기간", n.periodStart() + " ~ " + n.periodEnd()));
        rows.add(Row.of("부여 연차", days(n.granted())));
        rows.add(Row.of("사용 연차", days(n.used())));
        rows.add(Row.of("남은 연차", days(n.remaining())));
        if (n.pending().signum() > 0) {
            rows.add(Row.of("결재 대기", days(n.pending())));
        }
        rows.add(Row.strong("사용 계획 없는 연차", days(n.unplanned())));
        rows.add(Row.strong("사용 기한", n.periodEnd() + " (" + n.timeLeft() + " 남음, " + dDay(n.daysLeft()) + ")"));
        Content content = new Content("연차 사용 촉진 안내", intro, head, rows, "내 휴가 확인하기",
                MailLayout.url(baseUrl, "/my-leaves"));
        String subject = "[연차관리] 연차 사용 촉진 안내 - 사용 계획 없는 연차 " + days(n.unplanned())
                + " (사용 기한 " + n.periodEnd() + ")";
        return new Mail(subject, content);
    }

    static String dDay(long daysLeft) {
        return daysLeft == 0 ? "D-day" : "D-" + daysLeft;
    }

    private static String days(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "일";
    }
}
