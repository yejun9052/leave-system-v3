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
            {"이메일", "이름", "부서명", "직급", "전화번호", "입사일(YYYY-MM-DD)", "권한(콤마)"};
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
        assertThat(req.roles()).containsExactlyInAnyOrder(Role.EMPLOYEE, Role.TEAM_LEAD);
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
        assertThat(req.roles()).containsExactly(Role.EMPLOYEE);
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
                        LocalDate.of(2024, 3, 2), EmployeeStatus.ACTIVE, List.of("EMPLOYEE")))));

        byte[] exported = excelService.export();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            Row header = wb.getSheetAt(0).getRow(0);
            List<String> headers = new ArrayList<>();
            header.forEach(c -> headers.add(c.getStringCellValue()));
            assertThat(headers).containsExactly(NEW_HEADERS);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getStringCellValue()).isEqualTo("개발팀");
        }
    }

    // --- helpers ---
    @Test
    void 시스템_관리자_권한이_뒤쪽_행에_있어도_아무_직원도_등록하지_않고_400으로_거부한다() throws IOException {
        byte[] file = 엑셀(NEW_HEADERS,
                new String[] {"ok@company.com", "정상", "", "", "", "2024-01-02", "EMPLOYEE"},
                new String[] {"bad@company.com", "권한상승", "", "", "", "2024-01-02", " HR_ADMIN, SUPER_ADMIN "});

        assertForbiddenRoles(file);
    }

    @Test
    void 예전_양식의_시스템_관리자_권한도_거부한다() throws IOException {
        byte[] file = 엑셀(LEGACY_HEADERS,
                new String[] {"bad@company.com", "권한상승", "E001", "", "", "", "2024-01-02", "SUPER_ADMIN"});

        assertForbiddenRoles(file);
    }

    private void assertForbiddenRoles(byte[] file) {
        assertThatThrownBy(() -> excelService.importFrom(new ByteArrayInputStream(file)))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SUPER_ADMIN_ROLE_RESTRICTED);
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
