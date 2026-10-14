package com.company.leave.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.GlobalExceptionHandler;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("요청 본문 크기 제한(파일 업로드가 아닌 요청 1MB)")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RequestBodyLimitFilterTest {

    private static final int LIMIT = (int) RequestBodyLimitFilter.MAX_BODY_BYTES;

    @RestController
    static class TestController {

        @PostMapping("/api/echo")
        ApiResponse<Integer> echo(@RequestBody Map<String, Object> body) {
            return ApiResponse.ok(String.valueOf(body.get("text")).length());
        }

        @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        ApiResponse<Long> upload(@RequestPart MultipartFile file) {
            return ApiResponse.ok(file.getSize());
        }

        @GetMapping("/api/ping")
        ApiResponse<String> ping() {
            return ApiResponse.ok("ok");
        }
    }

    /** 길이를 미리 알리지 않는 요청(chunked)처럼 Content-Length 를 감춘다 */
    private static final Filter HIDE_CONTENT_LENGTH = (request, response, chain) ->
            chain.doFilter(new HttpServletRequestWrapper((HttpServletRequest) request) {
                @Override
                public int getContentLength() {
                    return -1;
                }

                @Override
                public long getContentLengthLong() {
                    return -1;
                }

                @Override
                public String getHeader(String name) {
                    return "Content-Length".equalsIgnoreCase(name) ? null : super.getHeader(name);
                }
            }, response);

    private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter(JsonMapper.builder().build());

    private MockMvc mvc(Filter... before) {
        Filter[] filters = new Filter[before.length + 1];
        System.arraycopy(before, 0, filters, 0, before.length);
        filters[before.length] = filter;
        return MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(filters)
                .build();
    }

    /** {"text":"aaa…"} 전체 크기가 약 size 바이트인 JSON */
    private static String json(int size) {
        return "{\"text\":\"" + "a".repeat(size - 12) + "\"}";
    }

    @Test
    void 길이를_알린_1MB_넘는_JSON은_읽기_전에_413() throws Exception {
        mvc().perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(json(LIMIT + 100)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error.code").value("REQUEST_TOO_LARGE"))
                .andExpect(jsonPath("$.error.message").value("요청이 너무 큽니다. 입력한 내용을 줄여 다시 시도해 주세요."));
    }

    @Test
    void 길이를_알리지_않은_1MB_넘는_JSON은_읽다가_끊고_413() throws Exception {
        mvc(HIDE_CONTENT_LENGTH)
                .perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(json(LIMIT + 100)))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.error.code").value("REQUEST_TOO_LARGE"));
    }

    @Test
    void 길이를_알리지_않은_30MB_JSON도_413() throws Exception {
        mvc(HIDE_CONTENT_LENGTH)
                .perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(json(30 * LIMIT)))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void 크기가_1MB_이하인_JSON은_그대로_처리한다() throws Exception {
        mvc().perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(json(LIMIT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(LIMIT - 12));
    }

    @Test
    void 길이를_알리지_않아도_1MB_이하면_처리한다() throws Exception {
        mvc(HIDE_CONTENT_LENGTH)
                .perform(post("/api/echo").contentType(MediaType.APPLICATION_JSON).content(json(LIMIT)))
                .andExpect(status().isOk());
    }

    @Test
    void 엑셀_업로드_multipart는_1MB가_넘어도_막지_않는다() throws Exception {
        byte[] excel = new byte[2 * LIMIT];
        mvc().perform(multipart("/api/upload").file(new MockMultipartFile("file", "users.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2 * LIMIT));
    }

    @Test
    void 본문_없는_요청은_통과한다() throws Exception {
        mvc().perform(get("/api/ping")).andExpect(status().isOk());
    }
}
