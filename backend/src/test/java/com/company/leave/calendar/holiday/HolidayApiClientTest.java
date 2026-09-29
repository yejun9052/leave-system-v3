package com.company.leave.calendar.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 공휴일 API 응답 파서 + 요청 URL. 응답 예시는 실제 API(2027년 3월·5월) 응답 형태 그대로.
 */
@DisplayName("공휴일 API 클라이언트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class HolidayApiClientTest {

    private static final String BASE = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService";

    private final HolidayApiClient client = 클라이언트("test-key");

    @Test
    void item이_배열이면_모두_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[
                {"dateKind":"01","dateName":"노동절","isHoliday":"Y","locdate":20270501,"seq":2},
                {"dateKind":"01","dateName":"대체공휴일(노동절)","isHoliday":"Y","locdate":20270503,"seq":1},
                {"dateKind":"01","dateName":"어린이날","isHoliday":"Y","locdate":20270505,"seq":1}
                ]},"numOfRows":100,"pageNo":1,"totalCount":3}}}
                """;

        List<HolidayApiClient.HolidayItem> items = client.parse(body, 2027, 5);

        assertThat(items).extracting(HolidayApiClient.HolidayItem::date).containsExactly(
                LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 5));
        assertThat(items.get(1).name()).isEqualTo("대체공휴일(노동절)");
        assertThat(items).allMatch(HolidayApiClient.HolidayItem::holiday);
    }

    @Test
    void item이_하나면_객체로_와도_목록으로_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":
                {"dateKind":"01","dateName":"삼일절","isHoliday":"Y","locdate":20270301,"seq":1}
                },"numOfRows":100,"pageNo":1,"totalCount":1}}}
                """;

        List<HolidayApiClient.HolidayItem> items = client.parse(body, 2027, 3);

        assertThat(items).containsExactly(
                new HolidayApiClient.HolidayItem(LocalDate.of(2027, 3, 1), "삼일절", true));
    }

    @Test
    void 결과가_없어_items가_빈_문자열이면_빈_목록이다() {
        String body = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":"","numOfRows":100,"pageNo":1,"totalCount":0}}}
                """;

        assertThat(client.parse(body, 2027, 4)).isEmpty();
    }

    @Test
    void 공휴일이_아닌_특일은_holiday_false로_읽는다() {
        String body = """
                {"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
                {"dateName":"제헌절","isHoliday":"N","locdate":20270717}},"totalCount":1}}}
                """;

        assertThat(client.parse(body, 2027, 7)).singleElement()
                .extracting(HolidayApiClient.HolidayItem::holiday).isEqualTo(false);
    }

    @Test
    void 오류가_XML로_오면_예외를_던진다() {
        String body = "<OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>"
                + "<returnReasonCode>30</returnReasonCode><returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR"
                + "</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>";

        assertThatThrownBy(() -> client.parse(body, 2027, 1))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("JSON 아님");
    }

    @Test
    void resultCode가_00이_아니면_예외를_던진다() {
        String body = """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR."}}}
                """;

        assertThatThrownBy(() -> client.parse(body, 2027, 1))
                .isInstanceOf(HolidayApiException.class)
                .hasMessageContaining("resultCode=22");
    }

    @Test
    void 서비스_키의_더하기_슬래시_등호는_퍼센트_인코딩된다() {
        URI uri = 클라이언트("ab+cd/ef==").buildUri(2027, 3);

        assertThat(uri.toString())
                .contains("serviceKey=ab%2Bcd%2Fef%3D%3D")
                .contains("solYear=2027")
                .contains("solMonth=03")
                .contains("_type=json")
                .startsWith(BASE + "/getRestDeInfo?");
    }

    @Test
    void 로그용_URL은_서비스_키를_가린다() {
        URI uri = 클라이언트("secret-key-value").buildUri(2027, 3);

        assertThat(HolidayApiClient.mask(uri)).contains("serviceKey=****").doesNotContain("secret-key-value");
    }

    private static HolidayApiClient 클라이언트(String key) {
        return new HolidayApiClient(new HolidayApiProperties(BASE, key), RestClient.create());
    }
}
