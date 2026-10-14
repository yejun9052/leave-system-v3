package com.company.leave.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("경로에 인코딩할 필요 없는 글자를 인코딩한 요청 거부")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EncodedPathRejectFilterTest {

    private final EncodedPathRejectFilter filter = new EncodedPathRejectFilter(JsonMapper.builder().build());

    @ParameterizedTest
    @ValueSource(strings = {
        "/%61pi/employees",
        "/%41PI/employees",
        "/api/employees/%65xport",
        "/api/backups/a.dump/%64ownload",
        "/api/employees/%32",
        "/api/leave%2Drequests",
        "/api/x%5Fy",
        "/api/x%7Ey",
    })
    void 영문_숫자_기호를_인코딩한_경로는_400(String uri) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = 실행(uri, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("INVALID_INPUT").contains("올바르지 않은 요청 주소입니다.");
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/employees",
        "/",
        "/assets/index-AbC123.js",
        "/api/holidays/2026-01-01",
        // 한글·공백·예약 문자의 인코딩은 막지 않는다
        "/api/departments/%EA%B0%9C%EB%B0%9C",
        "/api/files/a%20b",
        "/api/x%2Cy",
        // 끝이 잘린 %는 판단하지 않는다(서버가 따로 거부)
        "/api/x%6",
    })
    void 보통_경로는_통과한다(String uri) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        실행(uri, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 쿼리_문자열은_보지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        request.setQueryString("keyword=%61dmin");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletResponse 실행(String uri, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", uri), response, chain);
        return response;
    }
}
