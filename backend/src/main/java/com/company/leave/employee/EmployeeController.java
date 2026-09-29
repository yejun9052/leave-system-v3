package com.company.leave.employee;

import com.company.leave.common.dto.ApiResponse;
import com.company.leave.employee.domain.EmployeeStatus;
import com.company.leave.employee.dto.EmployeeRequests;
import com.company.leave.employee.dto.EmployeeResponse;
import com.company.leave.employee.dto.EmployeeSearchCondition;
import com.company.leave.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Employee", description = "사용자 관리")
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeExcelService excelService;

    public EmployeeController(EmployeeService employeeService, EmployeeExcelService excelService) {
        this.employeeService = employeeService;
        this.excelService = excelService;
    }

    @Operation(summary = "사용자 목록/검색 (페이지)")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN','TEAM_LEAD')")
    @GetMapping
    public ApiResponse<Page<EmployeeResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) EmployeeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var condition = new EmployeeSearchCondition(keyword, departmentId, status);
        return ApiResponse.ok(employeeService.search(condition, PageRequest.of(page, size)));
    }

    @Operation(summary = "사용자 단건 조회")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN','TEAM_LEAD')")
    @GetMapping("/{id}")
    public ApiResponse<EmployeeResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(employeeService.get(id));
    }

    @Operation(summary = "사용자 생성")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PostMapping
    public ApiResponse<EmployeeResponse> create(@Valid @RequestBody EmployeeRequests.Create req) {
        return ApiResponse.ok(employeeService.create(req));
    }

    @Operation(summary = "사용자 수정")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PutMapping("/{id}")
    public ApiResponse<EmployeeResponse> update(
            @PathVariable Long id, @Valid @RequestBody EmployeeRequests.Update req) {
        return ApiResponse.ok(employeeService.update(id, req));
    }

    @Operation(summary = "사용자 퇴사 처리")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> resign(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate resignedDate) {
        employeeService.resign(id, resignedDate);
        return ApiResponse.ok();
    }

    @Operation(summary = "사용자 재직 복원")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PatchMapping("/{id}/reactivate")
    public ApiResponse<Void> reactivate(@PathVariable Long id) {
        employeeService.reactivate(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "비밀번호 초기화(관리자)")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PatchMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id, @Valid @RequestBody EmployeeRequests.ResetPassword req) {
        employeeService.resetPassword(id, req.newPassword());
        return ApiResponse.ok();
    }

    // --- 본인 ---

    @Operation(summary = "내 프로필 수정")
    @PutMapping("/me/profile")
    public ApiResponse<EmployeeResponse> updateMyProfile(
            @Valid @RequestBody EmployeeRequests.UpdateMyProfile req) {
        return ApiResponse.ok(employeeService.updateMyProfile(SecurityUtils.currentEmployeeId(), req));
    }

    @Operation(summary = "내 비밀번호 변경")
    @PatchMapping("/me/password")
    public ApiResponse<Void> changeMyPassword(
            @Valid @RequestBody EmployeeRequests.ChangeMyPassword req) {
        employeeService.changeMyPassword(
                SecurityUtils.currentEmployeeId(), req.currentPassword(), req.newPassword());
        return ApiResponse.ok();
    }

    // --- 엑셀 ---

    @Operation(summary = "사용자 엑셀 내보내기")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @GetMapping("/export")
    public ResponseEntity<ByteArrayResource> export() {
        byte[] bytes = excelService.export();
        String filename = "employees.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new ByteArrayResource(bytes));
    }

    @Operation(summary = "사용자 엑셀 일괄 등록")
    @PreAuthorize("hasAnyRole('HR_ADMIN','SUPER_ADMIN')")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EmployeeExcelService.ImportResult> importExcel(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(excelService.importFrom(file.getInputStream()));
    }
}
