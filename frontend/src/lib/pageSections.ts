/**
 * 탭(보기)이 있는 화면의 탭 이름. 화면의 탭과 상단 위치 표시("정책 · 휴가종류 > 연차 정책")가 같이 쓴다.
 * 탭은 주소의 검색어로 고른다(?tab=, 캘린더는 ?view=). 첫 항목이 기본 탭이다.
 */
export interface PageSection {
  value: string;
  label: string;
}

/** 정책 · 휴가종류(/admin/policy?tab=) */
export const POLICY_TABS: PageSection[] = [
  { value: "policy", label: "연차 정책" },
  { value: "types", label: "휴가 종류" },
  { value: "rules", label: "포상 · 경조사 · 공가" },
  { value: "blackout", label: "블랙아웃" },
  { value: "holidays", label: "공휴일" },
  { value: "promotion", label: "촉진 · 미사용" },
  { value: "automation", label: "자동화" },
  { value: "backup", label: "백업" },
];

/** 결재함(/approvals?tab=) */
export const APPROVAL_TABS: PageSection[] = [
  { value: "pending", label: "결재 대기" },
  { value: "list", label: "휴가 목록" },
];

/** 리포트(/admin/reports?tab=) */
export const REPORT_TABS: PageSection[] = [
  { value: "status", label: "회사 현황" },
  { value: "excel", label: "연차현황표" },
];

/** 캘린더 보기(/calendar?view=, 값은 소문자로 주소에 들어간다) */
export const CALENDAR_VIEWS: PageSection[] = [
  { value: "all", label: "전체" },
  { value: "schedule", label: "일정" },
  { value: "department", label: "부서" },
  { value: "personal", label: "개인" },
];

/** 주소의 값으로 탭을 찾는다. 없거나 모르는 값이면 기본(첫) 탭 */
export function sectionOf(sections: PageSection[], value: string | null | undefined): PageSection {
  return sections.find((s) => s.value === value?.toLowerCase()) ?? sections[0];
}
