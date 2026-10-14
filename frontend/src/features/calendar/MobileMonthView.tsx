import { useRef, useState } from "react";
import { CalendarDays, Search } from "lucide-react";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { useMediaQuery } from "@/lib/useMediaQuery";
import { monthGridDates, shiftMonth, todayString, weekdayLabel } from "./useDateSelection";

/** 칸 아래 점·읽어 주기용 일정 표시. 점 색: 공휴일·연차 제한 빨강, 휴가 primary, 회사 일정 주황 */
export interface DayMark {
  kind: "HOLIDAY" | "BLACKOUT" | "PERIOD" | "LEAVE" | "EVENT";
  title: string;
}

const DOT_CLASS: Record<DayMark["kind"], string> = {
  HOLIDAY: "bg-red-500",
  BLACKOUT: "bg-red-500",
  PERIOD: "bg-teal-700",
  LEAVE: "bg-primary",
  EVENT: "bg-orange-500",
};

const WEEK_HEADER = ["일", "월", "화", "수", "목", "금", "토"];

/** 이만큼 누르고 있으면 날짜 메뉴 */
const LONG_PRESS_MS = 500;
const TAP_SLOP_PX = 10;
/** 놓았을 때 달을 넘기는 기준: 화면 폭의 이만큼 밀었거나, 이 속도(px/ms) 이상으로 튕겼을 때 */
const SWIPE_RATIO = 0.18;
const FLICK_SPEED = 0.35;
/** 놓은 뒤 옆 달로 넘어가거나 제자리로 돌아오는 시간 */
const SNAP_MS = 260;
const SNAP_EASE = "cubic-bezier(0.2, 0.8, 0.2, 1)";

interface TouchGesture {
  x: number;
  y: number;
  moved: boolean;
  /** 처음 움직인 방향이 가로였는지(가로면 달력이 손가락을 따라 움직이고 놓으면 달 이동) */
  horizontal: boolean;
  longPressed: boolean;
  timer: number;
  /** 튕긴 속도를 재려고 마지막 위치·시각을 기억한다 */
  lastX: number;
  lastT: number;
  velocity: number;
}

/**
 * 모바일 전용 월 격자(폭 640px 미만). 칸 안에 제목 글자를 넣지 않고 점으로만 표시한다.
 * 탭은 날짜 선택(아래 요약이 바뀜), 길게 누르기는 날짜 메뉴, 좌우로 밀면 이전·다음 달.
 * 안드로이드 기본 캘린더처럼 이전·지금·다음 달을 옆으로 나란히 두고, 미는 동안 옆 달이 따라 들어온다.
 * 화면 폭의 18% 이상 밀거나 빠르게 튕기면 넘어가고, 덜 밀면 제자리로 돌아온다.
 * 오늘 버튼처럼 밀지 않고 달이 바뀌면 그 방향에서 미끄러져 들어온다. 움직임 줄이기 설정이면 애니메이션 없이 바꾼다.
 */
export default function MobileMonthView({
  month,
  focused,
  picking,
  pickSpans,
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
  /** 선택 모드에서 칠할 고른 날짜의 이어진 묶음(사이에 낀 주말·공휴일도 칠한다) */
  pickSpans: { startDate: string; endDate: string }[];
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
  const reduceMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  const viewportRef = useRef<HTMLDivElement>(null);
  /** 손가락을 따라 옮긴 거리(px). 0 이면 제자리 */
  const [offset, setOffset] = useState(0);
  /** 보여 줄 칸: -1 이전 달, 0 지금 달, 1 다음 달(놓은 뒤 옆 달로 넘어가는 동안만 ±1) */
  const [slot, setSlot] = useState<-1 | 0 | 1>(0);
  /** 놓은 뒤 넘어가거나 돌아오는 중(이때만 transition 을 켠다) */
  const [settling, setSettling] = useState(false);
  const snapping = useRef(false);
  // 밀지 않고 달이 바뀌면(오늘 버튼 등) 그 방향에서 미끄러져 들어온다. 밀어서 넘긴 경우는 이미 보이고 있어 애니메이션 없음
  const prevMonth = useRef(month);
  const swiped = useRef(false);
  const enterFrom = useRef<"right" | "left" | null>(null);
  if (prevMonth.current !== month) {
    enterFrom.current = swiped.current ? null : month > prevMonth.current ? "right" : "left";
    swiped.current = false;
    prevMonth.current = month;
  }
  const today = todayString();
  const [year, mon] = month.split("-").map(Number);
  const currentYear = Number(today.slice(0, 4));

  const dateOf = (target: EventTarget | null): string | null =>
    target instanceof Element ? (target.closest("[data-date]")?.getAttribute("data-date") ?? null) : null;

  const width = () => viewportRef.current?.offsetWidth || 360;

  const onTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length !== 1 || snapping.current) return;
    const target = e.target;
    const p = e.touches[0];
    setSettling(false);
    const gesture: TouchGesture = {
      x: p.clientX,
      y: p.clientY,
      moved: false,
      horizontal: false,
      longPressed: false,
      timer: 0,
      lastX: p.clientX,
      lastT: e.timeStamp,
      velocity: 0,
    };
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
    if (!t || t.longPressed) return;
    const p = e.touches[0];
    const dx = p.clientX - t.x;
    const dy = p.clientY - t.y;
    if (!t.moved && (Math.abs(dx) > TAP_SLOP_PX || Math.abs(dy) > TAP_SLOP_PX)) {
      t.moved = true;
      t.horizontal = Math.abs(dx) > Math.abs(dy);
      window.clearTimeout(t.timer);
    }
    if (!t.horizontal) return; // 세로 스크롤은 브라우저 몫
    const dt = e.timeStamp - t.lastT;
    if (dt > 0) t.velocity = (p.clientX - t.lastX) / dt;
    t.lastX = p.clientX;
    t.lastT = e.timeStamp;
    if (!reduceMotion) setOffset(dx);
  };

  /** 옆 달로 넘어간 뒤(애니메이션이 끝나면) 그 달을 가운데로 바꾼다. 보이는 모습은 그대로라 끊김이 없다 */
  const snapTo = (dir: -1 | 1) => {
    const next = shiftMonth(month, dir);
    if (reduceMotion) {
      onMonthChange(next);
      return;
    }
    snapping.current = true;
    setSettling(true);
    setOffset(0);
    setSlot(dir);
    window.setTimeout(() => {
      snapping.current = false;
      swiped.current = true;
      setSettling(false);
      setSlot(0);
      onMonthChange(next);
    }, SNAP_MS);
  };

  const onTouchEnd = (e: React.TouchEvent) => {
    const t = touch.current;
    if (!t) return;
    window.clearTimeout(t.timer);
    const p = e.changedTouches[0];
    const dx = p.clientX - t.x;
    if (t.horizontal && !t.longPressed) {
      const far = Math.abs(dx) >= width() * SWIPE_RATIO;
      const flick = Math.abs(t.velocity) >= FLICK_SPEED && Math.sign(t.velocity) === Math.sign(dx);
      if (dx !== 0 && (far || flick)) {
        snapTo(dx < 0 ? 1 : -1);
      } else {
        setSettling(true);
        setOffset(0);
      }
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

  /** 한 달 격자. 옆 달(active=false)은 보여 주기만 하고 누를 수 없다 */
  const renderGrid = (gridMonth: string, active: boolean) => (
    <div
      key={gridMonth}
      aria-hidden={!active}
      className={cn("grid w-full shrink-0 grid-cols-7 content-start gap-1.5", !active && "pointer-events-none")}
    >
      {monthGridDates(gridMonth).map((date) => {
        const inMonth = date.slice(0, 7) === gridMonth;
        const dayMarks = marks.get(date) ?? [];
        const holiday = holidayNames.get(date);
        const sunday = weekdayLabel(date) === "일";
        const saturday = weekdayLabel(date) === "토";
        const isToday = date === today;
        const isFocused = active && !picking && date === focused;
        const inPick = active && picking && pickSpans.some((s) => date >= s.startDate && date <= s.endDate);
        const [, m, d] = date.split("-").map(Number);
        const label =
          `${m}월 ${d}일 ${weekdayLabel(date)}요일` +
          (holiday ? `, 공휴일 ${holiday}` : "") +
          (dayMarks.length > 0 ? `, 일정 ${dayMarks.length}건` : "");
        return (
          <button
            key={date}
            type="button"
            tabIndex={active ? undefined : -1}
            data-date={active ? date : undefined}
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
  );

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

      {/* 이전·지금·다음 달을 옆으로 나란히 둔 띠. 가운데(지금 달)가 보이게 한 칸 왼쪽으로 옮겨 두고, 미는 만큼 더 옮긴다.
          touch-pan-y: 가로 밀기는 달 이동에 쓰고 브라우저의 뒤로 가기 제스처로 넘기지 않는다(세로 스크롤은 그대로).
          overflow-hidden: 옆 달은 밀 때만 보이고 페이지가 옆으로 넓어지지 않게 */}
      <div ref={viewportRef} className="-m-1 overflow-hidden p-1">
        {/* 밀지 않고 달이 바뀔 때의 들어오는 애니메이션은 바깥 칸에(띠의 위치 이동과 겹치지 않게) */}
        <div
          key={month}
          className={cn(
            !reduceMotion && enterFrom.current === "right" && "animate-in fade-in-50 slide-in-from-right-1/3 duration-300 ease-out",
            !reduceMotion && enterFrom.current === "left" && "animate-in fade-in-50 slide-in-from-left-1/3 duration-300 ease-out",
          )}
        >
        <div
          className="flex w-full touch-pan-y select-none items-start gap-2 [-webkit-touch-callout:none]"
          style={{
            // 달 사이 간격(gap 0.5rem)만큼 더 옮겨 옆 달이 딱 맞춰 들어오게
            transform: `translateX(calc(${-(slot + 1) * 100}% - ${(slot + 1) * 0.5}rem + ${offset}px))`,
            transition: settling ? `transform ${SNAP_MS}ms ${SNAP_EASE}` : "none",
          }}
          onTouchStart={onTouchStart}
          onTouchMove={onTouchMove}
          onTouchEnd={onTouchEnd}
          onTouchCancel={() => {
            if (touch.current) window.clearTimeout(touch.current.timer);
            touch.current = null;
            setSettling(true);
            setOffset(0);
          }}
        >
          {renderGrid(shiftMonth(month, -1), false)}
          {renderGrid(month, true)}
          {renderGrid(shiftMonth(month, 1), false)}
        </div>
        </div>
      </div>
    </section>
  );
}
