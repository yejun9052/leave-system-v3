package com.company.leave.config.init;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기본 정책, 휴가 종류, 공휴일을 시드한다.
 */
@Order(2)
@Component
public class LeaveDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LeaveDataInitializer.class);

    private final LeaveTypeRepository leaveTypeRepository;
    private final HolidayRepository holidayRepository;
    private final PolicyService policyService;
    private final ServiceAwardRuleRepository awardRuleRepository;
    private final SpecialLeaveRuleRepository specialRuleRepository;

    public LeaveDataInitializer(LeaveTypeRepository leaveTypeRepository,
                                HolidayRepository holidayRepository,
                                PolicyService policyService,
                                ServiceAwardRuleRepository awardRuleRepository,
                                SpecialLeaveRuleRepository specialRuleRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.holidayRepository = holidayRepository;
        this.policyService = policyService;
        this.awardRuleRepository = awardRuleRepository;
        this.specialRuleRepository = specialRuleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        policyService.getActivePolicy(); // 기본 정책 보장

        if (leaveTypeRepository.count() == 0) {
            leaveTypeRepository.saveAll(List.of(
                    type("ANNUAL", "연차", "1.0", true, false, true, "#4f46e5", 1),
                    type("HALF_AM", "오전 반차", "0.5", true, true, true, "#22c55e", 2),
                    type("HALF_PM", "오후 반차", "0.5", true, true, true, "#06b6d4", 3),
                    type("CONDOLENCE", "경조사 휴가", "0.0", true, false, false, "#f59e0b", 4),
                    type("SICK", "병가", "0.0", false, false, false, "#ef4444", 5),
                    type("OFFICIAL", "공가", "0.0", true, false, false, "#8b5cf6", 6)));
            log.info("기본 휴가 종류 6종 생성");
        }

        if (holidayRepository.count() == 0) {
            seedHolidays2026();
            log.info("2026년 공휴일 시드 완료");
        }

        if (awardRuleRepository.count() == 0) {
            awardRuleRepository.saveAll(List.of(
                    new ServiceAwardRule(5, new BigDecimal("3.0"), "5년 근속 포상"),
                    new ServiceAwardRule(10, new BigDecimal("5.0"), "10년 근속 포상"),
                    new ServiceAwardRule(20, new BigDecimal("10.0"), "20년 근속 포상")));
            log.info("장기근속 포상 규칙 시드 완료");
        }

        if (specialRuleRepository.count() == 0) {
            specialRuleRepository.saveAll(List.of(
                    new SpecialLeaveRule("본인 결혼", new BigDecimal("5.0"), "CONDOLENCE", 1),
                    new SpecialLeaveRule("배우자 출산", new BigDecimal("10.0"), "CONDOLENCE", 2),
                    new SpecialLeaveRule("자녀 결혼", new BigDecimal("1.0"), "CONDOLENCE", 3),
                    new SpecialLeaveRule("부모/배우자 사망", new BigDecimal("5.0"), "CONDOLENCE", 4),
                    new SpecialLeaveRule("조부모/형제자매 사망", new BigDecimal("3.0"), "CONDOLENCE", 5)));
            log.info("경조사 규칙 시드 완료");
        }
    }

    private LeaveType type(String code, String name, String deduct, boolean paid,
                           boolean half, boolean deductFromAnnual, String color, int sort) {
        return new LeaveType(code, name, new BigDecimal(deduct), paid, half, deductFromAnnual, color, sort);
    }

    private void seedHolidays2026() {
        save(LocalDate.of(2026, 1, 1), "신정");
        save(LocalDate.of(2026, 2, 16), "설날 연휴");
        save(LocalDate.of(2026, 2, 17), "설날");
        save(LocalDate.of(2026, 2, 18), "설날 연휴");
        save(LocalDate.of(2026, 3, 1), "삼일절");
        save(LocalDate.of(2026, 3, 2), "삼일절 대체공휴일");
        save(LocalDate.of(2026, 5, 5), "어린이날");
        save(LocalDate.of(2026, 5, 24), "부처님오신날");
        save(LocalDate.of(2026, 5, 25), "부처님오신날 대체공휴일");
        save(LocalDate.of(2026, 6, 6), "현충일");
        save(LocalDate.of(2026, 8, 15), "광복절");
        save(LocalDate.of(2026, 8, 17), "광복절 대체공휴일");
        save(LocalDate.of(2026, 9, 24), "추석 연휴");
        save(LocalDate.of(2026, 9, 25), "추석");
        save(LocalDate.of(2026, 9, 26), "추석 연휴");
        save(LocalDate.of(2026, 10, 3), "개천절");
        save(LocalDate.of(2026, 10, 5), "개천절 대체공휴일");
        save(LocalDate.of(2026, 10, 9), "한글날");
        save(LocalDate.of(2026, 12, 25), "크리스마스");
    }

    private void save(LocalDate date, String name) {
        if (!holidayRepository.existsByDate(date)) {
            holidayRepository.save(new Holiday(date, name));
        }
    }
}
