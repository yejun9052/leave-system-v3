-- 연차 사용 금지 기간(블랙아웃)에도 신청할 수 있는 휴가 종류. 차감 방식과 따로 정한다.
-- 회사 답변: 경조사·공가는 금지 기간에도 신청 가능, 병가는 불가. 그 외(연차·반차·시간차·직접 만든 종류)는 불가.

ALTER TABLE leave_types ADD COLUMN allowed_during_blackout BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE leave_types SET allowed_during_blackout = TRUE WHERE code IN ('CONDOLENCE', 'OFFICIAL');
