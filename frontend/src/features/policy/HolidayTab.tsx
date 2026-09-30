import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { holidayApi, type HolidaySyncResult } from "@/api/holidays";
import { extractErrorMessage } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";
import { useConfirm } from "@/components/ui/confirm";

const WEEKDAY = ["일", "월", "화", "수", "목", "금", "토"];

/** 공휴일 조회·동기화(관리자). 동기화는 매일 00:10 자동으로도 실행된다. */
export default function HolidayTab() {
  const { toast } = useToast();
  const confirm = useConfirm();
  const qc = useQueryClient();
  const [year, setYear] = useState(new Date().getFullYear());
  const [result, setResult] = useState<HolidaySyncResult | null>(null);

  const { data: holidays = [], isLoading } = useQuery({
    queryKey: ["holidays", year],
    queryFn: () => holidayApi.list(year),
    enabled: year >= 2000 && year <= 2100,
  });

  const sync = useMutation({
    mutationFn: () => holidayApi.sync(year),
    onSuccess: (r) => {
      setResult(r);
      qc.invalidateQueries({ queryKey: ["holidays", year] });
      toast({ title: `${r.year}년 공휴일 동기화 완료`, variant: "success" });
    },
    onError: (e) => toast({ title: extractErrorMessage(e), variant: "destructive" }),
  });

  return (
    <Card className="max-w-3xl">
      <CardHeader>
        <CardTitle className="text-base">공휴일</CardTitle>
        <CardDescription>
          공공데이터포털 특일 정보 API 와 매일 00:10 에 자동 동기화됩니다. 새 공휴일이 이미 신청된 휴가 기간에
          걸리면 차감 일수를 다시 계산해 잔액을 돌려주고 직원에게 알립니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="space-y-1">
            <Label className="text-xs">연도</Label>
            <Input
              type="number"
              className="w-28"
              value={year}
              onChange={(e) => {
                setYear(Number(e.target.value));
                setResult(null);
              }}
            />
          </div>
          <Button
            size="sm"
            onClick={async () => {
              const ok = await confirm({
                title: `${year}년 공휴일을 동기화할까요?`,
                description: "새 공휴일이 걸친 신청 휴가는 자동 조정·환원됩니다.",
                confirmText: "동기화",
              });
              if (ok) sync.mutate();
            }}
            disabled={sync.isPending}
          >
            <RefreshCw className={sync.isPending ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
            {year}년 동기화
          </Button>
        </div>

        {result && (
          <div className="rounded-lg border bg-muted/40 p-3 text-sm space-y-1">
            <p className="font-medium">{result.year}년 동기화 결과</p>
            <p>
              추가된 공휴일 {result.added.length}건
              {result.added.length > 0 && ` (${result.added.map((h) => `${h.date} ${h.name}`).join(", ")})`}
            </p>
            {result.renamed.length > 0 && <p>이름 변경 {result.renamed.length}건</p>}
            <p>
              조정된 휴가 신청 {result.adjustedRequests}건 · 환원 {result.restoredDays}일
            </p>
            {result.missingInApi.length > 0 && (
              <p className="text-muted-foreground">
                API 응답에 없는 기존 날짜 {result.missingInApi.length}건(삭제하지 않음):{" "}
                {result.missingInApi.map((h) => `${h.date} ${h.name}`).join(", ")}
              </p>
            )}
          </div>
        )}

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-40">날짜</TableHead>
              <TableHead>이름</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {holidays.length > 0 ? (
              holidays.map((h) => (
                <TableRow key={h.date}>
                  <TableCell>
                    {h.date} ({WEEKDAY[new Date(`${h.date}T00:00:00`).getDay()]})
                  </TableCell>
                  <TableCell className="font-medium">{h.name}</TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow>
                <TableCell colSpan={2} className="text-center text-muted-foreground">
                  {isLoading ? "불러오는 중..." : "등록된 공휴일이 없습니다. 동기화를 실행하세요."}
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
