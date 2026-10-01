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
