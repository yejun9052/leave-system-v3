package com.company.leave.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.leave.mail.MailLayout.Content;
import com.company.leave.mail.MailLayout.Row;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayName("안내 메일 모양")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MailLayoutTest {

    private final Content content = new Content("휴가 등록 안내",
            List.of("인사과장님이 휴가를 등록했습니다.", "아래 휴가 내역은 승인 완료 상태입니다."),
            List.of(Row.of("처리자", "인사과장 (인사관리자)")),
            List.of(Row.of("신청자", "연구소장"), Row.of("신청 사유", "<b>가족</b> & 여행"),
                    Row.strong("처리 상태", "승인 완료")),
            "내 휴가 확인하기", "http://localhost:5173/my-leaves");

    @Test
    void 일반_텍스트는_제목_안내_수신자와_처리자_내용_링크_순서다() {
        assertThat(content.withRecipient("연구소장").text()).isEqualTo("""
                휴가 등록 안내

                인사과장님이 휴가를 등록했습니다.
                아래 휴가 내역은 승인 완료 상태입니다.

                수신자: 연구소장
                처리자: 인사과장 (인사관리자)

                신청자: 연구소장
                신청 사유: <b>가족</b> & 여행
                처리 상태: 승인 완료

                내 휴가 확인하기: http://localhost:5173/my-leaves
                """);
    }

    @Test
    void HTML_은_표와_링크로_그리고_값은_이스케이프한다() {
        String html = content.withRecipient("연구소장").html();

        assertThat(html)
                .contains(">휴가 등록 안내</h1>")
                .contains("인사과장님이 휴가를 등록했습니다.<br>아래 휴가 내역은 승인 완료 상태입니다.")
                .contains(">수신자</th>")
                .contains("&lt;b&gt;가족&lt;/b&gt; &amp; 여행")
                .doesNotContain("<b>가족</b>")
                .contains("<a href=\"http://localhost:5173/my-leaves\"")
                .contains(">내 휴가 확인하기</a>");
        // 처리 상태는 굵게, 내용 첫 줄(신청자)은 위 칸(수신자·처리자)과 굵은 선으로 나뉜다
        assertThat(html).containsPattern("font-weight:700;[^>]*>승인 완료</td>");
        assertThat(html).containsPattern("border-top:2px solid #cbd5e1;[^>]*>신청자</th>");
    }
}
