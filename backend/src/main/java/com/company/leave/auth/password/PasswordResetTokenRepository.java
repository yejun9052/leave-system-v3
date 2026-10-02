package com.company.leave.auth.password;

import com.company.leave.auth.password.domain.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 새 토큰 발급 전, 그 사용자의 아직 쓰지 않은 토큰을 모두 무효화(삭제). */
    @Modifying
    @Query("delete from PasswordResetToken t where t.employeeId = :employeeId and t.usedAt is null")
    int deleteUnusedByEmployeeId(@Param("employeeId") Long employeeId);

    /**
     * 사용 처리. 아직 안 쓴 토큰일 때만 갱신되므로(조건부 UPDATE) 동시에 두 번 제출돼도
     * 1건만 성공한다. 반환값 1 = 이번 요청이 사용함, 0 = 이미 사용됨.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
