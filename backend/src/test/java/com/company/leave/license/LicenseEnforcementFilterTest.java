package com.company.leave.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 라이선스 집행 필터: 라이선스가 유효하지 않으면 라이선스 상태 조회를 뺀 모든 API 를 403 으로 막는다.
 * 화면 파일(API 가 아닌 주소)은 막지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("라이선스 집행 필터")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LicenseEnforcementFilterTest {

    @Mock
    private LicenseService licenseService;

    private LicenseEnforcementFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LicenseEnforcementFilter(licenseService, new ObjectMapper());
        lenient().when(licenseService.status()).thenReturn(new LicenseService.LicenseStatus(
                true, false, "라이선스가 만료되었습니다.", "테스트 회사", LocalDate.of(2026, 1, 1), 0, 0));
    }

    @Test
    void 라이선스가_유효하지_않으면_API를_403과_사유로_막는다() throws Exception {
        when(licenseService.valid()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(요청("/api/employees"), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .contains("\"LICENSE_INVALID\"")
                .contains("라이선스가 유효하지 않습니다: 라이선스가 만료되었습니다.");
    }

    @Test
    void 라이선스가_유효하지_않아도_라이선스_상태_조회는_통과시킨다() throws Exception {
        lenient().when(licenseService.valid()).thenReturn(false);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(요청("/api/license/status"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void 라이선스가_유효하지_않아도_화면_파일은_막지_않는다() throws Exception {
        lenient().when(licenseService.valid()).thenReturn(false);
        MockFilterChain root = new MockFilterChain();
        MockFilterChain asset = new MockFilterChain();

        filter.doFilter(요청("/"), new MockHttpServletResponse(), root);
        filter.doFilter(요청("/assets/index.js"), new MockHttpServletResponse(), asset);

        assertThat(root.getRequest()).isNotNull();
        assertThat(asset.getRequest()).isNotNull();
    }

    @Test
    void 라이선스가_유효하면_API를_그대로_통과시킨다() throws Exception {
        when(licenseService.valid()).thenReturn(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(요청("/api/employees"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest 요청(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
