package com.company.leave.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.company.leave.department.domain.Department;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.department.repository.DepartmentRepository;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.domain.Role;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 사용자 엑셀 내보내기·일괄 등록. 사번 열은 없어졌고, 예전 양식(사번 열 포함)도 그 열만 건너뛰고 읽는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 엑셀")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class EmployeeExcelServiceTest {

    private static final String[] NEW_HEADERS =
            {"이메일", "이름", "부서명", "직급", "전화번호", "입사일(YYYY-MM-DD)", "권한"};
    private static final String[] LEGACY_HEADERS =
            {"이메일", "이름", "사번", "부서명", "직급", "전화번호", "입사일(YYYY-MM-DD)", "권한(콤마)"};

    @Mock
    private EmployeeService employeeService;
    @Mock
    private DepartmentRepository departmentRepository;

    private EmployeeExcelService excelService;

    @BeforeEach
    void setUp() {
        excelService = new EmployeeExcelService(employeeService, departmentRepository);
        Department 개발팀 = new Department("개발팀", null, 0);
        ReflectionTestUtils.setField(개발팀, "id", 2L);
        lenient().when(departmentRepository.findByName("개발팀")).thenReturn(List.of(개발팀));
    }

    @Test
    void 새_양식은_사번_없이_각_열을_읽어_등록한다() throws IOException {
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"kim@company.com", "김철수", "개발팀", "대리", "010-1111-2222", "2024-03-02", "EMPLOYEE,TEAM_LEAD"});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        EmployeeRequests.Create req = 등록_요청들().get(0);
        assertThat(req.email()).isEqualTo("kim@company.com");
        assertThat(req.name()).isEqualTo("김철수");
        assertThat(req.departmentId()).isEqualTo(2L);
        assertThat(req.position()).isEqualTo("대리");
        assertThat(req.phone()).isEqualTo("010-1111-2222");
        assertThat(req.hireDate()).isEqualTo(LocalDate.of(2024, 3, 2));
        assertThat(req.role()).isEqualTo(Role.TEAM_LEAD); // 예전 양식처럼 여러 개면 가장 높은 권한 하나
    }

    @Test
    void 권한_칸은_하나만_받고_한글_이름도_읽으며_여러_개면_가장_높은_권한이다() {
        assertThat(EmployeeExcelService.parseRole(null)).isEqualTo(Role.EMPLOYEE);
        assertThat(EmployeeExcelService.parseRole("TEAM_LEAD")).isEqualTo(Role.TEAM_LEAD);
        assertThat(EmployeeExcelService.parseRole("인사관리자")).isEqualTo(Role.HR_ADMIN);
        assertThat(EmployeeExcelService.parseRole("EMPLOYEE, HR_ADMIN, TEAM_LEAD")).isEqualTo(Role.HR_ADMIN);
        assertThatThrownBy(() -> EmployeeExcelService.parseRole("부장"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getMessage()).contains("알 수 없는 권한"));
    }

    @Test
    void 사번_열이_있는_예전_양식은_사번만_건너뛰고_나머지를_같게_읽는다() throws IOException {
        byte[] file = 엑셀(LEGACY_HEADERS,
                new String[] {"kim@company.com", "김철수", "E001", "개발팀", "대리", "010-1111-2222", "2024-03-02", "EMPLOYEE"});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.created()).isEqualTo(1);
        EmployeeRequests.Create req = 등록_요청들().get(0);
        assertThat(req.departmentId()).isEqualTo(2L);
        assertThat(req.position()).isEqualTo("대리");
        assertThat(req.phone()).isEqualTo("010-1111-2222");
        assertThat(req.hireDate()).isEqualTo(LocalDate.of(2024, 3, 2));
        assertThat(req.role()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void 실패한_행은_오류로_모으고_나머지_행은_등록한다() throws IOException {
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"bad@company.com", "입사일없음", "", "", "", "", ""},
                new String[] {"ok@company.com", "정상", "", "", "", "2024-01-02", ""});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.errors()).singleElement().asString().startsWith("2행");
        verify(employeeService).create(any());
    }

    @Test
    void 내보내기_양식에는_사번_열이_없다() throws IOException {
        when(employeeService.search(any(), any())).thenReturn(new PageImpl<>(List.of(
                new EmployeeResponse(1L, "kim@company.com", "김철수", 2L, "개발팀", "대리", "010-1111-2222",
                        LocalDate.of(2024, 3, 2), EmployeeStatus.ACTIVE, Role.EMPLOYEE, false))));

        byte[] exported = excelService.export();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            Row header = wb.getSheetAt(0).getRow(0);
            List<String> headers = new ArrayList<>();
            header.forEach(c -> headers.add(c.getStringCellValue()));
            assertThat(headers).containsExactly(NEW_HEADERS);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("개발팀");
        }
    }

    @Test
    void 내보내기에는_관리_전용_계정을_넣지_않는다() throws IOException {
        when(employeeService.search(any(), any())).thenReturn(new PageImpl<>(List.of(
                new EmployeeResponse(1L, "admin", "시스템관리자", 2L, "연구소", null, null,
                        LocalDate.of(2023, 10, 6), EmployeeStatus.ACTIVE, Role.SYSTEM_ADMIN, true),
                new EmployeeResponse(2L, "kim@company.com", "김철수", 2L, "개발팀", "대리", null,
                        LocalDate.of(2024, 3, 2), EmployeeStatus.ACTIVE, Role.EMPLOYEE, false))));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelService.export()))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("kim@company.com");
        }
    }

    @Test
    void 이름이_같은_부서가_여럿이면_아무_데나_넣지_않고_경로로_적어_달라고_한다() throws IOException {
        Department 연구소 = 부서(1L, "연구소", null);
        Department 사업부 = 부서(5L, "사업부", null);
        Department 연구소_개발팀 = 부서(2L, "개발팀", 연구소);
        Department 사업부_개발팀 = 부서(6L, "개발팀", 사업부);
        when(departmentRepository.findByName("개발팀")).thenReturn(List.of(연구소_개발팀, 사업부_개발팀));
        when(departmentRepository.findAllByOrderBySortOrderAscNameAsc())
                .thenReturn(List.of(연구소, 사업부, 연구소_개발팀, 사업부_개발팀));
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"a@company.com", "가", "개발팀", "", "", "2024-01-02", ""},
                new String[] {"b@company.com", "나", "사업부 › 개발팀", "", "", "2024-01-02", ""},
                new String[] {"c@company.com", "다", "연구소>개발팀", "", "", "2024-01-02", ""});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.errors()).singleElement().asString()
                .startsWith("2행: 이름이 같은 부서가 여러 개입니다(")
                .contains("연구소 › 개발팀").contains("사업부 › 개발팀");
        assertThat(등록_요청들()).extracting(EmployeeRequests.Create::departmentId).containsExactly(6L, 2L);
    }

    @Test
    void 내보내기의_부서는_경로로_쓴다() throws IOException {
        Department 연구소 = 부서(1L, "연구소", null);
        Department 개발팀 = 부서(2L, "개발팀", 연구소);
        when(departmentRepository.findAllByOrderBySortOrderAscNameAsc()).thenReturn(List.of(연구소, 개발팀));
        when(employeeService.search(any(), any())).thenReturn(new PageImpl<>(List.of(
                new EmployeeResponse(1L, "kim@company.com", "김철수", 2L, "개발팀", "대리", null,
                        LocalDate.of(2024, 3, 2), EmployeeStatus.ACTIVE, Role.EMPLOYEE, false))));

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(excelService.export()))) {
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("연구소 › 개발팀");
        }
    }

    @Test
    void 화면_등록과_같은_입력_검사를_하고_칸_이름으로_알린다() throws IOException {
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"홍길동", "이메일아님", "", "", "", "2024-01-02", ""},
                new String[] {"long@company.com", "직급초과", "", "가".repeat(51), "", "2024-01-02", ""},
                new String[] {"date@company.com", "날짜틀림", "", "", "", "2024/01/02", ""});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.created()).isZero();
        assertThat(result.errors()).containsExactly(
                "2행: 이메일 형식이 아닙니다.",
                "3행: 직급은 50자까지입니다.",
                "4행: 입사일 형식이 올바르지 않습니다(예: 2024-03-01): 2024/01/02");
        verify(employeeService, never()).create(any());
    }

    @Test
    void DB_오류_문구는_화면에_내보내지_않는다() throws IOException {
        when(employeeService.create(any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("insert into employees ... uq_employees_email"))
                .thenThrow(new IllegalStateException("could not execute statement [insert into employees (...)]"));
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"dup@company.com", "중복", "", "", "", "2024-01-02", ""},
                new String[] {"err@company.com", "오류", "", "", "", "2024-01-02", ""});

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.errors()).containsExactly(
                "2행: 이미 등록된 이메일이거나 값이 너무 깁니다.",
                "3행: 등록하지 못했습니다. 입력 값을 확인해 주세요.");
    }

    @Test
    void 엑셀_날짜_서식_칸의_입사일도_읽는다() throws IOException {
        byte[] file;
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("사용자");
            쓰기(sheet.createRow(0), NEW_HEADERS);
            Row row = sheet.createRow(1);
            쓰기(row, new String[] {"kim@company.com", "김철수", "", "", "", "", ""});
            org.apache.poi.ss.usermodel.CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            row.getCell(5).setCellValue(LocalDate.of(2024, 3, 2));
            row.getCell(5).setCellStyle(dateStyle);
            wb.write(out);
            file = out.toByteArray();
        }

        EmployeeExcelService.ImportResult result = excelService.importFrom(new ByteArrayInputStream(file));

        assertThat(result.errors()).isEmpty();
        assertThat(등록_요청들().get(0).hireDate()).isEqualTo(LocalDate.of(2024, 3, 2));
    }

    private static Department 부서(Long id, String name, Department parent) {
        Department d = new Department(name, parent, 0);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    // --- helpers ---
    @Test
    void 시스템_관리자_권한이_뒤쪽_행에_있어도_아무_직원도_등록하지_않고_400으로_거부한다() throws IOException {
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"ok@company.com", "정상", "", "", "", "2024-01-02", "EMPLOYEE"},
                new String[] {"bad@company.com", "권한상승", "", "", "", "2024-01-02", " HR_ADMIN, SYSTEM_ADMIN "});

        assertForbiddenRoles(file);
    }

    @Test
    void 예전_양식의_시스템_관리자_권한도_거부한다() throws IOException {
        byte[] file = 엑셀(LEGACY_HEADERS,
                new String[] {"bad@company.com", "권한상승", "E001", "", "", "", "2024-01-02", "SYSTEM_ADMIN"});

        assertForbiddenRoles(file);
    }

    private void assertForbiddenRoles(byte[] file) {
        assertThatThrownBy(() -> excelService.importFrom(new ByteArrayInputStream(file)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYSTEM_ADMIN_ROLE_RESTRICTED);
                    assertThat(ex.getErrorCode().status().value()).isEqualTo(400);
                });
        verify(employeeService, never()).create(any());
    }

    private List<EmployeeRequests.Create> 등록_요청들() {
        ArgumentCaptor<EmployeeRequests.Create> captor = ArgumentCaptor.forClass(EmployeeRequests.Create.class);
        verify(employeeService, org.mockito.Mockito.atLeastOnce()).create(captor.capture());
        return captor.getAllValues();
    }

    private static byte[] 엑셀(String[] headers, String[]... rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("사용자");
            쓰기(sheet.createRow(0), headers);
            for (int i = 0; i < rows.length; i++) {
                쓰기(sheet.createRow(i + 1), rows[i]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void 쓰기(Row row, String[] values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
