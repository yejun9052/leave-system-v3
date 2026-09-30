package com.company.leave.employee;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 사용자 엑셀 내보내기/가져오기.
 */
@Service
public class EmployeeExcelService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] HEADERS =
            {"이메일", "이름", "부서명", "직급", "전화번호", "입사일(YYYY-MM-DD)", "권한(콤마)"};
    /** 사번 기능 삭제 전 양식: 3번째 열이 "사번". 이 열은 건너뛰고 읽는다. */
    private static final String LEGACY_EMPLOYEE_NO_HEADER = "사번";

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;

    public EmployeeExcelService(EmployeeService employeeService,
                                DepartmentRepository departmentRepository) {
        this.employeeService = employeeService;
        this.departmentRepository = departmentRepository;
    }

    /** 현재 사용자 목록을 엑셀로 내보낸다. */
    @Transactional(readOnly = true)
    public byte[] export() {
        var employees = employeeService.search(
                new EmployeeSearchCondition(null, null, null), Pageable.ofSize(10_000));
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("사용자");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int r = 1;
            for (var e : employees.getContent()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.email());
                row.createCell(1).setCellValue(e.name());
                row.createCell(2).setCellValue(orEmpty(e.departmentName()));
                row.createCell(3).setCellValue(orEmpty(e.position()));
                row.createCell(4).setCellValue(orEmpty(e.phone()));
                row.createCell(5).setCellValue(e.hireDate() != null ? e.hireDate().toString() : "");
                row.createCell(6).setCellValue(String.join(",", e.roles()));
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "엑셀 생성 실패: " + ex.getMessage());
        }
    }

    /**
     * 엑셀 파일로 사용자를 일괄 등록한다.
     * <p>여기에 @Transactional 을 걸지 않는다: 걸면 행마다 호출하는 {@code employeeService.create()}
     * (@Transactional)가 같은 트랜잭션에 합류해, 한 행이라도 실패하면 트랜잭션이 rollback-only 로
     * 표시되어 커밋 시 전체가 UnexpectedRollbackException 으로 실패한다. 트랜잭션을 두지 않으면
     * 각 create() 가 독립 트랜잭션으로 실행되어, 실패한 행만 롤백되고 나머지는 정상 등록된다.
     * <p>제목 행의 3번째 열이 "사번"인 예전 양식은 그 열을 건너뛰고 읽는다(사번 값은 버림).
     */
    public ImportResult importFrom(InputStream in) {
        List<String> errors = new ArrayList<>();
        int created = 0;
        int rowNum = 1;
        int shift = 0; // 예전 양식이면 1: 이름 뒤 열을 한 칸씩 밀어 읽는다
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            // 금지 권한은 파일 전체를 등록 전에 검사한다. 앞선 정상 행도 저장하지 않고 400으로 거부.
            Row header = sheet.getRow(0);
            int roleColumn = header != null && LEGACY_EMPLOYEE_NO_HEADER.equals(cell(header, 2)) ? 7 : 6;
            for (Row row : sheet) {
                if (row.getRowNum() == 0 || !StringUtils.hasText(cell(row, 0))) {
                    continue;
                }
                String roles = cell(row, roleColumn);
                if (StringUtils.hasText(roles) && Arrays.stream(roles.split(","))
                        .map(String::trim).anyMatch(Role.SUPER_ADMIN.name()::equals)) {
                    throw new BusinessException(ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
                }
            }
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    shift = LEGACY_EMPLOYEE_NO_HEADER.equals(cell(row, 2)) ? 1 : 0;
                    continue; // header
                }
                rowNum = row.getRowNum() + 1;
                String email = cell(row, 0);
                if (!StringUtils.hasText(email)) {
                    continue; // 빈 행 skip
                }
                try {
                    EmployeeRequests.Create req = new EmployeeRequests.Create(
                            email,
                            cell(row, 1),
                            resolveDepartmentId(cell(row, 2 + shift)),
                            cell(row, 3 + shift),
                            cell(row, 4 + shift),
                            parseDate(cell(row, 5 + shift)),
                            parseRoles(cell(row, 6 + shift)));
                    employeeService.create(req);
                    created++;
                } catch (Exception ex) {
                    errors.add(rowNum + "행: " + ex.getMessage());
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + ex.getMessage());
        }
        return new ImportResult(created, errors);
    }

    private Long resolveDepartmentId(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        List<Department> found = departmentRepository.findByName(name.trim());
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, "부서를 찾을 수 없습니다: " + name);
        }
        return found.get(0).getId();
    }

    private LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입사일이 비어 있습니다.");
        }
        return LocalDate.parse(s.trim(), DATE_FMT);
    }

    private Set<Role> parseRoles(String s) {
        if (!StringUtils.hasText(s)) {
            return Set.of(Role.EMPLOYEE);
        }
        return Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }

    private String cell(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            return null;
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default -> null;
        };
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ImportResult(int created, List<String> errors) {
    }
}
