package com.company.leave.audit;

import com.company.leave.audit.domain.AuditLog;
import com.company.leave.common.search.SearchKeywords;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

/**
 * 이벤트 로그 검색 조건. 검색 단어마다 표의 어느 칸이든 맞으면 되고, 단어끼리는 모두 만족해야 한다.
 * <ul>
 *   <li>사용자(이름·이메일), 상세, 대상 ID: 부분 일치</li>
 *   <li>동작·대상: 코드 또는 한글 표시 이름(예: "로그인" → LOGIN, "휴가" → leave-requests·leave-types)</li>
 *   <li>결과: "성공" / "실패"</li>
 *   <li>날짜: 2026-10-01, 2026.10.01, 2026-10 (한국 시간 기준 그날·그달)</li>
 * </ul>
 */
final class AuditLogSearch {

    /** 화면이 한국 시간으로 보여 주므로 날짜 검색도 한국 시간 기준. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private AuditLogSearch() {
    }

    static Specification<AuditLog> of(String keyword) {
        List<String> tokens = SearchKeywords.tokens(keyword);
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            for (String token : tokens) {
                all.add(cb.or(anyColumn(root, cb, token).toArray(Predicate[]::new)));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
    }

    private static List<Predicate> anyColumn(Root<AuditLog> root, CriteriaBuilder cb, String token) {
        String like = SearchKeywords.likePattern(token);
        List<Predicate> or = new ArrayList<>();
        for (String column : List.of("actorName", "action", "entityType", "entityId", "detail")) {
            or.add(cb.like(cb.lower(root.get(column)), like, '\\'));
        }
        String id = token.startsWith("#") ? token.substring(1) : null;
        if (id != null && !id.isEmpty()) {
            or.add(cb.equal(root.get("entityId"), id));
        }
        Set<String> actions = SearchKeywords.codesMatching(AuditLabels.ACTIONS, token);
        if (!actions.isEmpty()) {
            or.add(root.get("action").in(actions));
        }
        Set<String> resources = SearchKeywords.codesMatching(AuditLabels.RESOURCES, token);
        if (!resources.isEmpty()) {
            or.add(root.get("entityType").in(resources));
        }
        if ("성공".contains(token)) {
            or.add(cb.isTrue(root.get("success")));
        }
        if ("실패".contains(token)) {
            or.add(cb.isFalse(root.get("success")));
        }
        Predicate date = dateRange(root, cb, token);
        if (date != null) {
            or.add(date);
        }
        return or;
    }

    /** 2026-10-01 · 2026.10.01 · 2026/10/01 → 그날, 2026-10 → 그달. 날짜가 아니면 null. */
    private static Predicate dateRange(Root<AuditLog> root, CriteriaBuilder cb, String token) {
        String normalized = token.replace('.', '-').replace('/', '-');
        if (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            LocalDate day = LocalDate.parse(normalized);
            return between(root, cb, day, day.plusDays(1));
        } catch (DateTimeParseException notDay) {
            try {
                YearMonth month = YearMonth.parse(normalized);
                return between(root, cb, month.atDay(1), month.plusMonths(1).atDay(1));
            } catch (DateTimeParseException notMonth) {
                return null;
            }
        }
    }

    private static Predicate between(Root<AuditLog> root, CriteriaBuilder cb, LocalDate from, LocalDate toExclusive) {
        return cb.and(
                cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay(ZONE).toInstant()),
                cb.lessThan(root.get("createdAt"), toExclusive.atStartOfDay(ZONE).toInstant()));
    }
}
