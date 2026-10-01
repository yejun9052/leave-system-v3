import { useRef, useState } from "react";
import { CalendarDays, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { monthGridDates, shiftMonth, todayString, weekdayLabel } from "./useDateSelection";

/** 칸 아래 점·읽어 주기용 일정 표시. 점 색: 공휴일·연차 제한 빨강, 휴가 primary, 회사 일정 주황 */
export interface DayMark {
  kind: "HOLIDAY" | "BLACKOUT" | "LEAVE" | "EVENT";
  title: string;
}

const DOT_CLASS: Record<DayMark["kind"], string> = {
  HOLIDAY: "bg-red-500",
  BLACKOUT: "bg-red-500",
  LEAVE: "bg-primary",
  EVENT: "bg-orange-500",
};

const WEEK_HEADER = ["일", "월", "화", "수", "목", "금", "토"];

/** 이만큼 누르고 있으면 날짜 메뉴, 이만큼 이상 가로로 밀면 달 이동 */
const LONG_PRESS_MS = 500;
const SWIPE_MIN_PX = 50;
const TAP_SLOP_PX = 10;

interface TouchGesture {
  x: number;
  y: number;
  moved: boolean;
  longPressed: boolean;
  timer: number;
}

/**
 * 모바일 전용 월 격자(폭 640px 미만). 칸 안에 제목 글자를 넣지 않고 점으로만 표시한다.
 * 탭은 날짜 선택(아래 요약이 바뀜), 길게 누르기는 날짜 메뉴, 좌우로 밀면 이전·다음 달.
 */
export default function MobileMonthView({
  month,
  focused,
  picking,
  pickRange,
  marks,
  holidayNames,
  query,
  onQueryChange,
  onMonthChange,
  onToday,
  onSelectDate,
  onLongPress,
}: {
  /** "YYYY-MM" */
  month: string;
  /** 요약에 보여 줄 고른 날 */
  focused: string;
  /** 신청 시트의 "날짜 바꾸기" 선택 모드인지 */
  picking: boolean;
  /** 선택 모드에서 칠할 시작~종료(아직 안 골랐으면 null) */
  pickRange: { start: string; end: string } | null;
  marks: Map<string, DayMark[]>;
  /** 공휴일 이름(날짜 숫자 빨강·읽어 주기용) */
  holidayNames: Map<string, string>;
  query: string;
  onQueryChange: (q: string) => void;
  onMonthChange: (month: string) => void;
  onToday: () => void;
  onSelectDate: (date: string) => void;
  onLongPress: (date: string, x: number, y: number) => void;
}) {
  const [searchOpen, setSearchOpen] = useState(false);
  const touch = useRef<TouchGesture | null>(null);
  const today = todayString();
  const dates = monthGridDates(month);
  const [year, mon] = month.split("-").map(Number);
  const currentYear = Number(today.slice(0, 4));

  const dateOf = (target: EventTarget | null): string | null =>
    target instanceof Element ? (target.closest("[data-date]")?.getAttribute("data-date") ?? null) : null;

  const onTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length !== 1) return;
    const target = e.target;
    const p = e.touches[0];
    const gesture: TouchGesture = { x: p.clientX, y: p.clientY, moved: false, longPressed: false, timer: 0 };
    gesture.timer = window.setTimeout(() => {
      const date = dateOf(target);
      if (!date) return;
      gesture.longPressed = true;
      onLongPress(date, gesture.x, gesture.y);
    }, LONG_PRESS_MS);
    touch.current = gesture;
  };

  const onTouchMove = (e: React.TouchEvent) => {
    const t = touch.current;
    if (!t || t.moved) return;
    const p = e.touches[0];
    if (Math.abs(p.clientX - t.x) > TAP_SLOP_PX || Math.abs(p.clientY - t.y) > TAP_SLOP_PX) {
      t.moved = true;
      window.clearTimeout(t.timer);
    }
  };

  const onTouchEnd = (e: React.TouchEvent) => {
    const t = touch.current;
    if (!t) return;
    window.clearTimeout(t.timer);
    const p = e.changedTouches[0];
    const dx = p.clientX - t.x;
    const dy = p.clientY - t.y;
    // 가로 이동이 세로보다 크고 50px 이상일 때만 달 이동(세로 스크롤은 그대로)
    if (!t.longPressed && Math.abs(dx) > Math.abs(dy) && Math.abs(dx) >= SWIPE_MIN_PX) {
      onMonthChange(shiftMonth(month, dx < 0 ? 1 : -1));
    }
    // 이 터치 뒤에 오는 click 까지 판단한 뒤 비운다
    window.setTimeout(() => {
      if (touch.current === t) touch.current = null;
    }, 400);
  };

  const onCellClick = (date: string) => {
    // 스와이프·길게 누르기로 끝난 터치는 탭으로 보지 않는다
    const t = touch.current;
    if (t && (t.moved || t.longPressed)) return;
    onSelectDate(date);
  };

  const onCellContextMenu = (e: React.MouseEvent, date: string) => {
    // 안드로이드는 길게 누르면 contextmenu 도 온다: 이미 연 메뉴를 두고 기본 메뉴만 막는다
    e.preventDefault();
    const t = touch.current;
    if (t?.longPressed) return;
    if (t) {
      window.clearTimeout(t.timer);
      t.longPressed = true;
    }
    onLongPress(date, e.clientX, e.clientY);
  };

  const toggleSearch = () => {
    if (searchOpen) onQueryChange("");
    setSearchOpen(!searchOpen);
  };

  return (
    <section aria-label="월간 캘린더" className="space-y-3">
      <div className="flex items-center gap-2">
        <h1 className="flex-1 text-3xl font-bold text-foreground">
          {year !== currentYear && <span className="mr-1.5 text-lg font-semibold text-muted-foreground">{year}년</span>}
          {mon}월
        </h1>
        <button
          type="button"
          onClick={toggleSearch}
          aria-label="이름으로 찾기"
          aria-expanded={searchOpen}
          className={cn(
            "flex h-10 w-10 items-center justify-center rounded-xl border bg-card",
            searchOpen && "border-primary",
          )}
        >
          <Search className="h-4 w-4 text-muted-foreground" />
        </button>
        <button
          type="button"
          onClick={onToday}
          aria-label="오늘로 이동"
          className="flex h-10 w-10 items-center justify-center rounded-xl border bg-card"
        >
          <CalendarDays className="h-4 w-4 text-muted-foreground" />
        </button>
      </div>

      {searchOpen && (
        <Input
          autoFocus
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
          placeholder="이름으로 찾기 (이 달 일정에서)"
          aria-label="이름으로 찾기"
        />
      )}

      <div className="grid grid-cols-7 gap-1.5 text-center text-xs">
        {WEEK_HEADER.map((w, i) => (
          <span key={w} className={i === 0 ? "text-red-500" : i === 6 ? "text-blue-500" : "text-muted-foreground"}>
            {w}
          </span>
        ))}
      </div>

      {/* touch-pan-y: 가로 밀기는 달 이동에 쓰고 브라우저의 뒤로 가기 제스처로 넘기지 않는다(세로 스크롤은 그대로) */}
      <div
        className="grid touch-pan-y select-none grid-cols-7 gap-1.5 [-webkit-touch-callout:none]"
        onTouchStart={onTouchStart}
        onTouchMove={onTouchMove}
        onTouchEnd={onTouchEnd}
        onTouchCancel={() => {
          if (touch.current) window.clearTimeout(touch.current.timer);
          touch.current = null;
        }}
      >
        {dates.map((date) => {
          const inMonth = date.slice(0, 7) === month;
          const dayMarks = marks.get(date) ?? [];
          const holiday = holidayNames.get(date);
          const sunday = weekdayLabel(date) === "일";
          const saturday = weekdayLabel(date) === "토";
          const isToday = date === today;
          const isFocused = !picking && date === focused;
          const inPick = picking && !!pickRange && date >= pickRange.start && date <= pickRange.end;
          const [, m, d] = date.split("-").map(Number);
          const label =
            `${m}월 ${d}일 ${weekdayLabel(date)}요일` +
            (holiday ? `, 공휴일 ${holiday}` : "") +
            (dayMarks.length > 0 ? `, 일정 ${dayMarks.length}건` : "");
          return (
            <button
              key={date}
              type="button"
              data-date={date}
              aria-label={label}
              aria-pressed={isFocused || inPick}
              aria-current={isToday ? "date" : undefined}
              onClick={() => onCellClick(date)}
              onContextMenu={(e) => onCellContextMenu(e, date)}
              className={cn(
                "flex aspect-square min-h-11 flex-col items-start justify-between rounded-xl border p-1.5 text-left",
                inMonth ? "bg-card" : "border-transparent bg-muted/40",
                inPick && "bg-primary/10",
                isFocused && "border-2 border-primary bg-primary/5",
              )}
            >
              <span
                className={cn(
                  "text-sm font-semibold",
                  !inMonth
                    ? "text-muted-foreground/50"
                    : isToday
                      ? "inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-primary-foreground"
                      : holiday || sunday
                        ? "text-red-500"
                        : saturday
                          ? "text-blue-500"
                          : "text-foreground",
                )}
              >
                {d}
              </span>
              {inMonth && dayMarks.length > 0 && (
                <span aria-hidden className="flex gap-0.5">
                  {dayMarks.slice(0, 3).map((mk, i) => (
                    <span key={i} className={cn("h-1.5 w-1.5 rounded-full", DOT_CLASS[mk.kind])} />
                  ))}
                </span>
              )}
            </button>
          );
        })}
      </div>
    </section>
  );
}
