import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Search, ShieldCheck } from "lucide-react";
import { auditApi, type AuditLog } from "@/api/audit";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const ACTION_LABEL: Record<string, string> = {
  POST: "생성",
  PUT: "수정",
  PATCH: "변경",
  DELETE: "삭제",
  LOGIN: "로그인",
  CALL: "실행",
  // 행위형 엔드포인트
  approve: "승인",
  reject: "반려",
  cancel: "취소 요청",
  cancel_approve: "취소 승인",
  cancel_reject: "취소 반려",
  move: "이동",
  reactivate: "복원",
  password: "비밀번호 변경",
  grant: "연차 부여",
  run: "촉진 발송",
  import: "일괄 등록",
  export: "내보내기",
  "read-all": "알림 읽음",
};

// 삭제·반려성 동작은 붉은 배지로 강조
const NEGATIVE_ACTIONS = new Set(["DELETE", "reject", "cancel", "cancel_reject"]);

const RESOURCE_LABEL: Record<string, string> = {
  auth: "인증",
  employees: "사용자",
  departments: "부서",
  "leave-requests": "휴가 신청",
  "leave-types": "휴가 종류",
  policy: "정책",
  calendar: "캘린더",
  leave: "연차 운영",
  notifications: "알림",
  reports: "리포트",
};

function actionLabel(a: AuditLog): string {
  return ACTION_LABEL[a.action] ?? a.action;
}
function resourceLabel(a: AuditLog): string {
  return RESOURCE_LABEL[a.entityType] ?? a.entityType;
}

export default function AuditLogPage() {
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ["auditLogs", { search, page }],
    queryFn: () => auditApi.list(search, page, 30),
  });

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setSearch(keyword);
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="flex items-center gap-2 text-2xl font-bold">
          <ShieldCheck className="h-6 w-6 text-primary" /> 이벤트 로그
        </h1>
        <p className="text-sm text-muted-foreground">
          시스템 전반의 생성·수정·삭제·로그인 등 모든 활동 기록입니다.
        </p>
      </div>

      <form onSubmit={onSearch} className="flex gap-2">
        <div className="relative max-w-sm flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="사용자·동작·대상·상세 검색"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary">
          검색
        </Button>
      </form>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[170px]">시간</TableHead>
                <TableHead>사용자</TableHead>
                <TableHead>동작</TableHead>
                <TableHead>대상</TableHead>
                <TableHead>결과</TableHead>
                <TableHead>상세</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
                    불러오는 중…
                  </TableCell>
                </TableRow>
              ) : data && data.content.length > 0 ? (
                data.content.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="whitespace-nowrap text-muted-foreground">
                      {new Date(a.createdAt).toLocaleString("ko-KR")}
                    </TableCell>
                    <TableCell className="font-medium">{a.actorName}</TableCell>
                    <TableCell>
                      <Badge variant={NEGATIVE_ACTIONS.has(a.action) ? "destructive" : "secondary"}>
                        {actionLabel(a)}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {resourceLabel(a)}
                      {a.entityId && <span className="text-muted-foreground"> #{a.entityId}</span>}
                    </TableCell>
                    <TableCell>
                      <Badge variant={a.success ? "success" : "destructive"}>
                        {a.success ? "성공" : "실패"}
                      </Badge>
                    </TableCell>
                    <TableCell className="max-w-[320px] truncate text-xs text-muted-foreground" title={a.detail}>
                      {a.detail}
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                    기록이 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </Button>
          <span className="text-sm text-muted-foreground">
            {page + 1} / {data.totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            다음
          </Button>
        </div>
      )}
    </div>
  );
}
