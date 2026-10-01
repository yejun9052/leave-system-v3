package com.company.leave.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.repository.CalendarEventRepository;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.EmployeeService;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.leave.accrual.LeaveAccrualCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.dto.LeaveRequestDtos;
import com.company.leave.leave.repository.LeaveRequestRepository;
import com.company.leave.mail.AccountMailEvents;
import com.company.leave.notification.NotificationService;
import com.company.leave.policy.PolicyService;
import com.company.leave.policy.domain.LeavePolicy;
import com.company.leave.policy.domain.SpecialLeaveRule;
import com.company.leave.policy.repository.BlackoutPeriodRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 병가·공가 사용 조건(잔여 연차 1일 미만 + 대기 중 연차 신청 없음, 승인 시 남은 연차 소멸)과
 * 부분 휴가(반차·반반차·시간차, 같은 날 합계 1일, 정책 켜기/끄기).
 * 기준일 2027-05-04(화). 공휴일 없음. 일수 계산은 실제 WorkdayCalculator.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("병가·공가 조건과 부분 휴가")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveRequestServiceSickAndPartialTest {

    private static final LocalDate TUE = LocalDate.of(2027, 5, 4);
    private static final long EMP = 10L;
    private static final long ADMIN = 1L;

    @Mock
    private LeaveRequestRepository requestRepository;
    @Mock
    private LeaveTypeService leaveTypeService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private LeaveBalanceService balanceService;
    @Mock
    private HolidayRepository holidayRepository;
    @Mock
    private PolicyService policyService;
    @Mock
    private LeaveAccrualCalculator accrualCalculator;
    @Mock
    private CalendarEventRepository calendarEventRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private BlackoutPeriodRepository blackoutPeriodRepository;
    @Mock
    private LeavePolicy policy;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LeaveRequestService service;
    private final LeaveBalance balance = new LeaveBalance(EMP, 2027);
    private final Map<Long, LeaveRequest> saved = new HashMap<>();
    private Employee employee;

    // LeaveType(code, name, deductDays, paid, portion, deductFromAnnual, requiresAnnualExhausted, color, sort)
    private final LeaveType 연차 = 종류(1L, "ANNUAL", "연차", "1.0", DayPortion.FULL, true, false);
    private final LeaveType 반차 = 종류(2L, "HALF_AM", "오전 반차", "0.5", DayPortion.HALF, true, false);
    private final LeaveType 반반차 = 종류(3L, "QUARTER", "반반차", "0.25", DayPortion.QUARTER, true, false);
    private final LeaveType 시간차 = 종류(4L, "HOURLY", "시간차", "0.125", DayPortion.HOURLY, true, false);
    private final LeaveType 경조사 = 종류(5L, "CONDOLENCE", "경조사 휴가", "0.0", DayPortion.FULL, false, false);
    private final LeaveType 병가 = 종류(6L, "SICK", "병가", "0.0", DayPortion.FULL, false, true);
    private final LeaveType 공가 = 종류(7L, "OFFICIAL", "공가", "0.0", DayPortion.FULL, false, true);

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(requestRepository, leaveTypeService, employeeService, balanceService,
                holidayRepository, new WorkdayCalculator(), policyService, accrualCalculator,
                calendarEventRepository, notificationService, departmentRepository, blackoutPeriodRepository,
                eventPublisher,
                new LeaveMessenger(notificationService, eventPublisher, new com.company.leave.mail.AccountMailProperties(
                        "noreply@company.com", "http://localhost:5173")),
                org.mockito.Mockito.mock(com.company.leave.audit.AuditService.class));

        employee = Employee.builder().email("user@company.com").passwordHash("h").name("홍길동").build();
        ReflectionTestUtils.setField(employee, "id", EMP);
        Employee admin = Employee.builder().email("hr@company.com").passwordHash("h").name("인사")
                .roles(Set.of(Role.HR_ADMIN)).build();
        ReflectionTestUtils.setField(admin, "id", ADMIN);
        lenient().when(employeeService.getEntity(EMP)).thenReturn(employee);
        lenient().when(employeeService.getEntity(ADMIN)).thenReturn(admin);
        for (LeaveType t : List.of(연차, 반차, 반반차, 시간차, 경조사, 병가, 공가)) {
            lenient().when(leaveTypeService.getEntity(t.getId())).thenReturn(t);
        }
        lenient().when(policyService.getActivePolicy()).thenReturn(policy);
        lenient().when(policy.allows(any())).thenReturn(true);
        lenient().when(policy.isAllowNegative()).thenReturn(true);
        lenient().when(balanceService.getOrCreate(anyLong(), anyInt())).thenReturn(balance);
        lenient().when(requestRepository.sumPendingDeductedDays(anyLong(), anyInt())).thenReturn(BigDecimal.ZERO);
        lenient().when(requestRepository.save(any(LeaveRequest.class))).thenAnswer(inv -> {
            LeaveRequest r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 500L + saved.size());
            saved.put(r.getId(), r);
            return r;
        });
        lenient().when(requestRepository.findById(anyLong()))
                .thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<Long>getArgument(0))));
    }

    @Nested
    @DisplayName("병가·공가 신청 조건")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 신청_조건 {

        @Test
        void 잔여_연차가_1일_이상이면_병가를_신청할_수_없다() {
            잔여(3);

            assertThatThrownBy(() -> 신청(병가, null, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_ANNUAL_NOT_EXHAUSTED);
                        assertThat(ex.getMessage()).contains("3일");
                    });
            verify(requestRepository, never()).save(any());
        }

        @Test
        void 공가도_같은_조건을_적용한다() {
            잔여(1);

            assertThatThrownBy(() -> 신청(공가, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_ANNUAL_NOT_EXHAUSTED));
        }

        @Test
        void 잔여가_1일_미만이어도_대기_중인_연차_신청이_있으면_신청할_수_없다() {
            잔여(0.5);
            when(requestRepository.existsPendingDeducting(EMP, 2027)).thenReturn(true);

            assertThatThrownBy(() -> 신청(병가, null, true))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_PENDING_ANNUAL_EXISTS));
        }

        @Test
        void 소멸될_연차가_있으면_안내를_확인해야_신청할_수_있다() {
            잔여(0.5);

            assertThatThrownBy(() -> 신청(병가, null, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_FORFEIT_NOT_ACKNOWLEDGED);
                        assertThat(ex.getMessage()).contains("0.5일");
                    });
        }

        @Test
        void 안내를_확인하면_신청되고_신청만으로는_잔액이_바뀌지_않는다() {
            잔여(0.5);

            LeaveRequest request = 신청(병가, null, true);

            assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(balance.remaining()).isEqualByComparingTo("0.5");
            assertThat(request.getForfeitedDays()).isEqualByComparingTo("0");
        }

        @Test
        void 잔여가_0이면_확인_없이_신청할_수_있다() {
            잔여(0);

            assertThat(신청(병가, null, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }

        @Test
        void 병가_공가가_아닌_비차감_휴가는_잔여와_관계없이_신청할_수_있다() {
            잔여(10);

            assertThat(신청(경조사, null, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("병가·공가 승인과 소멸")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 승인과_소멸 {

        @Test
        void 승인되면_남은_연차를_소멸시키고_알림과_메일로_안내한다() {
            잔여(0.5);
            LeaveRequest request = 신청(병가, null, true);

            service.approve(request.getId(), ADMIN);

            assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.APPROVED);
            assertThat(request.getForfeitedDays()).isEqualByComparingTo("0.5");
            assertThat(balance.getExpired()).isEqualByComparingTo("0.5");
            assertThat(balance.remaining()).isEqualByComparingTo("0");
            verify(notificationService).notify(eq(EMP), eq("LEAVE_FORFEITED"), anyString(),
                    org.mockito.ArgumentMatchers.contains("0.5일"), eq("/my-leaves"));
            ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(events.capture());
            // 결재 메일(접수·승인)과 별도로 소멸 안내 메일이 정확히 한 통
            assertThat(events.getAllValues()).filteredOn(AccountMailEvents.LeaveForfeited.class::isInstance)
                    .singleElement()
                    .isInstanceOfSatisfying(AccountMailEvents.LeaveForfeited.class, mail -> {
                        assertThat(mail.email()).isEqualTo("user@company.com");
                        assertThat(mail.forfeitedDays()).isEqualTo("0.5");
                    });
        }

        @Test
        void 남은_연차가_없으면_소멸_안내를_보내지_않는다() {
            잔여(0);
            LeaveRequest request = 신청(병가, null, null);

            service.approve(request.getId(), ADMIN);

            assertThat(request.getForfeitedDays()).isEqualByComparingTo("0");
            verify(notificationService, never()).notify(any(), eq("LEAVE_FORFEITED"), any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any(AccountMailEvents.LeaveForfeited.class));
        }

        @Test
        void 신청_후_대기_중인_연차_신청이_생기면_승인할_수_없다() {
            잔여(0.5);
            LeaveRequest request = 신청(병가, null, true);
            when(requestRepository.existsPendingDeducting(EMP, 2027)).thenReturn(true);

            assertThatThrownBy(() -> service.approve(request.getId(), ADMIN))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_PENDING_ANNUAL_EXISTS);
                        assertThat(ex.getMessage()).contains("신청자");
                    });
            assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(balance.getExpired()).isEqualByComparingTo("0");
        }

        @Test
        void 신청_후_잔여가_1일_이상으로_늘면_승인할_수_없다() {
            잔여(0.5);
            LeaveRequest request = 신청(병가, null, true);
            잔여(1.5); // 예: 월별 적치로 연차가 새로 생김

            assertThatThrownBy(() -> service.approve(request.getId(), ADMIN))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_ANNUAL_NOT_EXHAUSTED));
        }

        @Test
        void 반려되면_소멸되지_않는다() {
            잔여(0.5);
            LeaveRequest request = 신청(병가, null, true);

            service.reject(request.getId(), ADMIN, "서류 미비");

            assertThat(balance.remaining()).isEqualByComparingTo("0.5");
        }

        @Test
        void 승인된_병가가_취소되면_소멸된_연차를_한_번만_되돌린다() {
            잔여(0.5);
            LeaveRequest request = 신청(병가, null, true);
            service.approve(request.getId(), ADMIN);

            service.cancel(request.getId(), ADMIN, "일정 변경");

            assertThat(request.getStatus()).isEqualTo(LeaveRequestStatus.CANCELLED);
            assertThat(balance.remaining()).isEqualByComparingTo("0.5");
            assertThat(request.getForfeitedDays()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("신청 가능 여부 조회")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 신청_가능_여부 {

        @Test
        void 병가는_소멸될_연차를_알려준다() {
            잔여(0.5);

            LeaveRequestDtos.Eligibility result = service.eligibility(EMP, 병가.getId(), TUE);

            assertThat(result.allowed()).isTrue();
            assertThat(result.forfeitDays()).isEqualByComparingTo("0.5");
        }

        @Test
        void 잔여가_남아_있으면_불가_사유를_알려준다() {
            잔여(3);

            LeaveRequestDtos.Eligibility result = service.eligibility(EMP, 병가.getId(), TUE);

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).contains("3일");
            assertThat(result.remainingDays()).isEqualByComparingTo("3");
        }

        @Test
        void 정책에서_꺼진_종류는_불가로_알려준다() {
            when(policy.allows(DayPortion.HOURLY)).thenReturn(false);

            assertThat(service.eligibility(EMP, 시간차.getId(), TUE).allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("부분 휴가")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 부분_휴가 {

        @Test
        void 반반차를_대체하는_시간차_2시간은_0_25일을_차감한다() {
            LeaveRequest request = 신청(시간차, 2, null);

            assertThat(request.getDays()).isEqualByComparingTo("0.25");
            assertThat(request.getDeductedDays()).isEqualByComparingTo("0.25");
        }

        @Test
        void 반반차는_시간차로_대체되어_정책과_무관하게_신청할_수_없다() {
            LeavePolicy real = LeavePolicy.createDefault();
            when(policy.allows(DayPortion.QUARTER)).thenAnswer(inv -> real.allows(DayPortion.QUARTER));

            assertThat(real.allows(DayPortion.QUARTER)).isFalse();
            assertThatThrownBy(() -> 신청(반반차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_TYPE_DISABLED));
        }

        @Test
        void 시간차는_시간_수만큼_0_125일씩_차감한다() {
            LeaveRequest request = 신청(시간차, 3, null);

            assertThat(request.getDays()).isEqualByComparingTo("0.375");
            assertThat(request.getDeductedDays()).isEqualByComparingTo("0.375");
        }

        @Test
        void 시간차는_1에서_3시간만_신청할_수_있다() {
            assertThatThrownBy(() -> 신청(시간차, null, null)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("1~3시간");
            assertThatThrownBy(() -> 신청(시간차, 4, null)).isInstanceOf(BusinessException.class);
        }

        @Test
        void 정책에서_끈_단위는_신청할_수_없다() {
            when(policy.allows(DayPortion.HALF)).thenReturn(false);

            assertThatThrownBy(() -> 신청(반차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_TYPE_DISABLED));
        }

        @Test
        void 부분_휴가는_하루만_신청할_수_있다() {
            assertThatThrownBy(() -> service.create(EMP,
                    new LeaveRequestDtos.Create(반차.getId(), TUE, TUE.plusDays(1), "사유")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_INVALID_PERIOD));
        }

        @Test
        void 같은_날_부분_휴가는_합계_1일까지_함께_신청할_수_있다() {
            기존_신청(반차, "0.5");
            기존_신청(반반차, "0.25"); // V19 이전에 승인된 반반차 기록도 합계에 들어간다

            LeaveRequest request = 신청(시간차, 2, null); // 0.5 + 0.25 + 0.25 = 1.0

            assertThat(request.getDays()).isEqualByComparingTo("0.25");
        }

        @Test
        void 같은_날_부분_휴가_합계가_1일을_넘으면_거부한다() {
            기존_신청(반차, "0.5");
            기존_신청(반반차, "0.25");

            assertThatThrownBy(() -> 신청(시간차, 3, null)) // 1.125일
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_OVERLAP);
                        assertThat(ex.getMessage()).contains("0.75일");
                    });
        }

        @Test
        void 종일_휴가가_있는_날에는_부분_휴가를_신청할_수_없다() {
            기존_신청(연차, "1");

            assertThatThrownBy(() -> 신청(시간차, 1, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_OVERLAP));
        }

        @Test
        void 부분_휴가가_있는_날에는_종일_휴가를_신청할_수_없다() {
            기존_신청(반반차, "0.25");

            assertThatThrownBy(() -> 신청(연차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_DATE_OVERLAP));
        }
    }

    @Nested
    @DisplayName("경조사 규정")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 경조사_규정 {

        // 2027-05-04(화) ~ 05-10(월): 달력 7일, 근무일 5일(주말 제외)
        private static final LocalDate MON_NEXT = LocalDate.of(2027, 5, 10);
        private final SpecialLeaveRule 본인_결혼 = 규정(1L, "본인 결혼", "5.0");
        private final SpecialLeaveRule 자녀_결혼 = 규정(2L, "자녀 결혼", "1.0");

        @BeforeEach
        void 규정_연결() {
            lenient().when(leaveTypeService.specialRulesOf(경조사)).thenReturn(List.of(본인_결혼, 자녀_결혼));
        }

        @Test
        void 규정이_있는_종류는_규정을_고르지_않으면_신청할_수_없다() {
            assertThatThrownBy(() -> 경조사_신청(TUE, TUE, null))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_SPECIAL_RULE_INVALID);
                        assertThat(ex.getMessage()).contains("사유(규정)를 선택");
                    });
        }

        @Test
        void 해당_종류의_규정이_아니면_신청할_수_없다() {
            assertThatThrownBy(() -> 경조사_신청(TUE, TUE, 99L))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_SPECIAL_RULE_INVALID));
        }

        @Test
        void 주말을_뺀_근무일이_규정_일수_이내면_신청되고_규정을_기록한다() {
            LeaveRequest request = 경조사_신청(TUE, MON_NEXT, 본인_결혼.getId());

            assertThat(request.getDays()).isEqualByComparingTo("5");
            assertThat(request.getSpecialRuleId()).isEqualTo(1L);
            assertThat(request.getSpecialRuleName()).isEqualTo("본인 결혼");
            assertThat(request.getSpecialRuleDays()).isEqualByComparingTo("5");
            assertThat(request.getDeductedDays()).isEqualByComparingTo("0");
        }

        @Test
        void 근무일이_규정_일수를_넘으면_거부한다() {
            assertThatThrownBy(() -> 경조사_신청(TUE, MON_NEXT.plusDays(1), 본인_결혼.getId()))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_SPECIAL_RULE_EXCEEDED);
                        assertThat(ex.getMessage()).contains("최대 5일").contains("신청 6일");
                    });
            verify(requestRepository, never()).save(any());
        }

        @Test
        void 같은_규정을_여러_번_신청할_수_있다() {
            경조사_신청(TUE, TUE, 자녀_결혼.getId());
            경조사_신청(TUE.plusDays(7), TUE.plusDays(7), 자녀_결혼.getId());

            assertThat(saved.values()).hasSize(2)
                    .allSatisfy(r -> assertThat(r.getSpecialRuleName()).isEqualTo("자녀 결혼"));
        }

        @Test
        void 규정이_없는_종류에_규정을_보내면_거부한다() {
            assertThatThrownBy(() -> service.create(EMP,
                    new LeaveRequestDtos.Create(연차.getId(), TUE, TUE, "사유", null, null, 본인_결혼.getId())))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_SPECIAL_RULE_INVALID));
        }

        private LeaveRequest 경조사_신청(LocalDate start, LocalDate end, Long ruleId) {
            LeaveRequestDtos.Response response = service.create(EMP,
                    new LeaveRequestDtos.Create(경조사.getId(), start, end, "사유", null, null, ruleId));
            return saved.get(response.id());
        }

        private static SpecialLeaveRule 규정(long id, String name, String days) {
            SpecialLeaveRule rule = new SpecialLeaveRule(name, new BigDecimal(days), "CONDOLENCE", (int) id);
            ReflectionTestUtils.setField(rule, "id", id);
            return rule;
        }
    }

    @Nested
    @DisplayName("경조사·병가·공가는 사용 통제 제외")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 사용_통제_제외 {

        @Test
        void 블랙아웃_기간에도_경조사와_병가는_신청되고_연차는_막힌다() {
            when(blackoutPeriodRepository.existsOverlap(any(), any())).thenReturn(true);
            잔여(0);

            assertThat(신청(경조사, null, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThat(신청(병가, null, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThatThrownBy(() -> 신청(연차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_BLACKOUT));
        }

        @Test
        void 최소_사전_신청_기한은_경조사에_적용하지_않는다() {
            when(policy.getMinAdvanceDays()).thenReturn(10_000);

            assertThat(신청(경조사, null, null).getStatus()).isEqualTo(LeaveRequestStatus.PENDING);
            assertThatThrownBy(() -> 신청(연차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_MIN_ADVANCE));
        }

        @Test
        void 최대_연속_사용일은_경조사에_적용하지_않는다() {
            when(policy.getMaxConsecutiveDays()).thenReturn(1);

            LeaveRequestDtos.Response ok = service.create(EMP,
                    new LeaveRequestDtos.Create(경조사.getId(), TUE, TUE.plusDays(2), "사유"));
            assertThat(ok.days()).isEqualByComparingTo("3");
            assertThatThrownBy(() -> service.create(EMP,
                    new LeaveRequestDtos.Create(연차.getId(), TUE.plusDays(7), TUE.plusDays(9), "사유")))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_MAX_CONSECUTIVE));
        }

        @Test
        void 팀_동시_부재_한도를_넘어도_경조사는_신청되고_결재함에_경고가_뜬다() {
            팀원_한_명이_같은_기간_휴가_중(1);

            LeaveRequest request = 신청(경조사, null, null);
            assertThatThrownBy(() -> 신청(연차, null, null))
                    .isInstanceOfSatisfying(BusinessException.class,
                            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LEAVE_TEAM_LIMIT));

            when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(request));
            List<LeaveRequestDtos.Response> inbox = service.pendingForApprover(ADMIN);

            assertThat(inbox).singleElement().extracting(LeaveRequestDtos.Response::approvalWarning).asString()
                    .contains("팀 부재 2명").contains("한도(1명)");
        }

        @Test
        void 팀_한도_안이면_결재함_경고가_없다() {
            팀원_한_명이_같은_기간_휴가_중(2);
            LeaveRequest request = 신청(경조사, null, null);
            when(requestRepository.findForApproval(any(), any())).thenReturn(List.of(request));

            assertThat(service.pendingForApprover(ADMIN)).singleElement()
                    .extracting(LeaveRequestDtos.Response::approvalWarning).isNull();
        }

        private void 팀원_한_명이_같은_기간_휴가_중(int limit) {
            com.company.leave.department.domain.Department team =
                    new com.company.leave.department.domain.Department("개발팀", null, 0);
            ReflectionTestUtils.setField(team, "id", 3L);
            ReflectionTestUtils.setField(employee, "department", team);
            Employee mate = Employee.builder().email("mate@company.com").passwordHash("h").name("동료")
                    .department(team).build();
            ReflectionTestUtils.setField(mate, "id", 11L);
            LeaveRequest mateLeave = new LeaveRequest(mate, 연차, TUE, TUE, BigDecimal.ONE, BigDecimal.ONE, 2027, "휴가");
            lenient().when(requestRepository.findApprovedBetween(any(), any())).thenReturn(List.of(mateLeave));
            lenient().when(policy.getMaxConcurrentAbsence()).thenReturn(limit);
        }
    }

    // --- helpers ---

    private static LeaveType 종류(long id, String code, String name, String deduct, DayPortion portion,
                                boolean deductFromAnnual, boolean requiresAnnualExhausted) {
        LeaveType type = new LeaveType(code, name, new BigDecimal(deduct), true, portion, deductFromAnnual,
                requiresAnnualExhausted, "#000", 1);
        ReflectionTestUtils.setField(type, "id", id);
        return type;
    }

    /** 승인 기준 잔여 연차를 맞춘다(부여 = 잔여, 사용·소멸 0 기준에서 현재 소멸분은 유지). */
    private void 잔여(double remaining) {
        balance.setGranted(BigDecimal.valueOf(remaining).add(balance.getUsed()).add(balance.getExpired()));
    }

    private LeaveRequest 신청(LeaveType type, Integer hours, Boolean acknowledged) {
        LeaveRequestDtos.Response response = service.create(EMP,
                new LeaveRequestDtos.Create(type.getId(), TUE, TUE, "사유", hours, acknowledged));
        return saved.get(response.id());
    }

    /** 같은 날(TUE) 대기 중인 기존 신청. */
    private void 기존_신청(LeaveType type, String days) {
        LeaveRequest existing = new LeaveRequest(employee, type, TUE, TUE, new BigDecimal(days),
                new BigDecimal(days), 2027, "기존");
        List<LeaveRequest> current = new java.util.ArrayList<>(
                requestRepository.findActiveOverlapping(EMP, TUE, TUE));
        current.add(existing);
        when(requestRepository.findActiveOverlapping(EMP, TUE, TUE)).thenReturn(current);
    }
}
