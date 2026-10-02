package com.company.leave.leave.repository;

import java.time.Instant;

/** 직원·연차 기간별 촉진 안내 발송 횟수와 최근 발송 시각. */
public record PromotionNoticeSummary(Long employeeId, int balanceYear, long count, Instant lastSentAt) {
}
