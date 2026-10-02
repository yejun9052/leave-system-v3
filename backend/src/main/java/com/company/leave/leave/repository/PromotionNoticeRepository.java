package com.company.leave.leave.repository;

import com.company.leave.leave.domain.PromotionNotice;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromotionNoticeRepository extends JpaRepository<PromotionNotice, Long> {

    /** 직원·연차 기간별 발송 횟수와 최근 발송 시각. */
    @Query("""
            select new com.company.leave.leave.repository.PromotionNoticeSummary(
                       n.employeeId, n.balanceYear, count(n), max(n.sentAt))
            from PromotionNotice n
            where n.employeeId in :employeeIds
            group by n.employeeId, n.balanceYear
            """)
    List<PromotionNoticeSummary> summarize(@Param("employeeIds") Collection<Long> employeeIds);
}
