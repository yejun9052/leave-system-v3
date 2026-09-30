package com.company.leave.mail;

/**
 * 계정 메일 발송 요청 이벤트. 발행한 트랜잭션이 커밋된 뒤에만 발송된다({@link AccountMailService}).
 * 임시 비밀번호·재설정 토큰 원문은 이 객체(메모리)로만 전달하고 로그·DB 에 남기지 않는다.
 */
public final class AccountMailEvents {

    private AccountMailEvents() {
    }

    /** 계정 생성: 로그인 주소 + 임시 비밀번호 안내. */
    public record AccountCreated(String email, String name, String temporaryPassword) {

        @Override
        public String toString() {
            return "AccountCreated[email=" + email + "]";
        }
    }

    /**
     * 병가·공가 승인으로 남은 연차가 소멸됨.
     *
     * @param leaveTypeName 소멸을 일으킨 휴가 종류(병가·공가)
     * @param forfeitedDays 소멸 일수(예: "0.5")
     * @param period        휴가 기간 표시(예: "2027-05-04 ~ 2027-05-06")
     */
    public record LeaveForfeited(String email, String name, String leaveTypeName,
                                 String forfeitedDays, String period) {
    }

    /** 비밀번호 재설정: 1회용 토큰 링크 안내. */
    public record PasswordReset(String email, String name, String resetToken) {

        @Override
        public String toString() {
            return "PasswordReset[email=" + email + "]";
        }
    }
}
