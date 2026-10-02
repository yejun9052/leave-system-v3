package com.company.leave.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("연차 촉진 자동 발송 설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeavePolicyAutomationTest {

    @Test
    void 기본은_꺼져_있고_발송_시기는_6개월_전과_2개월_전이다() {
        LeavePolicy policy = LeavePolicy.createDefault();

        assertThat(policy.isPromotionEnabled()).isFalse();
        assertThat(policy.getPromotionMonths()).containsExactly(6, 2);
    }

    @Test
    void 발송_시기는_겹치지_않게_큰_값부터_저장한다() {
        LeavePolicy policy = LeavePolicy.createDefault();

        policy.applyAutomation(true, List.of(1, 3, 3, 6));

        assertThat(policy.isPromotionEnabled()).isTrue();
        assertThat(policy.getPromotionMonths()).containsExactly(6, 3, 1);
    }

    @Test
    void 발송_시기는_1개월에서_6개월_전_사이이고_하나_이상이어야_한다() {
        LeavePolicy policy = LeavePolicy.createDefault();

        assertThatThrownBy(() -> policy.applyAutomation(true, List.of(7))).hasMessageContaining("1~6개월");
        assertThatThrownBy(() -> policy.applyAutomation(true, List.of(0))).hasMessageContaining("1~6개월");
        assertThatThrownBy(() -> policy.applyAutomation(false, List.of())).hasMessageContaining("하나 이상");
        assertThat(policy.getPromotionMonths()).containsExactly(6, 2);
    }
}
