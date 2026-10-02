package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.mail.AnnouncementMailTemplates.Change;
import com.company.leave.mail.AnnouncementMailTemplates.Mail;
import com.company.leave.mail.AnnouncementMailTemplates.Schedule;
import com.company.leave.mail.LeaveMailTemplates.Handler;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

@DisplayName("블랙아웃·일정 공지 메일")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnouncementMailTest {

    private static final String BASE = "https://leave.company.com";
    private static final LocalDate D1 = LocalDate.of(2026, 12, 28);
    private static final LocalDate D2 = LocalDate.of(2026, 12, 31);

    private static final String ALL = "재직 중인 전 직원";

    private final Handler 인사 = new Handler("인사관리자", "이인사");

    @Test
    void 블랙아웃_추가_메일에는_수신_범위_처리자_이름_기간이_들어간다() {
        Mail mail = AnnouncementMailTemplates.blackout(Change.CREATED, new Schedule("결산 주간", D1, D2, null), null,
                인사, ALL, BASE);

        assertThat(mail.subject()).isEqualTo("[연차관리] 연차 사용 금지 기간 추가 - 결산 주간 (2026-12-28 ~ 2026-12-31)");
        assertThat(mail.body())
                .startsWith("연차 사용 금지 기간 추가 안내\n\n연차 사용 금지 기간이 추가되었습니다.\n이 기간에는 연차를 신청할 수 없습니다.")
                .contains("수신자: 재직 중인 전 직원\n처리자: 이인사 (인사관리자)")
                .contains("이름: 결산 주간")
                .contains("기간: 2026-12-28 ~ 2026-12-31")
                .contains("연차 신청: 불가")
                .contains("캘린더 보기: https://leave.company.com/calendar")
                .doesNotContain("범위:");
    }

    @Test
    void 블랙아웃_변경_메일에는_변경_전_내용이_함께_들어간다() {
        Mail mail = AnnouncementMailTemplates.blackout(Change.UPDATED, new Schedule("결산 주간", D1, D2, null),
                new Schedule("결산", D1, D1, null), 인사, ALL, BASE);

        assertThat(mail.body()).contains("변경되었습니다").contains("변경 전 이름: 결산\n변경 전 기간: 2026-12-28\n");
    }

    @Test
    void 블랙아웃_삭제_메일은_다시_신청할_수_있다고_안내한다() {
        Mail mail = AnnouncementMailTemplates.blackout(Change.DELETED, new Schedule("결산 주간", D1, D2, null), null,
                인사, ALL, BASE);

        assertThat(mail.subject()).startsWith("[연차관리] 연차 사용 금지 기간 삭제 - 결산 주간");
        assertThat(mail.body()).contains("이 기간에도 연차를 신청할 수 있습니다").contains("연차 신청: 가능")
                .doesNotContain("변경 전");
    }

    @Test
    void 일정_메일에는_범위가_들어가고_범위가_바뀌면_변경_전_범위도_보인다() {
        Mail created = AnnouncementMailTemplates.event(Change.CREATED, new Schedule("워크숍", D1, D1, "QA팀 일정"), null,
                인사, "QA팀 소속 직원", BASE);
        Mail moved = AnnouncementMailTemplates.event(Change.UPDATED, new Schedule("워크숍", D1, D1, "전체 일정"),
                new Schedule("워크숍", D1, D1, "QA팀 일정"), 인사, ALL, BASE);

        assertThat(created.subject()).isEqualTo("[연차관리] 일정 추가 - 워크숍 (2026-12-28)");
        assertThat(created.body()).startsWith("일정 추가 안내\n\nQA팀 일정이 추가되었습니다.")
                .contains("수신자: QA팀 소속 직원").contains("범위: QA팀 일정").contains("처리 상태: 추가됨");
        assertThat(moved.body()).contains("전체 일정이 변경되었습니다.")
                .contains("범위: 전체 일정\n변경 전 일정: 워크숍\n변경 전 기간: 2026-12-28\n변경 전 범위: QA팀 일정");
    }

    @Test
    void HTML_본문도_같은_내용으로_만든다() {
        String html = AnnouncementMailTemplates.blackout(Change.CREATED, new Schedule("결산 <주간>", D1, D2, null), null,
                인사, ALL, BASE).content().html();

        assertThat(html).contains(">연차 사용 금지 기간 추가 안내</h1>").contains("결산 &lt;주간&gt;")
                .contains(">캘린더 보기</a>");
    }

    @Test
    void 받는_사람끼리_주소가_보이지_않게_숨은_참조로_한_통만_보낸다() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        AnnouncementMailService service = new AnnouncementMailService(sender,
                new AccountMailProperties("연차관리 <no-reply@company.com>", BASE));

        service.onAnnouncement(new AnnouncementMail(List.of("a@company.com", "b@company.com"), "제목", "본문", "<p>본문</p>"));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        assertThat(sent.getRecipients(Message.RecipientType.TO))
                .extracting(a -> ((InternetAddress) a).getAddress()).containsExactly("no-reply@company.com");
        assertThat(sent.getRecipients(Message.RecipientType.BCC))
                .extracting(a -> ((InternetAddress) a).getAddress()).containsExactly("a@company.com", "b@company.com");
    }

    @Test
    void 받는_사람이_없으면_보내지_않는다() {
        JavaMailSender sender = mock(JavaMailSender.class);
        new AnnouncementMailService(sender, new AccountMailProperties("no-reply@company.com", BASE))
                .onAnnouncement(new AnnouncementMail(List.of(), "제목", "본문", "<p>본문</p>"));

        verify(sender, never()).send(any(MimeMessage.class));
    }
}
