package com.company.leave.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.calendar.domain.Holiday;
import com.company.leave.calendar.repository.HolidayRepository;
import com.company.leave.department.domain.Department;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.accrual.WorkdayCalculator;
import com.company.leave.leave.domain.AnnualDeductionMode;
import com.company.leave.leave.domain.DayPortion;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveType;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 연차 현황 엑셀(연구소 연차현황표 양식): 기준일(올해는 오늘, 다른 해는 12월 31일)에 직원마다 쓰고 있던 연차 기간,
 * 부서별로 묶은 팀 칸, 그 기간에 쓴 날을 한 칸씩. 실제 엑셀 파일로 다시 읽어 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 현황 엑셀 보고서")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveReportServiceTest {

    private static final LocalDate TODAY = LocalDate.now();
    /** 2024년: 1월 1일 ~ 12월 31일 기간. 3월 1일(금)은 공휴일 */
    private static final LeavePeriodCalculator.Period Y2024 =
            new LeavePeriodCalculator.Period(2024, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    /** A열은 비워 두고 B열부터 쓴다 */
    private static final int C = 1;
    private static final int HEADER_ROW = 4;
    private static final int FIRST_DAY_COL = C + 10;

    @Mock private LeaveBalanceService balanceService;
    @Mock private LeaveRequestRepository requestRepository;
    @Mock private HolidayRepository holidayRepository;

    private LeaveReportService service;
    private final LeaveType 연차 = type("ANNUAL", DayPortion.FULL, "1.0", AnnualDeductionMode.DEDUCT);
    private final LeaveType 반차 = type("HALF_AM", DayPortion.HALF, "0.5", AnnualDeductionMode.DEDUCT);
    private final LeaveType 시간차 = type("HOURLY", DayPortion.HOURLY, "0.125", AnnualDeductionMode.DEDUCT);
    private final LeaveType 경조사 = type("CONDOLENCE", DayPortion.FULL, "0.0", AnnualDeductionMode.NONE);

    @BeforeEach
    void setUp() {
        service = new LeaveReportService(balanceService, requestRepository, holidayRepository, new WorkdayCalculator());
    }

    @Test
    void 올해_보고서는_오늘_기준이고_퇴사자도_포함해_조회한다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(TODAY.getYear()));

        verify(balanceService).balancesAsOf(TODAY, false, false);
        assertThat(sheet.getSheetName()).isEqualTo(String.valueOf(TODAY.getYear()));
        assertThat(글자(sheet, 1, C)).isEqualTo(TODAY.getYear() + " 연차현황 (" + TODAY + " 기준)");
    }

    @Test
    void 다른_해_보고서는_그해_12월_31일_기준이다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(2024));

        verify(balanceService).balancesAsOf(LocalDate.of(2024, 12, 31), false, false);
        assertThat(sheet.getSheetName()).isEqualTo("2024");
        assertThat(글자(sheet, 1, C)).isEqualTo("2024 연차현황 (2024-12-31 기준)");
    }

    @Test
    void 받은_양식처럼_A열과_1행은_비워_두고_B2부터_쓴다() throws IOException {
        Employee 김하늘 = employee(1L, "김하늘", null);
        기간(김하늘, balance(1L, "15", "0"));
        공휴일();
        승인();

        Sheet sheet = 시트(service.exportUsage(2024));

        assertThat(sheet.getRow(0)).isNull();
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            assertThat(값(sheet.getRow(r).getCell(0))).as("%d행 A열", r + 1).isEmpty();
        }
        assertThat(sheet.getRow(1).getFirstCellNum()).isEqualTo((short) C);
        assertThat(칸(sheet, HEADER_ROW + 1, C + 1)).isEqualTo("김하늘");
    }

    @Test
    void 머리줄은_양식_순서이고_사용일은_번호를_붙인_50칸이다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(2024));

        assertThat(글자(sheet, 2, C)).isEqualTo("반차 : (*) · 시간차 : (시간, 예: 2h)");
        assertThat(칸들(sheet.getRow(HEADER_ROW)).subList(C, C + 11)).containsExactly(
                "번호", "이름", "팀", "사용", "입사일", "연차", "추가일", "전체 연차", "남은 연차", "사용 기간", "사용일");
        assertThat(칸(sheet, HEADER_ROW - 1, FIRST_DAY_COL)).isEqualTo("1.0");
        assertThat(칸(sheet, HEADER_ROW - 1, FIRST_DAY_COL + 49)).isEqualTo("50.0");
        assertThat(sheet.getMergedRegions()).contains(
                new CellRangeAddress(HEADER_ROW, HEADER_ROW, FIRST_DAY_COL, FIRST_DAY_COL + 49));
        assertThat(sheet.getLastRowNum()).isEqualTo(HEADER_ROW);
    }

    @Test
    void 사용일은_종일은_날짜_반차는_별표_시간차는_시간으로_쓰고_사용은_칸_합계다() throws IOException {
        Employee 김하늘 = employee(1L, "김하늘", null);
        기간(김하늘, balance(1L, "15", "0"));
        공휴일(LocalDate.of(2024, 3, 1));
        승인(
                // 목~월: 3/1 공휴일, 주말을 빼면 2/29·3/4 두 칸
                request(김하늘, 연차, LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 4), "2", "2"),
                request(김하늘, 시간차, LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 3), "0.25", "0.25"),
                request(김하늘, 반차, LocalDate.of(2024, 5, 8), LocalDate.of(2024, 5, 8), "0.5", "0.5"));

        Sheet sheet = 시트(service.exportUsage(2024));

        int row = HEADER_ROW + 1;
        assertThat(칸들(sheet.getRow(row)).subList(FIRST_DAY_COL, FIRST_DAY_COL + 5)).containsExactly(
                "2024-02-29", "2024-03-04", "2024-05-08(*)", "2024-06-03(2h)", "");
        assertThat(칸(sheet, row, C + 3)).isEqualTo("2.75");
    }

    @Test
    void 연차처럼_차감하지_않는_종류와_연차_기간_밖의_날은_넣지_않는다() throws IOException {
        Employee 박서준 = employee(1L, "박서준", null);
        LeavePeriodCalculator.Period 입사일기준 =
                new LeavePeriodCalculator.Period(2024, LocalDate.of(2024, 4, 1), LocalDate.of(2025, 3, 31));
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of(
                new LeaveBalanceService.PeriodBalance(박서준, 입사일기준, balance(1L, "15", "0"))));
        공휴일();
        승인(
                request(박서준, 경조사, LocalDate.of(2024, 5, 2), LocalDate.of(2024, 5, 3), "2", "0"),
                // 기산일을 걸친 휴가: 3/29(금)는 지난 기간, 4/1(월)만 이번 기간
                request(박서준, 연차, LocalDate.of(2024, 3, 29), LocalDate.of(2024, 4, 1), "2", "2"),
                // 다른 직원(리포트 대상 아님)
                request(employee(9L, "대상아님", null), 연차, LocalDate.of(2024, 6, 3), LocalDate.of(2024, 6, 3), "1", "1"));

        Sheet sheet = 시트(service.exportUsage(2024));

        int row = HEADER_ROW + 1;
        assertThat(칸들(sheet.getRow(row)).subList(FIRST_DAY_COL, FIRST_DAY_COL + 2)).containsExactly("2024-04-01", "");
        assertThat(칸(sheet, row, C + 3)).isEqualTo("1.0");
        assertThat(칸(sheet, row, C + 9)).isEqualTo("2024.04.01 ~ 2025.03.31");
    }

    @Test
    void 연차는_부여_추가일은_빈칸_전체는_부여_남은_연차는_시스템_잔여이고_음수도_그대로다() throws IOException {
        Employee 이도윤 = employee(1L, "이도윤", null);
        ReflectionTestUtils.setField(이도윤, "hireDate", LocalDate.of(2021, 7, 5));
        LeaveBalance b = balance(1L, "15", "17");
        b.setCarriedOver(new BigDecimal("1"));
        기간(이도윤, b);
        공휴일();
        승인();

        Sheet sheet = 시트(service.exportUsage(2024));

        int row = HEADER_ROW + 1;
        assertThat(칸들(sheet.getRow(row)).subList(C, C + 10)).containsExactly(
                "1.0", "이도윤", "부서 없음", "0.0", "2021-07-05", "15.0", "", "15.0", "-1.0",
                "2024.01.01 ~ 2024.12.31");
    }

    @Test
    void 부서_트리_순서로_정렬하고_부서_경로를_쓰며_같은_부서는_팀_칸을_합친다() throws IOException {
        Department 회사 = department(1L, "MLsoft", null, 0);
        Department 연구소 = department(2L, "연구소", 회사, 0);
        Department 개발팀 = department(3L, "개발팀", 연구소, 0);
        Department 기획팀 = department(4L, "기획팀", 연구소, 1);
        Employee 하윤 = employee(1L, "하윤", 기획팀);
        Employee 나래 = employee(2L, "나래", 개발팀);
        Employee 가온 = employee(3L, "가온", 개발팀);
        Employee 무소속 = employee(4L, "무소속", null);
        Employee 대표 = employee(5L, "대표", 회사);
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of(
                new LeaveBalanceService.PeriodBalance(무소속, Y2024, balance(4L, "15", "0")),
                new LeaveBalanceService.PeriodBalance(하윤, Y2024, balance(1L, "15", "0")),
                new LeaveBalanceService.PeriodBalance(나래, Y2024, balance(2L, "15", "0")),
                new LeaveBalanceService.PeriodBalance(대표, Y2024, balance(5L, "15", "0")),
                new LeaveBalanceService.PeriodBalance(가온, Y2024, balance(3L, "15", "0"))));
        공휴일();
        승인();

        Sheet sheet = 시트(service.exportUsage(2024));

        int first = HEADER_ROW + 1;
        List<String> names = new ArrayList<>();
        for (int r = first; r <= sheet.getLastRowNum(); r++) {
            names.add(칸(sheet, r, C + 1));
        }
        assertThat(names).containsExactly("대표", "가온", "나래", "하윤", "무소속");
        assertThat(칸(sheet, first, C + 2)).isEqualTo("MLsoft");
        assertThat(칸(sheet, first + 1, C + 2)).isEqualTo("MLsoft › 연구소 › 개발팀");
        assertThat(칸(sheet, first + 3, C + 2)).isEqualTo("MLsoft › 연구소 › 기획팀");
        assertThat(칸(sheet, first + 4, C + 2)).isEqualTo("부서 없음");
        assertThat(칸(sheet, first + 4, C)).isEqualTo("5.0");
        assertThat(sheet.getMergedRegions())
                .contains(new CellRangeAddress(first + 1, first + 2, C + 2, C + 2))
                .noneMatch(m -> m.getFirstColumn() == C + 2 && m.getFirstRow() != first + 1);
    }

    @Test
    void 사용일이_50일보다_많으면_칸을_그만큼_늘린다() throws IOException {
        Employee 최유나 = employee(1L, "최유나", null);
        기간(최유나, balance(1L, "60", "0"));
        공휴일();
        // 2024-01-01(월) ~ 2024-03-29(금): 주말을 빼면 65일
        승인(request(최유나, 연차, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 29), "65", "65"));

        Sheet sheet = 시트(service.exportUsage(2024));

        assertThat(칸(sheet, HEADER_ROW - 1, FIRST_DAY_COL + 64)).isEqualTo("65.0");
        assertThat(칸(sheet, HEADER_ROW + 1, FIRST_DAY_COL + 64)).isEqualTo("2024-03-29");
        assertThat(칸(sheet, HEADER_ROW + 1, C + 3)).isEqualTo("65.0");
    }

    // --- helpers ---

    private void 기간(Employee e, LeaveBalance b) {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false)))
                .thenReturn(List.of(new LeaveBalanceService.PeriodBalance(e, Y2024, b)));
    }

    private void 공휴일(LocalDate... dates) {
        when(holidayRepository.findByDateBetweenOrderByDateAsc(any(), any()))
                .thenReturn(java.util.Arrays.stream(dates).map(d -> new Holiday(d, "공휴일")).toList());
    }

    private void 승인(LeaveRequest... requests) {
        when(requestRepository.findApprovedBetween(any(), any())).thenReturn(List.of(requests));
    }

    private static LeaveType type(String code, DayPortion portion, String deduct, AnnualDeductionMode mode) {
        return new LeaveType(code, code, new BigDecimal(deduct), true, portion, mode, "#000000", 0);
    }

    private static LeaveRequest request(Employee e, LeaveType type, LocalDate start, LocalDate end,
                                        String days, String deducted) {
        LeaveRequest r = new LeaveRequest(e, type, start, end, new BigDecimal(days), new BigDecimal(deducted),
                start.getYear(), null);
        r.approve(null, Instant.now());
        return r;
    }

    private static Employee employee(Long id, String name, Department department) {
        Employee e = Employee.builder().email(name + "@company.com").passwordHash("h").name(name)
                .department(department).build();
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static Department department(Long id, String name, Department parent, int sortOrder) {
        Department d = new Department(name, parent, sortOrder);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    private static LeaveBalance balance(Long employeeId, String granted, String used) {
        LeaveBalance b = new LeaveBalance(employeeId, 2024);
        b.setGranted(new BigDecimal(granted));
        b.addUsed(new BigDecimal(used));
        return b;
    }

    /** 메모리의 엑셀이라 닫지 않고 읽는다(닫으면 시트를 더 읽을 수 없다). */
    @SuppressWarnings("resource")
    private static Sheet 시트(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
    }

    private static String 글자(Sheet sheet, int row, int col) {
        return sheet.getRow(row).getCell(col).getStringCellValue();
    }

    private static String 칸(Sheet sheet, int row, int col) {
        return 값(sheet.getRow(row).getCell(col));
    }

    private static List<String> 칸들(Row row) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            values.add(값(row.getCell(i)));
        }
        return values;
    }

    /** 날짜 칸은 yyyy-MM-dd, 숫자 칸은 숫자 글자, 빈칸은 "". */
    private static String 값(Cell c) {
        if (c == null || c.getCellType() == CellType.BLANK) {
            return "";
        }
        if (c.getCellType() == CellType.NUMERIC) {
            return DateUtil.isCellDateFormatted(c)
                    ? c.getLocalDateTimeCellValue().toLocalDate().toString()
                    : String.valueOf(c.getNumericCellValue());
        }
        return c.getStringCellValue();
    }
}
