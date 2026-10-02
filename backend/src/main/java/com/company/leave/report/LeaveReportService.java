package com.company.leave.report;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.leave.LeaveBalanceService;
import com.company.leave.leave.domain.LeaveBalance;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 연차 사용 현황 리포트 (엑셀).
 */
@Service
public class LeaveReportService {

    private static final String[] HEADERS =
            {"이름", "부서", "입사일", "기간 시작", "사용 기한", "부여", "이월", "사용", "소멸", "잔여"};

    private final LeaveBalanceService balanceService;

    public LeaveReportService(LeaveBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    /**
     * year 의 연차 현황. 입사일 기준이면 직원마다 연차 기간이 달라, 기준일(올해는 오늘, 지난해는 12월 31일)에
     * 각 직원이 쓰고 있던 기간의 잔액을 보여 준다.
     */
    @Transactional(readOnly = true)
    public byte[] exportUsage(int year) {
        LocalDate today = LocalDate.now();
        LocalDate asOf = year == today.getYear() ? today : LocalDate.of(year, 12, 31);
        List<LeaveBalanceService.PeriodBalance> rows = balanceService.balancesAsOf(asOf, false);

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(year + " 연차현황 (" + asOf + " 기준)");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int r = 1;
            for (LeaveBalanceService.PeriodBalance pb : rows) {
                Employee e = pb.employee();
                LeaveBalance b = pb.balance();
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getName());
                row.createCell(1).setCellValue(e.getDepartment() != null ? e.getDepartment().getName() : "");
                row.createCell(2).setCellValue(e.getHireDate() != null ? e.getHireDate().toString() : "");
                row.createCell(3).setCellValue(pb.period().start().toString());
                row.createCell(4).setCellValue(pb.period().end().toString());
                row.createCell(5).setCellValue(b.getGranted().doubleValue());
                row.createCell(6).setCellValue(b.getCarriedOver().doubleValue());
                row.createCell(7).setCellValue(b.getUsed().doubleValue());
                row.createCell(8).setCellValue(b.getExpired().doubleValue());
                row.createCell(9).setCellValue(b.remaining().doubleValue());
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "리포트 생성 실패: " + ex.getMessage());
        }
    }
}
