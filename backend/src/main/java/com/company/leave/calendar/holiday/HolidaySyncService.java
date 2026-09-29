package com.company.leave.calendar.holiday;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.leave.HolidayImpactService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공휴일 API → holidays 테이블 동기화.
 * <ol>
 *   <li>1~12월을 모두 먼저 받는다. 한 달이라도 실패하면 DB 를 전혀 건드리지 않는다(부분 저장 금지)</li>
 *   <li>같은 날짜의 여러 공휴일은 이름을 합쳐 한 행으로(holiday_date UNIQUE)</li>
 *   <li>한 트랜잭션에서 추가·이름 변경. API 에 없는 기존 날짜는 지우지 않고 경고만</li>
 *   <li>새로 추가된 날짜로 기존 휴가 재계산(HolidayImpactService, 같은 트랜잭션)</li>
 * </ol>
 */
@Service
public class HolidaySyncService {

    private static final Logger log = LoggerFactory.getLogger(HolidaySyncService.class);
    /** holidays.name 컬럼 길이. */
    static final int NAME_MAX_LENGTH = 60;

    private final HolidayApiClient apiClient;
    private final HolidayRepository holidayRepository;
    private final HolidayImpactService impactService;
    private final TransactionTemplate transactionTemplate;

    public HolidaySyncService(HolidayApiClient apiClient,
                              HolidayRepository holidayRepository,
                              HolidayImpactService impactService,
                              PlatformTransactionManager transactionManager) {
        this.apiClient = apiClient;
        this.holidayRepository = holidayRepository;
        this.impactService = impactService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public record NamedDate(LocalDate date, String name) {
    }

    public record Renamed(LocalDate date, String before, String after) {
    }

    /** 동기화 결과 요약. */
    public record SyncResult(int year,
                             List<NamedDate> added,
                             List<Renamed> renamed,
                             List<NamedDate> missingInApi,
                             int adjustedRequests,
                             BigDecimal restoredDays) {
    }

    public boolean isConfigured() {
        return apiClient.isConfigured();
    }

    /** 해당 연도 동기화. API 실패 시 HolidayApiException(DB 변경 없음). */
    public SyncResult sync(int year) {
        if (!apiClient.isConfigured()) {
            throw new HolidayApiException("공휴일 API 키(app.holiday-api.service-key)가 설정되지 않았습니다.");
        }
        Map<LocalDate, String> fromApi = fetchYear(year); // 트랜잭션 밖: 전부 성공해야 다음 단계
        SyncResult result = transactionTemplate.execute(status -> apply(year, fromApi));
        log.info("공휴일 동기화 완료 {}년: 추가 {}건, 이름 변경 {}건, API에 없는 기존 날짜 {}건, 휴가 조정 {}건(환원 {}일)",
                year, result.added().size(), result.renamed().size(), result.missingInApi().size(),
                result.adjustedRequests(), result.restoredDays().stripTrailingZeros().toPlainString());
        return result;
    }

    /** 올해와 내년을 차례로 동기화. 한 해가 실패해도 다른 해는 진행하고, 실패는 로그로 남긴다. */
    public List<SyncResult> syncCurrentAndNextYear() {
        int thisYear = LocalDate.now().getYear();
        List<SyncResult> results = new ArrayList<>();
        for (int year : new int[] {thisYear, thisYear + 1}) {
            try {
                results.add(sync(year));
            } catch (RuntimeException ex) {
                log.warn("공휴일 동기화 실패 {}년: {}", year, ex.getMessage());
            }
        }
        return results;
    }

    /** 12개월 조회 → 날짜별 이름 합치기(isHoliday=Y 만). */
    Map<LocalDate, String> fetchYear(int year) {
        Map<LocalDate, Set<String>> byDate = new TreeMap<>();
        for (int month = 1; month <= 12; month++) {
            for (HolidayApiClient.HolidayItem item : apiClient.fetchMonth(year, month)) {
                if (item.holiday() && item.date().getYear() == year) {
                    byDate.computeIfAbsent(item.date(), d -> new LinkedHashSet<>()).add(item.name());
                }
            }
        }
        Map<LocalDate, String> merged = new TreeMap<>();
        byDate.forEach((date, names) -> merged.put(date, truncate(String.join(", ", names))));
        return merged;
    }

    private SyncResult apply(int year, Map<LocalDate, String> fromApi) {
        Map<LocalDate, Holiday> existing = holidayRepository
                .findByDateBetweenOrderByDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .collect(Collectors.toMap(Holiday::getDate, Function.identity()));

        Map<LocalDate, String> added = new TreeMap<>();
        List<Renamed> renamed = new ArrayList<>();
        fromApi.forEach((date, name) -> {
            Holiday holiday = existing.get(date);
            if (holiday == null) {
                holidayRepository.save(new Holiday(date, name));
                added.put(date, name);
            } else if (!holiday.getName().equals(name)) {
                renamed.add(new Renamed(date, holiday.getName(), name));
                holiday.rename(name);
            }
        });
        List<NamedDate> missing = existing.values().stream()
                .filter(h -> !fromApi.containsKey(h.getDate()))
                .map(h -> new NamedDate(h.getDate(), h.getName()))
                .toList();
        if (!missing.isEmpty()) {
            log.warn("공휴일 API 응답에 없는 기존 날짜(삭제하지 않음) {}년: {}", year, missing);
        }

        HolidayImpactService.ImpactSummary impact = impactService.applyNewHolidays(added);
        return new SyncResult(year,
                added.entrySet().stream().map(e -> new NamedDate(e.getKey(), e.getValue())).toList(),
                renamed, missing, impact.adjustedRequests(), impact.restoredDays());
    }

    private static String truncate(String name) {
        return name.length() <= NAME_MAX_LENGTH ? name : name.substring(0, NAME_MAX_LENGTH);
    }
}
