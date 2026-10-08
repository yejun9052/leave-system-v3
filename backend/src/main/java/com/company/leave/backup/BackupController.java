package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupDtos.Overview;
import com.company.leave.backup.BackupDtos.Settings;
import com.company.leave.backup.BackupDtos.SettingsRequest;
import com.company.leave.common.dto.ApiResponse;
import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.security.SecurityUtils;
import com.company.leave.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책 → 백업 탭. 백업 실행·목록은 인사관리자·시스템 관리자, 내려받기는 시스템 관리자만.
 * 실행(POST)·삭제(DELETE)와 내려받기(GET .../download)는 이벤트 로그에 남는다(AuditAspect).
 * 복원은 시스템 관리자만, 복원이 끝난 뒤 RestoreService 가 직접 기록한다.
 * 점검 모드 조회(GET /api/backups/status)는 로그인 없이 {@link MaintenanceFilter} 가 바로 답한다.
 */
@Tag(name = "Backup", description = "DB 백업")
@RestController
@RequestMapping("/api/backups")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class BackupController {

    private final BackupService backupService;
    private final BackupSettingsService settingsService;
    private final RestoreService restoreService;

    public BackupController(BackupService backupService, BackupSettingsService settingsService,
                            RestoreService restoreService) {
        this.backupService = backupService;
        this.settingsService = settingsService;
        this.restoreService = restoreService;
    }

    /** @param confirm 확인 문구. "복원" 이어야 한다 */
    public record RestoreRequest(String confirm) {
    }

    @Operation(summary = "백업 폴더·남은 공간·진행 여부와 백업 목록(최신순)")
    @GetMapping
    public ApiResponse<Overview> overview() {
        return ApiResponse.ok(backupService.overview());
    }

    @Operation(summary = "지금 백업", description = "끝날 때까지 기다린 뒤 만든 파일 정보를 돌려준다. 진행 중이면 409.")
    @PostMapping
    public ApiResponse<BackupFile> backup() {
        return ApiResponse.ok(backupService.backup(Kind.MANUAL));
    }

    @Operation(summary = "자동 백업 설정과 다음 실행 시각")
    @GetMapping("/settings")
    public ApiResponse<Settings> settings() {
        return ApiResponse.ok(settingsService.get());
    }

    @Operation(summary = "자동 백업 설정 변경", description = "저장하면 재시작 없이 바로 새 시각으로 예약된다.")
    @PutMapping("/settings")
    public ApiResponse<Settings> updateSettings(@Valid @RequestBody SettingsRequest req) {
        return ApiResponse.ok(settingsService.update(req));
    }

    @Operation(summary = "백업 파일 삭제(시스템 관리자)", description = "수동·복원 전 백업은 자동 정리되지 않아 여기서 지운다.")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @DeleteMapping("/{fileName}")
    public ApiResponse<Void> delete(@PathVariable String fileName) {
        backupService.delete(fileName);
        return ApiResponse.ok();
    }

    @Operation(summary = "복원 전 확인(시스템 관리자)",
            description = "체크섬·백업 파일 형식·DB 버전을 확인해 확인 창에 보여 줄 정보를 돌려준다. source=import 면 가져온 파일.")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/{fileName}/check")
    public ApiResponse<RestoreService.Check> check(@PathVariable String fileName,
                                                   @RequestParam(required = false) String source) {
        return ApiResponse.ok(restoreService.check(fileName, RestoreService.Source.of(source)));
    }

    @Operation(summary = "복원(시스템 관리자)",
            description = "본문 {\"confirm\":\"복원\"} 필수. 끝나면 모든 로그인 세션이 지워진다(본인 포함). "
                    + "source=import 면 가져온 파일.")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @PostMapping("/{fileName}/restore")
    public ApiResponse<RestoreService.Result> restore(@PathVariable String fileName,
                                                      @RequestParam(required = false) String source,
                                                      @RequestBody(required = false) RestoreRequest req,
                                                      HttpServletRequest request) {
        if (req == null || !RestoreService.CONFIRM.equals(req.confirm())) {
            throw new BusinessException(ErrorCode.BACKUP_RESTORE_CONFIRM_REQUIRED);
        }
        UserPrincipal me = SecurityUtils.currentPrincipal();
        RestoreService.Result result = restoreService.restore(fileName, RestoreService.Source.of(source),
                me.getId(), me.getName());
        // 세션 표는 이미 비웠다. 이 요청의 세션도 끝내 응답 뒤 다시 저장되지 않게 한다
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // 이미 끝난 세션
            }
        }
        SecurityContextHolder.clearContext();
        return ApiResponse.ok(result);
    }

    @Operation(summary = "백업 파일 내려받기(시스템 관리자)")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @GetMapping("/{fileName}/download")
    public ResponseEntity<Resource> download(@PathVariable String fileName) {
        Path file = backupService.resolve(fileName);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
