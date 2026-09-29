package com.company.leave.leave;

import com.company.leave.common.exception.BusinessException;
import com.company.leave.common.exception.ErrorCode;
import com.company.leave.leave.domain.LeaveBalance;
import com.company.leave.leave.dto.LeaveBalanceResponse;
import com.company.leave.leave.repository.LeaveBalanceRepository;
import com.company.leave.leave.repository.LeaveRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveBalanceService {

    private final LeaveBalanceRepository balanceRepository;
    private final LeaveRequestRepository requestRepository;

    public LeaveBalanceService(LeaveBalanceRepository balanceRepository,
                               LeaveRequestRepository requestRepository) {
        this.balanceRepository = balanceRepository;
        this.requestRepository = requestRepository;
    }

    /** 특정 연도 잔액(대기중 신청 포함) 응답. 없으면 0 기반 응답. */
    @Transactional
    public LeaveBalanceResponse getResponse(Long employeeId, int year) {
        LeaveBalance balance = getOrCreate(employeeId, year);
        BigDecimal pending = requestRepository.sumPendingDeductedDays(employeeId, year);
        return LeaveBalanceResponse.of(balance, pending);
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceResponse> listResponses(Long employeeId) {
        return balanceRepository.findByEmployeeIdOrderByYearDesc(employeeId).stream()
                .map(b -> LeaveBalanceResponse.of(b,
                        requestRepository.sumPendingDeductedDays(employeeId, b.getYear())))
                .toList();
    }

    public int currentYear() {
        return LocalDate.now().getYear();
    }

    @Transactional
    public LeaveBalance getOrCreate(Long employeeId, int year) {
        return balanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> balanceRepository.save(new LeaveBalance(employeeId, year)));
    }

    @Transactional(readOnly = true)
    public LeaveBalance require(Long employeeId, int year) {
        return balanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAVE_BALANCE_NOT_FOUND));
    }
}
