package com.company.leave.calendar.holiday;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한국천문연구원 특일 정보 API(getRestDeInfo) 클라이언트. 월 단위로 조회한다.
 *
 * <p>응답 특이점 처리:
 * <ol>
 *   <li>item 이 1개면 배열이 아니라 객체 하나로 온다 → ACCEPT_SINGLE_VALUE_AS_ARRAY</li>
 *   <li>결과가 0개면 items 가 빈 문자열("")로 올 수 있다 → ACCEPT_EMPTY_STRING_AS_NULL_OBJECT → 빈 목록</li>
 *   <li>오류는 _type=json 이어도 XML 로 올 수 있다 → 파싱 실패 시 응답 앞부분을 로그에 남기고 예외</li>
 * </ol>
 */
@Component
public class HolidayApiClient {

    private static final Logger log = LoggerFactory.getLogger(HolidayApiClient.class);
    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    private static final int LOG_BODY_PREFIX = 300;

    private final HolidayApiProperties properties;
    private final RestClient restClient;
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Autowired
    public HolidayApiClient(HolidayApiProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory()).build());
    }

    HolidayApiClient(HolidayApiProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    /** 공휴일 1건(API 원본 기준). holiday=false 는 공휴일이 아닌 기념일 등. */
    public record HolidayItem(LocalDate date, String name, boolean holiday) {
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    /** 해당 연·월의 특일 목록. resultCode 가 "00" 이 아니거나 응답을 해석할 수 없으면 예외. */
    public List<HolidayItem> fetchMonth(int year, int month) {
        URI uri = buildUri(year, month);
        log.info("공휴일 API 호출: {}", mask(uri));
        String body;
        try {
            body = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException ex) {
            throw new HolidayApiException("공휴일 API 호출 실패(" + year + "-" + month + "): " + ex.getMessage(), ex);
        }
        return parse(body, year, month);
    }

    /**
     * 서비스 키는 URI 변수로 넘겨 엄격 인코딩한다(+ → %2B, / → %2F, = → %3D).
     * 쿼리 값에 그대로 넣으면 '+' 가 인코딩되지 않아 서버에서 공백으로 해석될 수 있다.
     */
    URI buildUri(int year, int month) {
        return UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/getRestDeInfo")
                .queryParam("serviceKey", "{serviceKey}")
                .queryParam("solYear", year)
                .queryParam("solMonth", String.format("%02d", month))
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .encode()
                .buildAndExpand(properties.serviceKey())
                .toUri();
    }

    List<HolidayItem> parse(String body, int year, int month) {
        ApiResponse response;
        try {
            response = mapper.readValue(body == null ? "" : body, ApiResponse.class);
        } catch (JacksonException ex) {
            String head = body == null ? "(빈 응답)" : body.substring(0, Math.min(LOG_BODY_PREFIX, body.length()));
            log.warn("공휴일 API 응답을 JSON 으로 해석할 수 없음({}-{}): {}", year, month, head);
            throw new HolidayApiException("공휴일 API 응답 형식 오류(JSON 아님): " + year + "-" + month, ex);
        }
        if (response == null || response.response() == null || response.response().header() == null) {
            throw new HolidayApiException("공휴일 API 응답에 header 가 없음: " + year + "-" + month);
        }
        Header header = response.response().header();
        if (!"00".equals(header.resultCode())) {
            throw new HolidayApiException("공휴일 API 오류 resultCode=" + header.resultCode()
                    + " (" + header.resultMsg() + "): " + year + "-" + month);
        }
        Body resultBody = response.response().body();
        if (resultBody == null || resultBody.items() == null || resultBody.items().item() == null) {
            return List.of();
        }
        return resultBody.items().item().stream()
                .map(i -> new HolidayItem(
                        LocalDate.parse(String.valueOf(i.locdate()), LOCDATE),
                        i.dateName() == null ? "" : i.dateName().trim(),
                        "Y".equalsIgnoreCase(i.isHoliday())))
                .toList();
    }

    /** 로그용: serviceKey 값을 가린 요청 URL. */
    static String mask(URI uri) {
        return uri.toString().replaceAll("serviceKey=[^&]*", "serviceKey=****");
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    // --- 응답 구조 ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(Response response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Header header, Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body(Items items, Integer totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Items(List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(long locdate, String dateName, String isHoliday) {
    }
}
