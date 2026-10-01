package com.company.leave.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.audit.AuditLabels;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("목록 검색어")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SearchKeywordsTest {

    private static final Map<Role, String> ROLES = Arrays.stream(Role.values())
            .collect(Collectors.toMap(Function.identity(), Role::label));

    @Test
    void 검색어는_공백으로_나눠_소문자_단어로_만든다() {
        assertThat(SearchKeywords.tokens("  QA   팀장 qa ")).containsExactly("qa", "팀장");
        assertThat(SearchKeywords.tokens(" ")).isEmpty();
        assertThat(SearchKeywords.tokens(null)).isEmpty();
    }

    @Test
    void 한글_표시_이름에_단어가_들어_있으면_그_코드로_찾는다() {
        assertThat(SearchKeywords.codesMatching(AuditLabels.ACTIONS, "로그인")).containsExactly("LOGIN");
        assertThat(SearchKeywords.codesMatching(AuditLabels.ACTIONS, "로그")).containsExactlyInAnyOrder("LOGIN", "LOGOUT");
        assertThat(SearchKeywords.codesMatching(AuditLabels.ACTIONS, "강제취소")).containsExactly("force_cancel");
        assertThat(SearchKeywords.codesMatching(AuditLabels.RESOURCES, "휴가"))
                .containsExactlyInAnyOrder("leave-requests", "leave-types");
        assertThat(SearchKeywords.codesMatching(ROLES, "관리자")).containsExactlyInAnyOrder(Role.SYSTEM_ADMIN, Role.HR_ADMIN);
        assertThat(SearchKeywords.codesMatching(ROLES, "팀장")).containsExactly(Role.TEAM_LEAD);
        assertThat(SearchKeywords.codesMatching(ROLES, "없는말")).isEmpty();
    }

    @Test
    void 상태는_재직_휴직_퇴사로_찾는다() {
        Map<EmployeeStatus, String> statuses = Arrays.stream(EmployeeStatus.values())
                .collect(Collectors.toMap(Function.identity(), EmployeeStatus::label));
        assertThat(SearchKeywords.codesMatching(statuses, "퇴사")).containsExactly(EmployeeStatus.RESIGNED);
        assertThat(SearchKeywords.codesMatching(statuses, "직")).containsExactlyInAnyOrder(
                EmployeeStatus.ACTIVE, EmployeeStatus.ON_LEAVE);
    }

    @Test
    void LIKE_와일드카드_문자는_글자_그대로_찾도록_이스케이프한다() {
        assertThat(SearchKeywords.likePattern("50%")).isEqualTo("%50\\%%");
        assertThat(SearchKeywords.likePattern("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void 이벤트_로그_응답에는_한글_표시_이름이_함께_간다() {
        assertThat(AuditLabels.action("LOGOUT")).isEqualTo("로그아웃");
        assertThat(AuditLabels.action("unknown_action")).isEqualTo("unknown_action");
        assertThat(AuditLabels.resource("leave-requests")).isEqualTo("휴가 신청");
        assertThat(AuditLabels.resource(null)).isNull();
    }
}
