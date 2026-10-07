-- 경조사 규정별 연간 사용 횟수. 1년은 달력 연도(1~12월), 휴가 시작일 기준. 비우면(NULL) 제한 없음.
-- 결재 대기·승인·취소 요청 중인 신청을 센다. 직원 신청·미리보기는 막고, 인사관리자 직접 등록은 확인 뒤 허용한다.
ALTER TABLE special_leave_rules ADD COLUMN annual_limit INTEGER;
ALTER TABLE special_leave_rules ADD CONSTRAINT chk_special_leave_rules_annual_limit
    CHECK (annual_limit IS NULL OR annual_limit >= 1);

-- 생일 반차(0.5일 "생일" 규정)는 연 1회. 적용 전 로컬 DB: 해당 규정 1건(id 9)
UPDATE special_leave_rules SET annual_limit = 1 WHERE name = '생일' AND days = 0.5;
