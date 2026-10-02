package com.company.leave.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.notification.domain.Notification;
import com.company.leave.notification.dto.NotificationResponse;
import com.company.leave.notification.repository.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 앱 알림: 저장, 최신순 목록(개수 제한), 읽지 않은 개수, 모두 읽음.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("앱 알림")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NotificationServiceTest {

    @Mock private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository);
    }

    @Test
    void 알림을_보내면_읽지_않은_상태로_저장한다() {
        service.notify(10L, "LEAVE_APPROVED", "휴가 승인", "2026-10-05 연차가 승인되었습니다.", "/my-leaves");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        Notification n = saved.getValue();
        assertThat(n.getEmployeeId()).isEqualTo(10L);
        assertThat(n.getType()).isEqualTo("LEAVE_APPROVED");
        assertThat(n.getTitle()).isEqualTo("휴가 승인");
        assertThat(n.getMessage()).isEqualTo("2026-10-05 연차가 승인되었습니다.");
        assertThat(n.getLink()).isEqualTo("/my-leaves");
        assertThat(n.isRead()).isFalse();
        assertThat(n.getCreatedAt()).isNotNull();
    }

    @Test
    void 목록은_첫_페이지에서_요청한_개수만큼_최신순으로_가져온다() {
        Notification 읽음 = 알림(1L, "첫 알림");
        읽음.markRead();
        when(repository.findByEmployeeIdOrderByCreatedAtDesc(10L, PageRequest.of(0, 20)))
                .thenReturn(List.of(알림(2L, "둘째 알림"), 읽음));

        List<NotificationResponse> list = service.list(10L, 20);

        assertThat(list).extracting(NotificationResponse::id, NotificationResponse::title, NotificationResponse::read)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(2L, "둘째 알림", false),
                        org.assertj.core.groups.Tuple.tuple(1L, "첫 알림", true));
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByEmployeeIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.eq(10L), page.capture());
        assertThat(page.getValue().getPageNumber()).isZero();
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void 읽지_않은_개수는_본인_알림_중_읽지_않은_것을_센다() {
        when(repository.countByEmployeeIdAndReadFalse(10L)).thenReturn(4L);

        assertThat(service.unreadCount(10L)).isEqualTo(4L);
    }

    @Test
    void 모두_읽음은_본인_알림만_읽음으로_바꾼다() {
        service.markAllRead(10L);

        verify(repository).markAllRead(10L);
    }

    private static Notification 알림(Long id, String title) {
        Notification n = new Notification(10L, "INFO", title, null, null);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }
}
