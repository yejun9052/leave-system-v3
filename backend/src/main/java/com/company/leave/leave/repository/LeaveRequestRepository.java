package com.company.leave.leave.repository;

import com.company.leave.leave.domain.LeaveRequest;
import com.company.leave.leave.domain.LeaveRequestStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    Page<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId, Pageable pageable);

    List<LeaveRequest> findByEmployeeIdAndStatusOrderByStartDateDesc(
            Long employeeId, LeaveRequestStatus status);

    /** 기간이 겹치는 본인의 대기/승인 신청 존재 여부 (중복 신청 방지) */
    @Query("""
            select count(r) > 0 from LeaveRequest r
            where r.employee.id = :employeeId
              and r.status in (com.company.leave.leave.domain.LeaveRequestStatus.PENDING,
                               com.company.leave.leave.domain.LeaveRequestStatus.APPROVED)
              and r.startDate <= :end and r.endDate >= :start
            """)
    boolean existsOverlap(@Param("employeeId") Long employeeId,
                          @Param("start") LocalDate start,
                          @Param("end") LocalDate end);

    /** 본인의 특정 연도 대기중 신청의 "차감 예정액" 합계(비차감 유형은 0이라 자연히 제외) */
    @Query("""
            select coalesce(sum(r.deductedDays), 0) from LeaveRequest r
            where r.employee.id = :employeeId and r.appliedYear = :year
              and r.status = com.company.leave.leave.domain.LeaveRequestStatus.PENDING
            """)
    BigDecimal sumPendingDeductedDays(@Param("employeeId") Long employeeId, @Param("year") int year);

    /** 결재 대상: 주어진 사용자들의 신규 신청(PENDING) + 취소 요청(CANCEL_REQUESTED) */
    @Query("""
            select r from LeaveRequest r
            where r.employee.id in :employeeIds
              and r.status in :statuses
            order by r.createdAt asc
            """)
    List<LeaveRequest> findForApproval(@Param("employeeIds") Collection<Long> employeeIds,
                                       @Param("statuses") Collection<LeaveRequestStatus> statuses);

    long countByEmployeeIdInAndStatus(Collection<Long> employeeIds, LeaveRequestStatus status);

    long countByStatus(LeaveRequestStatus status);

    long countByStatusIn(Collection<LeaveRequestStatus> statuses);

    /** 특정 기간 내 승인된 휴가 (팀 동시부재/캘린더용) */
    @Query("""
            select r from LeaveRequest r
            where r.status = com.company.leave.leave.domain.LeaveRequestStatus.APPROVED
              and r.startDate <= :end and r.endDate >= :start
            """)
    List<LeaveRequest> findApprovedBetween(@Param("start") LocalDate start,
                                           @Param("end") LocalDate end);

    /** 주어진 상태이면서 기간이 [start, end] 와 겹치는 신청 (공휴일 추가 시 재계산 대상 조회). */
    @Query("""
            select r from LeaveRequest r
            where r.status in :statuses
              and r.startDate <= :end and r.endDate >= :start
            """)
    List<LeaveRequest> findByStatusInOverlapping(@Param("statuses") Collection<LeaveRequestStatus> statuses,
                                                 @Param("start") LocalDate start,
                                                 @Param("end") LocalDate end);
}
