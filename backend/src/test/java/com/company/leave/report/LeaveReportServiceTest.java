package com.company.leave.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.leave.department.domain.Department;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.accrual.LeavePeriodCalculator;
import com.company.leave.leave.domain.LeaveBalance;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 연차 현황 엑셀: 기준일(올해는 오늘, 다른 해는 12월 31일)에 직원마다 쓰고 있던 연차 기간의 잔액을 한 줄씩.
 * 시트 이름·머리줄(기간 시작·사용 기한 포함)·값을 실제 엑셀 파일로 다시 읽어 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("연차 현황 엑셀 보고서")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LeaveReportServiceTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Mock private LeaveBalanceService balanceService;

    private LeaveReportService service;

    @BeforeEach
    void setUp() {
        service = new LeaveReportService(balanceService);
    }

    @Test
    void 올해_보고서는_오늘_기준이고_퇴사자도_포함해_조회한다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(TODAY.getYear()));

        verify(balanceService).balancesAsOf(TODAY, false, false);
        assertThat(sheet.getSheetName()).isEqualTo(TODAY.getYear() + " 연차현황 (" + TODAY + " 기준)");
    }

    @Test
    void 다른_해_보고서는_그해_12월_31일_기준이다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(2024));

        verify(balanceService).balancesAsOf(LocalDate.of(2024, 12, 31), false, false);
        assertThat(sheet.getSheetName()).isEqualTo("2024 연차현황 (2024-12-31 기준)");
    }

    @Test
    void 머리줄에는_기간_시작과_사용_기한이_들어간다() throws IOException {
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of());

        Sheet sheet = 시트(service.exportUsage(2024));

        assertThat(셀들(sheet.getRow(0))).containsExactly(
                "이름", "부서", "입사일", "기간 시작", "사용 기한", "부여", "이월", "사용", "소멸", "잔여");
        assertThat(sheet.getLastRowNum()).isZero();
    }

    @Test
    void 직원마다_그_연차_기간의_잔액을_한_줄씩_쓴다() throws IOException {
        Department 개발팀 = new Department("개발팀", null, 0);
        LeaveBalance b = new LeaveBalance(1L, 2024);
        b.setGranted(new BigDecimal("16"));
        b.setCarriedOver(new BigDecimal("2"));
        b.addUsed(new BigDecimal("3.5"));
        b.setExpired(new BigDecimal("1"));
        Employee 정하은 = Employee.builder().email("a@company.com").passwordHash("h").name("정하은")
                .department(개발팀).hireDate(LocalDate.of(2021, 7, 5)).build();
        Employee 부서없음 = Employee.builder().email("b@company.com").passwordHash("h").name("부서없음").build();
        when(balanceService.balancesAsOf(any(), eq(false), eq(false))).thenReturn(List.of(
                new LeaveBalanceService.PeriodBalance(정하은,
                        new LeavePeriodCalculator.Period(2024, LocalDate.of(2024, 7, 5), LocalDate.of(2025, 7, 4)), b),
                new LeaveBalanceService.PeriodBalance(부서없음,
                        new LeavePeriodCalculator.Period(2024, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)),
                        new LeaveBalance(2L, 2024))));

        Sheet sheet = 시트(service.exportUsage(2024));

        assertThat(sheet.getLastRowNum()).isEqualTo(2);
        Row row = sheet.getRow(1);
        assertThat(셀들(row).subList(0, 5)).containsExactly("정하은", "개발팀", "2021-07-05", "2024-07-05", "2025-07-04");
        assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(16.0);
        assertThat(row.getCell(6).getNumericCellValue()).isEqualTo(2.0);
        assertThat(row.getCell(7).getNumericCellValue()).isEqualTo(3.5);
        assertThat(row.getCell(8).getNumericCellValue()).isEqualTo(1.0);
        assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(13.5); // 16 + 2 − 3.5 − 1
        assertThat(셀들(sheet.getRow(2)).subList(0, 3)).containsExactly("부서없음", "", "");
    }

    // --- helpers ---

    /** 메모리의 엑셀이라 닫지 않고 읽는다(닫으면 시트를 더 읽을 수 없다). */
    @SuppressWarnings("resource")
    private static Sheet 시트(byte[] xlsx) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(xlsx)).getSheetAt(0);
    }

    /** 문자 칸은 글자, 숫자 칸은 숫자 글자로. */
    private static List<String> 셀들(Row row) {
        List<String> values = new ArrayList<>();
        row.forEach(c -> values.add(switch (c.getCellType()) {
            case NUMERIC -> String.valueOf(c.getNumericCellValue());
            default -> c.getStringCellValue();
        }));
        return values;
    }
}
