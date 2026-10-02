import { useEffect, useMemo, useState } from "react";
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { MailWarning, Send } from "lucide-react";
import { promotionApi, type PromotionTarget } from "@/api/policy";
import { extractErrorMessage } from "@/api/client";
import { formatDays } from "@/lib/leaveFormat";
import { formatDateTime } from "@/lib/dateFormat";
import { useTableSort } from "@/lib/useTableSort";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  SortableTableHead,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";

const MONTHS = [6, 5, 4, 3, 2, 1];

/** 남은 날이 적을수록 눈에 띄게: 2개월 이하 빨강, 4개월 이하 노랑 */
function urgency(daysLeft: number): "destructive" | "warning" | "secondary" {
  if (daysLeft <= 61) return "destructive";
  if (daysLeft <= 122) return "warning";
  return "secondary";
}

/**
 * 연차 사용 촉진: 사용 기한(직원별 연차 기간의 마지막 날)이 N개월 안이고 남은 연차가 있는 직원을 찾아
 * 골라서 안내(앱 알림 + 메일)를 보낸다. 남은 기간은 서버가 계산한다.
 */
export default function PromotionTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const [months, setMonths] = useState(6);
  const [selected, setSelected] = useState<Set<number>>(new Set());

  // 조회 범위를 바꾸는 동안에는 이전 목록을 보여 준다(새 목록이 오기 전에 선택이 풀리지 않게)
  const { data: targets = [], isLoading, isPlaceholderData } = useQuery({
    queryKey: ["promotionTargets", months],
    queryFn: () => promotionApi.targets(months),
    placeholderData: keepPreviousData,
  });

  // 조회 범위가 바뀌어 목록에서 빠진 직원은 선택에서도 뺀다
  useEffect(() => {
    if (isPlaceholderData) return;
    const visible = new Set(targets.map((t) => t.employeeId));
    setSelected((prev) => {
      const next = new Set([...prev].filter((id) => visible.has(id)));
      return next.size === prev.size ? prev : next;
    });
  }, [targets, isPlaceholderData]);

  const { sorted, sort, toggle } = useTableSort(targets, {
    name: (t) => t.name,
    department: (t) => t.department,
    end: (t) => t.periodEnd,
    granted: (t) => t.granted,
    used: (t) => t.used,
    pending: (t) => t.pending,
    remaining: (t) => t.remaining,
    last: (t) => t.lastNotifiedAt,
  });

  const allSelected = targets.length > 0 && selected.size === targets.length;
  const someSelected = selected.size > 0 && !allSelected;
  const noEmail = useMemo(
    () => targets.filter((t) => selected.has(t.employeeId) && !t.hasEmail).length,
    [targets, selected],
  );

  const toggleAll = () =>
    setSelected(allSelected ? new Set() : new Set(targets.map((t) => t.employeeId)));
  const toggleOne = (id: number) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const send = useMutation({
    mutationFn: (ids: number[]) => promotionApi.send(ids),
    onSuccess: (r) => {
      toast({
        title: `${r.sent}명에게 촉진 안내를 보냈습니다. (메일 ${r.mailed}명)`,
        description: r.skipped > 0 ? `${r.skipped}명은 그 사이 대상이 아니게 되어 보내지 않았습니다.` : undefined,
        variant: "success",
      });
      setSelected(new Set());
      qc.invalidateQueries({ queryKey: ["promotionTargets"] });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  const onSend = async () => {
    const ok = await confirm({
      title: `${selected.size}명에게 연차 사용 촉진 안내를 보낼까요?`,
      description:
        "앱 알림과 메일이 발송되며 되돌릴 수 없습니다." +
        (noEmail > 0 ? ` 메일 주소가 없는 ${noEmail}명은 앱 알림만 받습니다.` : ""),
      confirmText: "발송",
    });
    if (ok) send.mutate([...selected]);
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">연차 사용 촉진</CardTitle>
        <p className="text-sm text-muted-foreground">
          사용 기한(직원별 연차 기간의 마지막 날)이 다가오는데 남은 연차가 있는 직원입니다. 결재 대기는 신청만 해 두고
          아직 승인되지 않은 일수입니다. 골라서 안내를 보내면 남은 기간을 계산해 메일과 앱 알림으로 알립니다.
        </p>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label className="text-xs">사용 기한까지</Label>
            <Select value={String(months)} onValueChange={(v) => setMonths(Number(v))}>
              <SelectTrigger className="w-[160px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {MONTHS.map((m) => (
                  <SelectItem key={m} value={String(m)}>
                    {m}개월 이하
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <span className="pb-2 text-sm text-muted-foreground">
            대상 {targets.length}명 · 선택 {selected.size}명
          </span>
          <Button className="ml-auto" size="sm" onClick={onSend} disabled={selected.size === 0 || send.isPending}>
            <Send className="h-4 w-4" /> 선택한 {selected.size}명에게 촉진 안내 발송
          </Button>
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <input
                  type="checkbox"
                  aria-label="전체 선택"
                  className="h-4 w-4 align-middle"
                  checked={allSelected}
                  ref={(el) => {
                    if (el) el.indeterminate = someSelected;
                  }}
                  onChange={toggleAll}
                  disabled={targets.length === 0}
                />
              </TableHead>
              <SortableTableHead sortKey="name" sort={sort} onSort={toggle}>이름</SortableTableHead>
              <SortableTableHead sortKey="department" sort={sort} onSort={toggle}>부서</SortableTableHead>
              <SortableTableHead sortKey="end" sort={sort} onSort={toggle}>사용 기한</SortableTableHead>
              <SortableTableHead sortKey="granted" sort={sort} onSort={toggle}>부여</SortableTableHead>
              <SortableTableHead sortKey="used" sort={sort} onSort={toggle}>사용</SortableTableHead>
              <SortableTableHead sortKey="pending" sort={sort} onSort={toggle}>결재 대기</SortableTableHead>
              <SortableTableHead sortKey="remaining" sort={sort} onSort={toggle}>남은 연차</SortableTableHead>
              <SortableTableHead sortKey="last" sort={sort} onSort={toggle}>최근 발송</SortableTableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={9} className="py-8 text-center text-muted-foreground">불러오는 중…</TableCell>
              </TableRow>
            ) : sorted.length > 0 ? (
              sorted.map((t) => <TargetRow key={t.employeeId} t={t} checked={selected.has(t.employeeId)} onToggle={toggleOne} />)
            ) : (
              <TableRow>
                <TableCell colSpan={9} className="py-8 text-center text-muted-foreground">
                  사용 기한이 {months}개월 안에 끝나는 촉진 대상이 없습니다.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function TargetRow({ t, checked, onToggle }: { t: PromotionTarget; checked: boolean; onToggle: (id: number) => void }) {
  return (
    <TableRow className={checked ? "bg-primary/5" : undefined}>
      <TableCell>
        <input
          type="checkbox"
          aria-label={`${t.name} 선택`}
          className="h-4 w-4 align-middle"
          checked={checked}
          onChange={() => onToggle(t.employeeId)}
        />
      </TableCell>
      <TableCell className="font-medium">
        {t.name}
        {!t.hasEmail && (
          <span title="메일 주소가 없어 앱 알림만 받습니다" className="ml-1 inline-flex align-middle text-amber-600">
            <MailWarning className="h-4 w-4" />
          </span>
        )}
      </TableCell>
      <TableCell>{t.department ?? "-"}</TableCell>
      <TableCell className="whitespace-nowrap">
        {t.periodEnd}{" "}
        <Badge variant={urgency(t.daysLeft)}>
          {t.timeLeft} 남음 · {t.daysLeft === 0 ? "D-day" : `D-${t.daysLeft}`}
        </Badge>
      </TableCell>
      <TableCell>{formatDays(t.granted)}일</TableCell>
      <TableCell>{formatDays(t.used)}일</TableCell>
      <TableCell>{t.pending > 0 ? `${formatDays(t.pending)}일` : "-"}</TableCell>
      <TableCell>
        <Badge variant="warning">{formatDays(t.remaining)}일</Badge>
      </TableCell>
      <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
        {t.lastNotifiedAt ? (
          <>
            {formatDateTime(t.lastNotifiedAt).slice(0, 10)}
            {t.noticeCount > 1 && ` (${t.noticeCount}회)`}
          </>
        ) : (
          "-"
        )}
      </TableCell>
    </TableRow>
  );
}
