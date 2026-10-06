package com.company.leave.config.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.holiday.HolidayApiProperties;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.leave.domain.AnnualDeductionMode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveTypeRepository;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.ServiceAwardRule;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.ServiceAwardRuleRepository;
import com.company.leave.policy.repository.SpecialLeaveRuleRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

/**
 * 기본 데이터 시드: 기본 정책 보장, 비어 있을 때만 휴가 종류 7종·근속 포상·경조사 규정,
 * 공휴일 API 키가 없고 공휴일이 비어 있을 때만 2026년 공휴일(하드코딩 대체 데이터).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("기본 휴가 데이터 시드")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveDataInitializerTest {

    @Mock private LeaveTypeRepository leaveTypeRepository;
    @Mock private HolidayRepository holidayRepository;
    @Mock private PolicyService policyService;
    @Mock private ServiceAwardRuleRepository awardRuleRepository;
    @Mock private SpecialLeaveRuleRepository specialRuleRepository;

    @Test
    void 처음_설치하면_기본_휴가_종류_7종을_만든다() {
        비어_있음();

        초기화(null);

        List<LeaveType> types = 저장된_목록(leaveTypeRepository);
        assertThat(types).extracting(LeaveType::getCode)
                .containsExactly("ANNUAL", "HALF_AM", "HALF_PM", "HOURLY", "CONDOLENCE", "SICK", "OFFICIAL");
        assertThat(types).filteredOn(LeaveType::isDeductFromAnnual).extracting(LeaveType::getCode)
                .containsExactly("ANNUAL", "HALF_AM", "HALF_PM", "HOURLY");
        assertThat(types).filteredOn(LeaveType::isRequiresAnnualExhausted).extracting(LeaveType::getCode)
                .containsExactly("SICK", "OFFICIAL");
        assertThat(types).filteredOn(t -> t.getAnnualDeductionMode() == AnnualDeductionMode.NONE)
                .extracting(LeaveType::getCode).containsExactly("CONDOLENCE");
        assertThat(types).filteredOn(LeaveType::isAllowedDuringBlackout).extracting(LeaveType::getCode)
                .containsExactly("CONDOLENCE", "OFFICIAL");
        assertThat(types).filteredOn(t -> t.getCode().equals("HOURLY")).extracting(LeaveType::getPortion)
                .containsExactly(DayPortion.HOURLY);
        assertThat(types).filteredOn(t -> !t.isPaid()).extracting(LeaveType::getCode).containsExactly("SICK");
    }

    @Test
    void 처음_설치하면_근속_포상과_경조사_규정을_만든다() {
        비어_있음();

        초기화(null);

        List<ServiceAwardRule> awards = 저장된_목록(awardRuleRepository);
        assertThat(awards).extracting(ServiceAwardRule::getYears).containsExactly(5, 10, 20);
        List<SpecialLeaveRule> rules = 저장된_목록(specialRuleRepository);
        assertThat(rules).extracting(SpecialLeaveRule::getName)
                .containsExactly("본인 결혼", "배우자 출산", "자녀 결혼", "부모/배우자 사망", "조부모/형제자매 사망");
        assertThat(rules).extracting(SpecialLeaveRule::getLeaveTypeCode).containsOnly("CONDOLENCE");
    }

    @Test
    void 기본_정책은_항상_보장한다() {
        비어_있음();

        초기화(null);

        verify(policyService).getActivePolicy();
    }

    @Test
    void 이미_데이터가_있으면_아무것도_만들지_않는다() {
        when(leaveTypeRepository.count()).thenReturn(7L);
        when(holidayRepository.count()).thenReturn(19L);
        when(awardRuleRepository.count()).thenReturn(3L);
        when(specialRuleRepository.count()).thenReturn(5L);

        초기화(null);

        verify(leaveTypeRepository, never()).saveAll(anyList());
        verify(awardRuleRepository, never()).saveAll(anyList());
        verify(specialRuleRepository, never()).saveAll(anyList());
        verify(holidayRepository, never()).save(any());
        verify(policyService).getActivePolicy();
    }

    @Test
    void 공휴일_API_키가_없고_공휴일이_비어_있으면_2026년_공휴일을_넣는다() {
        비어_있음();

        초기화(null);

        ArgumentCaptor<Holiday> saved = ArgumentCaptor.forClass(Holiday.class);
        verify(holidayRepository, times(19)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Holiday::getDate)
                .contains(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 12, 25));
    }

    @Test
    void 공휴일_API_키가_있으면_대체_공휴일을_넣지_않는다() {
        비어_있음();

        초기화("real-key-from-env");

        verify(holidayRepository, never()).save(any());
    }

    @Test
    void 이미_있는_날짜의_공휴일은_다시_넣지_않는다() {
        비어_있음();
        when(holidayRepository.existsByDate(LocalDate.of(2026, 1, 1))).thenReturn(true);

        초기화(null);

        verify(holidayRepository, times(18)).save(any());
    }

    private void 비어_있음() {
        when(leaveTypeRepository.count()).thenReturn(0L);
        when(holidayRepository.count()).thenReturn(0L);
        when(awardRuleRepository.count()).thenReturn(0L);
        when(specialRuleRepository.count()).thenReturn(0L);
    }

    private void 초기화(String holidayApiKey) {
        new LeaveDataInitializer(leaveTypeRepository, holidayRepository, policyService, awardRuleRepository,
                specialRuleRepository, new HolidayApiProperties("https://example.invalid", holidayApiKey))
                .run(new DefaultApplicationArguments());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> List<T> 저장된_목록(org.springframework.data.repository.CrudRepository repository) {
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return (List<T>) captor.getValue();
    }
}
