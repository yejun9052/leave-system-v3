package com.company.leave.common.dto;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 신청·등록 한 건의 기간(시작일~종료일, 양 끝 포함). 달력에서 여러 날짜를 고르면 화면이 이어진 묶음마다 하나씩 만들어 보낸다
 * (휴가·블랙아웃은 사이에 주말·공휴일만 있으면 이어진 것, 일정은 달력 날짜가 바로 이어져야 한다. 묶는 규칙은 화면 몫).
 * 서버는 묶음마다 기존 단건 검증을 그대로 돌리고, 하나라도 걸리면 전부 저장하지 않는다.
 */
public record DateSpan(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {

    /** 한 번에 보낼 수 있는 묶음 수 */
    public static final int MAX_SPANS = 31;

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("M/d");

    /** 쿼리 문자열 하나: "2026-10-11~2026-10-13", 하루면 "2026-10-15". */
    public static DateSpan parse(String text) {
        String[] parts = text == null ? new String[0] : text.trim().split("~");
        try {
            if (parts.length == 1 && !parts[0].isBlank()) {
                LocalDate day = LocalDate.parse(parts[0].trim());
                return new DateSpan(day, day);
            }
            if (parts.length == 2) {
                return new DateSpan(LocalDate.parse(parts[0].trim()), LocalDate.parse(parts[1].trim()));
            }
        } catch (DateTimeParseException ignored) {
            // 아래에서 같은 안내
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "날짜 형식이 올바르지 않습니다: " + text);
    }

    public static List<DateSpan> parseAll(List<String> texts) {
        return texts == null ? List.of() : texts.stream().map(DateSpan::parse).toList();
    }

    /** 화면·안내 문구용: "10/11 ~ 10/13", 하루면 "10/11". */
    public String label() {
        return startDate.equals(endDate)
                ? startDate.format(LABEL)
                : startDate.format(LABEL) + " ~ " + endDate.format(LABEL);
    }

    public static String labels(List<DateSpan> spans) {
        return spans.stream().map(DateSpan::label).collect(Collectors.joining(", "));
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !from.isAfter(endDate) && !to.isBefore(startDate);
    }

    /**
     * 시작일 순으로 정렬하고 검사한다: 비었거나 너무 많음, 종료일이 시작일보다 빠름, 묶음끼리 겹침.
     */
    public static List<DateSpan> normalize(List<DateSpan> spans) {
        if (spans == null || spans.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "날짜를 골라 주세요.");
        }
        if (spans.size() > MAX_SPANS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한 번에 최대 " + MAX_SPANS + "묶음까지 신청할 수 있습니다.");
        }
        List<DateSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparing(DateSpan::startDate));
        DateSpan prev = null;
        for (DateSpan s : sorted) {
            if (s.startDate() == null || s.endDate() == null || s.endDate().isBefore(s.startDate())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "종료일이 시작일보다 빠릅니다.");
            }
            if (prev != null && !s.startDate().isAfter(prev.endDate())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "고른 날짜가 겹칩니다: " + prev.label() + ", " + s.label());
            }
            prev = s;
        }
        return List.copyOf(sorted);
    }

    /** 여러 묶음 중 하나에서 걸린 규칙이면 어느 날짜 때문인지 앞에 붙인다("10/13: 잔여 연차가 부족합니다…"). */
    public BusinessException label(BusinessException ex, int spanCount) {
        return spanCount <= 1 ? ex : new BusinessException(ex.getErrorCode(), label() + ": " + ex.getMessage());
    }
}
