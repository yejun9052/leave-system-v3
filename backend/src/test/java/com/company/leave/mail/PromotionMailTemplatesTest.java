package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.mail.MailLayout.Row;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 연차 사용 촉진 메일: 제목, 첫 문장(결재 대기 유무), 이월 정책에 따른 소멸 문구, 발송자 줄, 표 순서·굵은 줄, D-day, 바로가기.
 */
@DisplayName("연차 사용 촉진 메일 문구")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PromotionMailTemplatesTest {

    private static final LocalDate 기한 = LocalDate.of(2026, 11, 30);

    @Test
    void 제목에_남은_연차와_사용_기한을_넣는다() {
        PromotionMailTemplates.Mail mail = 메일("0", false, "김인사 (인사관리자)");

        assertThat(mail.subject()).isEqualTo("[연차관리] 연차 사용 촉진 안내 - 남은 연차 11일 (사용 기한 2026-11-30)");
        assertThat(mail.content().title()).isEqualTo("연차 사용 촉진 안내");
    }

    @Test
    void 결재_대기가_있으면_첫_문장에_대기_일수를_덧붙인다() {
        assertThat(메일("2.125", false, null).content().intro().get(0))
                .isEqualTo("홍길동님, 사용하지 않은 연차가 11일 남아 있습니다. (결재 대기 중인 휴가 2.125일)");
        assertThat(메일("0", false, null).content().intro().get(0))
                .isEqualTo("홍길동님, 사용하지 않은 연차가 11일 남아 있습니다.");
    }

    @Test
    void 이월을_허용하면_이월_한도를_넘는_연차만_소멸된다고_안내한다() {
        assertThat(메일("0", false, null).content().intro().get(1))
                .startsWith("사용 기한(2026-11-30)이 지나면 남은 연차는 소멸됩니다.");
        assertThat(메일("0", true, null).content().intro().get(1))
                .startsWith("사용 기한(2026-11-30)이 지나면 이월 한도를 넘는 연차는 소멸됩니다.");
    }

    @Test
    void 발송자가_있으면_머리줄에_넣고_없으면_넣지_않는다() {
        assertThat(메일("0", false, "자동 발송 (사용 기한 2개월 전 안내)").content().head())
                .containsExactly(Row.of("발송자", "자동 발송 (사용 기한 2개월 전 안내)"));
        assertThat(메일("0", false, null).content().head()).isEmpty();
    }

    @Test
    void 표는_정해진_순서이고_남은_연차와_사용_기한을_굵게_표시한다() {
        MailLayout.Content content = 메일("2.125", false, null).content();

        assertThat(content.rows()).extracting(Row::label).containsExactly(
                "이름", "소속", "연차 사용 기간", "부여 연차", "사용 연차", "결재 대기", "남은 연차", "사용 기한");
        assertThat(content.rows()).filteredOn(Row::strong).extracting(Row::label)
                .containsExactly("남은 연차", "사용 기한");
        assertThat(content.rows()).extracting(Row::value).containsExactly(
                "홍길동", "개발팀", "2025-12-01 ~ 2026-11-30", "15일", "4일", "2.125일", "11일",
                "2026-11-30 (1개월 28일 남음, D-59)");
    }

    @Test
    void 소속이_없으면_하이픈으로_표시한다() {
        PromotionMailTemplates.Notice n = new PromotionMailTemplates.Notice("홍길동", null, LocalDate.of(2025, 12, 1),
                기한, new BigDecimal("15"), new BigDecimal("4"), new BigDecimal("11"), BigDecimal.ZERO, "1개월 28일", 59);

        assertThat(PromotionMailTemplates.promotion(n, null, false, "http://localhost:5173").content().rows())
                .contains(Row.of("소속", "-"));
    }

    @Test
    void 사용_기한_당일은_D_day_로_표시한다() {
        assertThat(PromotionMailTemplates.dDay(0)).isEqualTo("D-day");
        assertThat(PromotionMailTemplates.dDay(59)).isEqualTo("D-59");
    }

    @Test
    void 바로가기는_내_휴가_화면이다() {
        MailLayout.Content content = 메일("0", false, null).content();

        assertThat(content.linkLabel()).isEqualTo("내 휴가 확인하기");
        assertThat(content.linkUrl()).isEqualTo("http://localhost:5173/my-leaves");
    }

    private static PromotionMailTemplates.Mail 메일(String pending, boolean carryOver, String sender) {
        PromotionMailTemplates.Notice n = new PromotionMailTemplates.Notice("홍길동", "개발팀", LocalDate.of(2025, 12, 1),
                기한, new BigDecimal("15"), new BigDecimal("4"), new BigDecimal("11"), new BigDecimal(pending),
                "1개월 28일", 59);
        return PromotionMailTemplates.promotion(n, sender, carryOver, "http://localhost:5173");
    }
}
