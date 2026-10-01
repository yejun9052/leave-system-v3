package com.company.leave.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("페이지 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PageResponseTest {

    @Test
    void Page_의_목록과_전체_건수_페이지_정보를_그대로_옮긴다() {
        var page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("c", "d");
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    void JSON_에는_화면이_쓰는_다섯_필드만_나간다() {
        var page = new PageImpl<>(List.of("a"), PageRequest.of(0, 20), 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> json = JsonMapper.builder().build()
                .convertValue(PageResponse.from(page), Map.class);

        assertThat(json).containsOnlyKeys("content", "totalElements", "totalPages", "number", "size");
    }

    @Test
    void 빈_목록이면_전체_건수와_페이지_수가_0이다() {
        var page = new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
