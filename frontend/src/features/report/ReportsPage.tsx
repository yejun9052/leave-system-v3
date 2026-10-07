import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, FileSpreadsheet, Search, X } from "lucide-react";
import { reportApi } from "@/api/dashboard";
import { departmentApi } from "@/api/departments";
import { employeeApi } from "@/api/employees";
import DepartmentTreeSelect from "@/components/DepartmentTreeSelect";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";

/** 직원 목록은 한 번에 받아 화면에서 검색한다(리포트 대상 고르기용, 퇴사자 포함). */
const ALL_EMPLOYEES = 1000;

export default function ReportsPage() {
  const { toast } = useToast();
  const [year, setYear] = useState(new Date().getFullYear());
  const [loading, setLoading] = useState(false);
  const [departmentIds, setDepartmentIds] = useState<Set<number>>(new Set());
  const [employeeIds, setEmployeeIds] = useState<Set<number>>(new Set());
  const [keyword, setKeyword] = useState("");

  const { data: tree = [] } = useQuery({ queryKey: ["departments", "tree"], queryFn: departmentApi.tree });
  const { data: employeePage } = useQuery({
    queryKey: ["employees", "report-picker"],
    queryFn: () => employeeApi.search({ size: ALL_EMPLOYEES }),
  });
  // 리포트는 관리 전용 계정을 빼고 출력하므로 고르는 목록에서도 뺀다
  const employees = useMemo(
    () => (employeePage?.content ?? []).filter((e) => !e.systemAccount),
    [employeePage],
  );
  const visible = useMemo(() => {
    const words = keyword.trim().toLowerCase().split(/\s+/).filter(Boolean);
    if (words.length === 0) return employees;
    return employees.filter((e) => {
      const text = `${e.name} ${e.departmentName ?? ""} ${e.position ?? ""}`.toLowerCase();
      return words.every((w) => text.includes(w));
    });
  }, [employees, keyword]);
  const chosen = employees.filter((e) => employeeIds.has(e.id));

  const toggleEmployee = (id: number) => {
    setEmployeeIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const nothingChosen = departmentIds.size === 0 && employeeIds.size === 0;

  const download = async () => {
    setLoading(true);
    try {
      const url = reportApi.usageExportUrl(year, [...departmentIds], [...employeeIds]);
      const res = await fetch(url, { credentials: "same-origin" });
      if (!res.ok) throw new Error("다운로드 실패");
      const blob = await res.blob();
      const href = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = href;
      a.download = `leave-usage-${year}.xlsx`;
      a.click();
      URL.revokeObjectURL(href);
    } catch {
      toast({ title: "리포트를 내려받지 못했습니다.", variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">리포트</h1>
        <p className="text-sm text-muted-foreground">연차 사용 현황을 엑셀로 내려받습니다.</p>
      </div>

      <Card className="max-w-5xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileSpreadsheet className="h-5 w-5 text-primary" /> 연차 사용 현황
          </CardTitle>
          <CardDescription>
            선택한 연도의 연차현황표(부서별 직원, 부여·사용·남은 연차, 사용한 날짜)를 엑셀로 출력합니다. 부서나
            사용자를 고르면 그 대상만, 아무것도 고르지 않으면 전체를 출력합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <Label>연도</Label>
            <Input
              type="number"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="max-w-[140px]"
            />
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <section className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>부서</Label>
                {departmentIds.size > 0 && (
                  <Button variant="ghost" size="sm" className="h-7" onClick={() => setDepartmentIds(new Set())}>
                    선택 해제
                  </Button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                상위 부서를 체크하면 하위 부서도 함께 체크됩니다. 하위 부서는 따로 해제할 수 있습니다.
              </p>
              <div className="max-h-80 overflow-y-auto rounded-md border p-2">
                <DepartmentTreeSelect tree={tree} selected={departmentIds} onChange={setDepartmentIds} />
              </div>
            </section>

            <section className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>사용자</Label>
                {employeeIds.size > 0 && (
                  <Button variant="ghost" size="sm" className="h-7" onClick={() => setEmployeeIds(new Set())}>
                    선택 해제
                  </Button>
                )}
              </div>
              <div className="relative">
                <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
                  placeholder="이름 · 부서 · 직급으로 찾기"
                  className="pl-8"
                />
              </div>
              <ul className="max-h-[17rem] divide-y overflow-y-auto rounded-md border">
                {visible.length === 0 ? (
                  <li className="px-3 py-6 text-center text-sm text-muted-foreground">맞는 사용자가 없습니다.</li>
                ) : (
                  visible.map((e) => (
                    <li key={e.id}>
                      <label className="flex cursor-pointer items-center gap-2 px-3 py-1.5 text-sm hover:bg-accent">
                        <input
                          type="checkbox"
                          aria-label={`${e.name} 선택`}
                          className="h-4 w-4"
                          checked={employeeIds.has(e.id)}
                          onChange={() => toggleEmployee(e.id)}
                        />
                        <span className="font-medium">{e.name}</span>
                        <span className="truncate text-xs text-muted-foreground">{e.departmentName ?? "부서 없음"}</span>
                        {e.status === "RESIGNED" && (
                          <Badge variant="secondary" className="ml-auto px-1.5 py-0">
                            퇴사
                          </Badge>
                        )}
                      </label>
                    </li>
                  ))
                )}
              </ul>
              {chosen.length > 0 && (
                <div className="flex flex-wrap gap-1.5">
                  {chosen.map((e) => (
                    <Badge key={e.id} variant="default" className="gap-1 pr-1">
                      {e.name}
                      <button
                        type="button"
                        aria-label={`${e.name} 선택 해제`}
                        className="rounded hover:bg-primary/20"
                        onClick={() => toggleEmployee(e.id)}
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </Badge>
                  ))}
                </div>
              )}
            </section>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={download} disabled={loading}>
              <Download className="h-4 w-4" /> 엑셀 다운로드
            </Button>
            <span className="text-sm text-muted-foreground">
              {nothingChosen
                ? "전체 직원을 출력합니다."
                : `부서 ${departmentIds.size}곳 · 사용자 ${employeeIds.size}명을 출력합니다.`}
            </span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
