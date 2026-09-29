package com.company.leave.report;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.repository.EmployeeRepository;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
            {"이름", "사번", "부서", "입사일", "부여", "이월", "사용", "소멸", "잔여"};

    private final LeaveBalanceRepository balanceRepository;
    private final EmployeeRepository employeeRepository;

    public LeaveReportService(LeaveBalanceRepository balanceRepository,
                              EmployeeRepository employeeRepository) {
        this.balanceRepository = balanceRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional(readOnly = true)
    public byte[] exportUsage(int year) {
        List<LeaveBalance> balances = balanceRepository.findByYear(year);
        Map<Long, Employee> employees = employeeRepository
                .findAllById(balances.stream().map(LeaveBalance::getEmployeeId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Employee::getId, e -> e, (a, b) -> a, HashMap::new));

        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(year + " 연차현황");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int r = 1;
            for (LeaveBalance b : balances) {
                Employee e = employees.get(b.getEmployeeId());
                if (e == null) {
                    continue;
                }
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.getName());
                row.createCell(1).setCellValue(e.getEmployeeNo() != null ? e.getEmployeeNo() : "");
                row.createCell(2).setCellValue(e.getDepartment() != null ? e.getDepartment().getName() : "");
                row.createCell(3).setCellValue(e.getHireDate() != null ? e.getHireDate().toString() : "");
                row.createCell(4).setCellValue(b.getGranted().doubleValue());
                row.createCell(5).setCellValue(b.getCarriedOver().doubleValue());
                row.createCell(6).setCellValue(b.getUsed().doubleValue());
                row.createCell(7).setCellValue(b.getExpired().doubleValue());
                row.createCell(8).setCellValue(b.remaining().doubleValue());
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
