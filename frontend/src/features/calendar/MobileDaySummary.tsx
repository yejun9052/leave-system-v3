import { useQuery } from "@tanstack/react-query";
import { Loader2, Plus } from "lucide-react";
import { calendarApi, type DayLeaveDto } from "@/api/calendar";
import { cn } from "@/lib/utils";
import { weekdayLabel } from "./useDateSelection";

function leaveLabel(l: DayLeaveDto): string {
  if (l.portion === "HOURLY") return l.hours != null ? `${l.leaveTypeName} ${l.hours}시간` : l.leaveTypeName;
  return l.portion === "FULL" ? `${l.leaveTypeName} · 종일` : l.leaveTypeName;
}

interface SummaryItem {
  key: string;
  dot: string;
  title: string;
  sub?: string;
  /** 회사 일정이면 그 id("E12"), 그 밖은 null */
  eventId: string | null;
}

/**
 * 모바일: 고른 날의 요약(큰 날짜·요일·연월, 일정 카드 목록)과 "+ 추가"·하단 추가 버튼. 달력 아래에 둔다.
 * 이름 검색어가 있으면 휴가·일정을 이름(제목)으로 거른다(공휴일·연차 제한은 그대로).
 */
export default function MobileDaySummary({
  date,
  canApply,
  query,
  blackoutNames,
  onApply,
  onOpenItem,
}: {
  date: string;
  canApply: boolean;
  query: string;
  /** 그날 걸린 블랙아웃(연차 사용 제한) 이름 */
  blackoutNames: string[];
  onApply: (date: string) => void;
  /** 카드 탭: 회사 일정이면 eventId, 그 밖은 null(그날 상세) */
  onOpenItem: (eventId: string | null) => void;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["calendarDay", date],
    queryFn: () => calendarApi.day(date),
  });
  const [y, m, d] = date.split("-").map(Number);
  const q = query.trim();

  const items: SummaryItem[] = [];
  if (data?.holidayName) {
    items.push({ key: "holiday", dot: "bg-red-500", title: data.holidayName, eventId: null });
  }
  blackoutNames.forEach((name, i) =>
    items.push({ key: `blackout-${i}`, dot: "bg-red-500", title: `연차 제한 · ${name}`, eventId: null }),
  );
  for (const l of data?.leaves ?? []) {
    if (q && !l.employeeName.includes(q)) continue;
    const pending = l.status === "PENDING" || l.status === "LEAD_APPROVED";
    items.push({
      key: `L${l.id}`,
      dot: "bg-primary",
      title: `${l.employeeName}${l.mine ? " · 나" : ""}`,
      sub: leaveLabel(l) + (pending ? " · 결재 대기" : ""),
      eventId: null,
    });
  }
  for (const e of data?.events ?? []) {
    if (q && !e.title.includes(q)) continue;
    items.push({ key: e.id, dot: "bg-orange-500", title: e.title, sub: "회사 일정", eventId: e.id });
  }

  return (
    <section aria-label={`${m}월 ${d}일 선택한 날짜 상세`} className="space-y-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="flex items-baseline gap-2">
            <strong className="text-4xl font-bold leading-none text-foreground">{d}</strong>
            <span className="font-semibold text-foreground">{weekdayLabel(date)}요일</span>
          </div>
          <p className="mt-1 text-xs text-muted-foreground">
            {y}년 {m}월
          </p>
        </div>
        {canApply && (
          <button
            type="button"
            onClick={() => onApply(date)}
            className="rounded-full border border-primary px-3 py-1.5 text-xs font-semibold text-primary"
          >
            + 추가
          </button>
        )}
      </div>

      {isLoading ? (
        <Loader2 className="mx-auto h-5 w-5 animate-spin text-muted-foreground" />
      ) : items.length === 0 ? (
        <p className="text-sm text-muted-foreground">{q ? `"${q}" 일정이 없습니다` : "일정이 없습니다"}</p>
      ) : (
        <ul className="space-y-2">
          {items.map((it) => (
            <li key={it.key}>
              <button
                type="button"
                onClick={() => onOpenItem(it.eventId)}
                className="flex w-full items-center gap-3 rounded-xl border bg-card px-4 py-3 text-left"
              >
                <span aria-hidden className={cn("h-2.5 w-2.5 shrink-0 rounded-full", it.dot)} />
                <strong className="truncate text-sm font-semibold text-foreground">{it.title}</strong>
                {it.sub && <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">{it.sub}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}

      {canApply && (
        <button
          type="button"
          onClick={() => onApply(date)}
          className="flex h-14 w-full items-center justify-between rounded-full bg-secondary px-6 text-secondary-foreground"
        >
          <span className="font-semibold">
            {m}월 {d}일에 추가
          </span>
          <Plus className="h-6 w-6" aria-hidden />
        </button>
      )}
    </section>
  );
}
