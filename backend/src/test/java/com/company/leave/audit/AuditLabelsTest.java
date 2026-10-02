package com.company.leave.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 이벤트 로그의 동작·대상 한글 표시 이름. 모르는 코드는 코드 그대로 보여 준다.
 */
@DisplayName("이벤트 로그 한글 표시 이름")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditLabelsTest {

    @Test
    void 동작_코드를_한글로_바꾼다() {
        assertThat(AuditLabels.action("POST")).isEqualTo("생성");
        assertThat(AuditLabels.action("cancel_reject")).isEqualTo("취소 반려");
        assertThat(AuditLabels.action("self_approve")).isEqualTo("자가 승인");
        assertThat(AuditLabels.action("LOGIN")).isEqualTo("로그인");
    }

    @Test
    void 대상_코드를_한글로_바꾼다() {
        assertThat(AuditLabels.resource("leave-requests")).isEqualTo("휴가 신청");
        assertThat(AuditLabels.resource("policy")).isEqualTo("정책");
    }

    @Test
    void 모르는_코드는_그대로_보여주고_대상이_없으면_null_이다() {
        assertThat(AuditLabels.action("unknown_action")).isEqualTo("unknown_action");
        assertThat(AuditLabels.resource("holidays")).isEqualTo("holidays");
        assertThat(AuditLabels.resource(null)).isNull();
    }
}
