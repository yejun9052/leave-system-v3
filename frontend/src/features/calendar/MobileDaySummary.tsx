import { useQuery } from "@tanstack/react-query";
import { Loader2, Plus } from "lucide-react";
import { calendarApi, type DayLeaveDto } from "@/api/calendar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { formatKoreanDate } from "./useDateSelection";

function leaveLabel(l: DayLeaveDto): string {
  if (l.portion === "HOURLY") return l.hours != null ? `${l.leaveTypeName} ${l.hours}시간` : l.leaveTypeName;
  return l.portion === "FULL" ? `${l.leaveTypeName} · 종일` : l.leaveTypeName;
}

/** 모바일: 탭한 날짜의 요약(날짜·요일·휴가/일정 목록)과 "+ 신청" 버튼. 달력 아래에 둔다. */
export default function MobileDaySummary({
  date,
  canApply,
  onApply,
}: {
  date: string;
  canApply: boolean;
  onApply: (date: string) => void;
}) {
  const { data, isLoading } = useQuery({
    queryKey: ["calendarDay", date],
    queryFn: () => calendarApi.day(date),
  });
  const leaves = data?.leaves ?? [];
  const events = data?.events ?? [];

  return (
    <Card>
      <CardContent className="space-y-3 p-4">
        <div className="flex items-center gap-2">
          <h2 className="flex-1 font-semibold">
            {formatKoreanDate(date)}
            {data?.holidayName && (
              <Badge variant="destructive" className="ml-2">
                {data.holidayName}
              </Badge>
            )}
          </h2>
          {canApply && (
            <Button size="sm" onClick={() => onApply(date)}>
              <Plus className="h-4 w-4" /> 신청
            </Button>
          )}
        </div>
        {isLoading ? (
          <Loader2 className="mx-auto h-5 w-5 animate-spin text-muted-foreground" />
        ) : leaves.length === 0 && events.length === 0 ? (
          <p className="text-sm text-muted-foreground">휴가·일정이 없습니다.</p>
        ) : (
          <ul className="space-y-2 text-sm">
            {leaves.map((l) => (
              <li key={`L${l.id}`} className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: l.leaveTypeColor }} />
                <span className="font-medium">{l.employeeName}</span>
                {l.mine && (
                  <Badge variant="default" className="px-1.5 py-0">
                    나
                  </Badge>
                )}
                <span className="min-w-0 flex-1 truncate text-muted-foreground">{leaveLabel(l)}</span>
                {l.status !== "APPROVED" && l.status !== "CANCEL_REQUESTED" && (
                  <Badge variant="warning">결재 대기</Badge>
                )}
              </li>
            ))}
            {events.map((e) => (
              <li key={e.id} className="flex items-center gap-2">
                <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: e.colorHex }} />
                <span className="min-w-0 flex-1 truncate">{e.title}</span>
                <span className="text-xs text-muted-foreground">일정</span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
