package com.company.leave.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.audit.domain.AuditLog;
import com.company.leave.audit.dto.AuditLogResponse;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.security.UserPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 이벤트 로그 기록과 검색: 로그인한 사용자를 처리자로, 로그인 전·배치는 SYSTEM 으로 남기고,
 * 처리자를 직접 줄 수도 있다. 검색은 최신순(같은 시각이면 id 역순)으로 한글 표시 이름을 붙여 돌려준다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("이벤트 로그")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AuditServiceTest {

    @Mock private AuditLogRepository repository;

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(repository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 로그인한_사용자를_처리자로_남긴다() {
        로그인(7L, "김인사");

        service.record("approve", "leave-requests", "12", "/api/leave-requests/12/approve", true);

        AuditLog log = 저장된_로그();
        assertThat(log.getActorId()).isEqualTo(7L);
        assertThat(log.getActorName()).isEqualTo("김인사");
        assertThat(log.getAction()).isEqualTo("approve");
        assertThat(log.getEntityType()).isEqualTo("leave-requests");
        assertThat(log.getEntityId()).isEqualTo("12");
        assertThat(log.getDetail()).isEqualTo("/api/leave-requests/12/approve");
        assertThat(log.isSuccess()).isTrue();
    }

    @Test
    void 로그인_전이나_배치에서는_SYSTEM_으로_남긴다() {
        service.record("POST", "leave", null, "배치", false);

        AuditLog log = 저장된_로그();
        assertThat(log.getActorId()).isNull();
        assertThat(log.getActorName()).isEqualTo("SYSTEM");
        assertThat(log.isSuccess()).isFalse();
    }

    @Test
    void 처리자를_직접_주면_로그인_사용자와_상관없이_그대로_남긴다() {
        로그인(7L, "김인사");

        service.record(3L, "홍길동", "LOGIN", "auth", "3", "로그인 실패", false);

        AuditLog log = 저장된_로그();
        assertThat(log.getActorId()).isEqualTo(3L);
        assertThat(log.getActorName()).isEqualTo("홍길동");
        assertThat(log.getAction()).isEqualTo("LOGIN");
        assertThat(log.isSuccess()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 검색은_최신순이고_같은_시각이면_id_역순이며_한글_표시_이름을_붙인다() {
        AuditLog log = new AuditLog(7L, "김인사", "force_cancel", "leave-requests", "12", "사유: 일정 변경", true);
        ReflectionTestUtils.setField(log, "id", 99L);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogResponse> page = service.search("강제", PageRequest.of(2, 30, Sort.by("actorName")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(30);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        AuditLogResponse r = page.getContent().get(0);
        assertThat(r.id()).isEqualTo(99L);
        assertThat(r.actionLabel()).isEqualTo("강제 취소");
        assertThat(r.entityLabel()).isEqualTo("휴가 신청");
    }

    // --- helpers ---

    private static void 로그인(Long id, String name) {
        Employee e = Employee.builder().email("hr@company.com").passwordHash("h").name(name)
                .roles(Set.of(Role.EMPLOYEE, Role.HR_ADMIN)).build();
        ReflectionTestUtils.setField(e, "id", id);
        UserPrincipal principal = UserPrincipal.from(e);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    private AuditLog 저장된_로그() {
        ArgumentCaptor<AuditLog> saved = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }
}
