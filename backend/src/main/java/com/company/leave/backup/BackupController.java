package com.company.leave.backup;

import com.company.leave.backup.BackupDtos.BackupFile;
import com.company.leave.backup.BackupDtos.Kind;
import com.company.leave.backup.BackupDtos.Overview;
import com.company.leave.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정책 → 백업 탭. 백업 실행·목록은 인사관리자·시스템 관리자, 내려받기는 시스템 관리자만.
 * 실행(POST)과 내려받기(GET .../download)는 이벤트 로그에 남는다(AuditAspect).
 */
@Tag(name = "Backup", description = "DB 백업")
@RestController
@RequestMapping("/api/backups")
@PreAuthorize("hasAnyRole('HR_ADMIN','SYSTEM_ADMIN')")
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
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
