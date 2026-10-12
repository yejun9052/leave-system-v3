const pad = (n: number) => String(n).padStart(2, "0");

/**
 * 시각 표시: "2026-01-01 09:05:30" (브라우저 시간대 = 한국 시간).
 * 화면의 날짜와 검색에 쓰는 날짜(2026-01-01)를 같은 모양으로 맞춘다.
 */
export function formatDateTime(value: string | Date): string {
  const d = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(d.getTime())) return String(value);
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  );
}

/**
 * Date 의 로컬(브라우저 = 한국 시간) 날짜 "YYYY-MM-DD". FullCalendar 가 넘기는 날짜는 로컬 자정이다.
 * toISOString() 은 UTC 날짜라 한국 시간 00:00~08:59 에는 어제가 되므로 쓰지 않는다.
 */
export function localDateString(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** 연도 입력 칸 값이 쓸 수 있는 연도(2000~2100)인지. 칸을 비우는 중("")이면 false */
export function isValidYear(text: string): boolean {
  const year = Number(text);
  return text.trim() !== "" && Number.isInteger(year) && year >= 2000 && year <= 2100;
}

/** 로컬 시간 기준 오늘 "YYYY-MM-DD" */
export function todayString(): string {
  return localDateString(new Date());
}
