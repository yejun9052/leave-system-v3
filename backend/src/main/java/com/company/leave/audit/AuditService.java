package com.company.leave.audit;

import com.company.leave.audit.domain.AuditLog;
import com.company.leave.audit.dto.AuditLogResponse;
import com.company.leave.security.SecurityUtils;
import com.company.leave.security.UserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 현재 인증 사용자를 actor로 감사 로그를 기록.
     * 실패 이벤트도 남길 수 있도록 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId,
                       String detail, boolean success) {
        Long actorId = null;
        String actorName = "SYSTEM";
        try {
            UserPrincipal principal = SecurityUtils.currentPrincipal();
            actorId = principal.getId();
            actorName = principal.getName();
        } catch (RuntimeException ignored) {
            // 인증 컨텍스트가 없는 경우(배치/로그인 이전) SYSTEM 으로 기록
        }
        save(actorId, actorName, action, entityType, entityId, detail, success);
    }

    /** actor를 명시적으로 지정해 기록 (예: 로그인 성공/실패). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorName, String action, String entityType,
                       String entityId, String detail, boolean success) {
        save(actorId, actorName, action, entityType, entityId, detail, success);
    }

    private void save(Long actorId, String actorName, String action, String entityType,
                      String entityId, String detail, boolean success) {
        auditLogRepository.save(new AuditLog(actorId, actorName, action, entityType,
                entityId, detail, success));
    }

    /**
     * 이벤트 로그 검색(최신순). 표의 모든 칸(시간·사용자·동작·대상·결과·상세)으로 찾는다({@link AuditLogSearch}).
     * 공백으로 나눈 단어는 모두 맞아야 한다(예: "QA 로그인").
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String keyword, Pageable pageable) {
        Pageable newestFirst = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return auditLogRepository.findAll(AuditLogSearch.of(keyword), newestFirst).map(AuditLogResponse::from);
    }
}
