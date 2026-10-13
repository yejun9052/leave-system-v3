package com.company.leave.employee;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.domain.Department;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.Employee;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 사용자 엑셀 내보내기/가져오기.
 */
@Service
public class EmployeeExcelService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeExcelService.class);

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] HEADERS =
            {"이메일", "이름", "부서명", "직급", "전화번호", "입사일(YYYY-MM-DD)", "권한"};
    /** 부서 경로 구분(내보내기). 가져오기는 "›" 와 ">" 둘 다 받는다 */
    static final String PATH_SEPARATOR = " › ";
    /** 사번 기능 삭제 전 양식: 3번째 열이 "사번". 이 열은 건너뛰고 읽는다. */
    private static final String LEGACY_EMPLOYEE_NO_HEADER = "사번";

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    /** 화면 등록과 같은 입력 검사(@Email·@Size 등). 행마다 서비스에 넘기기 전에 확인한다 */
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final DataFormatter formatter = new DataFormatter();

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
            Map<Long, String> paths = departmentPaths();
            // 관리 전용 계정(admin)은 내보내지 않는다. 다시 올리면 시스템 관리자 권한 제한에 걸린다
            for (var e : employees.getContent().stream().filter(x -> !x.systemAccount()).toList()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(e.email());
                row.createCell(1).setCellValue(e.name());
                // 부서는 경로로(상위 › 하위): 이름이 같은 부서가 여럿이어도 다시 올리면 같은 부서로 들어간다
                row.createCell(2).setCellValue(e.departmentId() != null && paths.containsKey(e.departmentId())
                        ? paths.get(e.departmentId()) : orEmpty(e.departmentName()));
                row.createCell(3).setCellValue(orEmpty(e.position()));
                row.createCell(4).setCellValue(orEmpty(e.phone()));
                row.createCell(5).setCellValue(e.hireDate() != null ? e.hireDate().toString() : "");
                row.createCell(6).setCellValue(e.role().name());
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
                if (StringUtils.hasText(roles) && Arrays.stream(roles.split(",")).map(String::trim)
                        .anyMatch(t -> Role.SYSTEM_ADMIN.name().equalsIgnoreCase(t) || Role.SYSTEM_ADMIN.label().equals(t))) {
                    throw new BusinessException(ErrorCode.SYSTEM_ADMIN_ROLE_RESTRICTED);
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
                            parseDate(row.getCell(5 + shift)),
                            parseRole(cell(row, 6 + shift)));
                    validate(req);
                    employeeService.create(req);
                    created++;
                } catch (BusinessException ex) {
                    errors.add(rowNum + "행: " + ex.getMessage());
                } catch (DataIntegrityViolationException ex) {
                    errors.add(rowNum + "행: 이미 등록된 이메일이거나 값이 너무 깁니다.");
                } catch (RuntimeException ex) {
                    // DB·내부 오류 문구(SQL 등)는 화면에 내보내지 않는다
                    log.warn("엑셀 가져오기 {}행 실패", rowNum, ex);
                    errors.add(rowNum + "행: 등록하지 못했습니다. 입력 값을 확인해 주세요.");
                }
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + ex.getMessage());
        }
        return new ImportResult(created, errors);
    }

    /**
     * 부서 칸: 이름(그 이름이 하나뿐일 때) 또는 경로("연구소 › 개발팀", "연구소 > 개발팀").
     * 이름이 같은 부서가 여럿이면 아무 데나 넣지 않고 경로로 적어 달라고 알린다.
     */
    private Long resolveDepartmentId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.contains("›") || text.contains(">")) {
            String wanted = normalizePath(text);
            return departmentPaths().entrySet().stream()
                    .filter(e -> normalizePath(e.getValue()).equals(wanted))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND,
                            "부서를 찾을 수 없습니다: " + text));
        }
        List<Department> found = departmentRepository.findByName(text);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND, "부서를 찾을 수 없습니다: " + text);
        }
        if (found.size() > 1) {
            Map<Long, String> paths = departmentPaths();
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이름이 같은 부서가 여러 개입니다("
                    + found.stream().map(d -> paths.getOrDefault(d.getId(), d.getName()))
                            .collect(Collectors.joining(", "))
                    + "). 부서 칸에 경로로 적어 주세요(예: 연구소 › 개발팀).");
        }
        return found.get(0).getId();
    }

    /** 부서 id → 경로("상위 › 하위"). 상위가 끊기거나 순환이 있어도 멈춘다. */
    private Map<Long, String> departmentPaths() {
        Map<Long, Department> byId = new HashMap<>();
        departmentRepository.findAllByOrderBySortOrderAscNameAsc().forEach(d -> byId.put(d.getId(), d));
        Map<Long, String> paths = new HashMap<>();
        for (Department d : byId.values()) {
            List<String> names = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (Department cur = d; cur != null && seen.add(cur.getId());
                    cur = cur.getParentId() == null ? null : byId.get(cur.getParentId())) {
                names.add(0, cur.getName());
            }
            paths.put(d.getId(), String.join(PATH_SEPARATOR, names));
        }
        return paths;
    }

    private static String normalizePath(String path) {
        return Arrays.stream(path.split("[›>]")).map(String::trim).collect(Collectors.joining("›"));
    }

    /** 화면 등록과 같은 입력 검사. 어긋나면 칸 이름으로 알린다. */
    private void validate(EmployeeRequests.Create req) {
        Set<ConstraintViolation<EmployeeRequests.Create>> violations = validator.validate(req);
        if (violations.isEmpty()) {
            return;
        }
        String message = violations.stream()
                .map(v -> switch (v.getPropertyPath().toString()) {
                    case "email" -> "이메일 형식이 아닙니다";
                    case "name" -> "이름이 비어 있거나 100자를 넘습니다";
                    case "position" -> "직급은 50자까지입니다";
                    case "phone" -> "전화번호는 30자까지입니다";
                    case "hireDate" -> "입사일이 비어 있습니다";
                    default -> v.getPropertyPath() + " 값이 올바르지 않습니다";
                })
                .sorted()
                .distinct()
                .collect(Collectors.joining(", "));
        throw new BusinessException(ErrorCode.INVALID_INPUT, message + ".");
    }

    /** 입사일 칸: 글자(2024-03-01) 또는 엑셀 날짜 서식 칸. */
    private LocalDate parseDate(Cell c) {
        if (c != null && c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return c.getLocalDateTimeCellValue().toLocalDate();
        }
        String s = c == null ? null : formatter.formatCellValue(c).trim();
        if (!StringUtils.hasText(s)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입사일이 비어 있습니다.");
        }
        try {
            return LocalDate.parse(s, DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입사일 형식이 올바르지 않습니다(예: 2024-03-01): " + s);
        }
    }

    /**
     * 권한 칸: 하나(EMPLOYEE·TEAM_LEAD·HR_ADMIN 또는 사원·팀장·인사관리자). 비우면 사원.
     * 예전 양식처럼 콤마로 여러 개를 적었으면 그중 가장 높은 권한 하나로 받는다.
     */
    static Role parseRole(String s) {
        if (!StringUtils.hasText(s)) {
            return Role.EMPLOYEE;
        }
        return Role.highest(Arrays.stream(s.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(EmployeeExcelService::roleOf)
                .toList());
    }

    private static Role roleOf(String token) {
        for (Role r : Role.values()) {
            if (r.name().equalsIgnoreCase(token) || r.label().equals(token)) {
                return r;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT, "알 수 없는 권한입니다: " + token);
    }

    private String cell(Row row, int idx) {
        Cell c = row.getCell(idx);
        if (c == null) {
            return null;
        }
        // 화면에 보이는 그대로 읽는다(숫자 칸의 서식, 수식 결과 포함)
        String text = formatter.formatCellValue(c).trim();
        return text.isEmpty() ? null : text;
    }

    private String orEmpty(String s) {
        return s == null ? "" : s;
    }

    public record ImportResult(int created, List<String> errors) {
    }
}
