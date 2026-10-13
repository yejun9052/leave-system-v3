import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Download, FileSpreadsheet, Search } from "lucide-react";
import { reportApi } from "@/api/dashboard";
import { downloadFile } from "@/lib/download";
import { isValidYear } from "@/lib/dateFormat";
import { departmentApi } from "@/api/departments";
import { employeeApi } from "@/api/employees";
import DepartmentTreeSelect from "@/components/DepartmentTreeSelect";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";

/** 내려받는 파일 이름의 날짜: 오늘 2026_10_07 */
function todayForFileName(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}_${pad(d.getMonth() + 1)}_${pad(d.getDate())}`;
}

/** 직원 목록은 한 번에 받아 화면에서 검색한다(리포트 대상 고르기용, 퇴사자 포함). */
const ALL_EMPLOYEES = 1000;

export default function ReportsPage() {
  const { toast } = useToast();
  // 칸을 비우고 다시 쓸 수 있게 글자로 두고, 쓸 수 있는 연도일 때만 내려받는다(빈 칸이 0년이 되지 않게)
  const [yearText, setYearText] = useState(String(new Date().getFullYear()));
  const year = Number(yearText);
  const yearValid = isValidYear(yearText);
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

  const toggleEmployee = (id: number) => {
    setEmployeeIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  /**
   * 부서 체크가 바뀌면 그 부서(하위 부서는 트리가 함께 체크·해제한다) 사람들을 사용자 목록에서도 함께 체크·해제한다.
   * 그 뒤 사용자를 하나씩 체크·해제할 수 있다.
   */
  const changeDepartments = (next: Set<number>) => {
    const added = new Set([...next].filter((id) => !departmentIds.has(id)));
    const removed = new Set([...departmentIds].filter((id) => !next.has(id)));
    setEmployeeIds((prev) => {
      const ids = new Set(prev);
      for (const e of employees) {
        if (e.departmentId == null) continue;
        if (added.has(e.departmentId)) ids.add(e.id);
        else if (removed.has(e.departmentId)) ids.delete(e.id);
      }
      return ids;
    });
    setDepartmentIds(next);
  };

  // 부서 체크는 사람을 고르는 도구라, 출력 대상은 체크된 사용자다.
  // (서버는 "고른 부서 소속 또는 고른 사용자"를 출력하므로 부서도 보내면, 따로 해제한 사람이 다시 들어간다)
  const nothingChosen = departmentIds.size === 0 && employeeIds.size === 0;
  // 부서는 골랐는데 체크된 사람이 없으면(빈 부서이거나 모두 해제) 막는다. 빈 목록을 보내면 전체가 출력된다
  const emptyDepartments = departmentIds.size > 0 && employeeIds.size === 0;

  const download = async () => {
    setLoading(true);
    try {
      const url = reportApi.usageExportUrl(year, [], [...employeeIds]);
      await downloadFile(url, `연차현황표_${todayForFileName()}.xlsx`, "리포트를 내려받지 못했습니다.");
    } catch (e) {
      toast({ title: (e as Error).message, variant: "destructive" });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">리포트</h1>
        <p className="text-sm text-muted-foreground">연차현황표를 엑셀로 내려받습니다.</p>
      </div>

      <Card className="max-w-5xl">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileSpreadsheet className="h-5 w-5 text-primary" /> 연차현황표
          </CardTitle>
          <CardDescription>
            선택한 연도의 연차현황표(부서별 직원, 부여·사용·남은 연차, 사용한 날짜)를 엑셀로 출력합니다. 부서나
            사용자를 고르면 그 대상만, 아무것도 고르지 않으면 전체를 출력합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="report-year">연도</Label>
            <Input
              id="report-year"
              type="number"
              value={yearText}
              onChange={(e) => setYearText(e.target.value)}
              aria-invalid={!yearValid}
              className="max-w-[140px]"
            />
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <section className="space-y-2">
              {/* 높이 고정: "선택 해제" 버튼(h-7)이 나타나도 아래 목록이 밀리지 않게 */}
              <div className="flex h-7 items-center justify-between">
                <Label>부서</Label>
                {departmentIds.size > 0 && (
                  <Button variant="ghost" size="sm" className="h-7" onClick={() => changeDepartments(new Set())}>
                    선택 해제
                  </Button>
                )}
              </div>
              <p className="text-xs text-muted-foreground">
                상위 부서를 체크하면 하위 부서도 함께 체크됩니다. 하위 부서는 따로 해제할 수 있습니다. 체크한 부서의
                사용자는 오른쪽 목록에서 함께 체크됩니다.
              </p>
              <div className="max-h-80 overflow-y-auto rounded-md border p-2">
                <DepartmentTreeSelect tree={tree} selected={departmentIds} onChange={changeDepartments} />
              </div>
            </section>

            <section className="space-y-2">
              <div className="flex h-7 items-center justify-between">
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
                  aria-label="사용자를 이름 · 부서 · 직급으로 찾기"
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
            </section>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button onClick={download} disabled={loading || emptyDepartments || !yearValid}>
              <Download className="h-4 w-4" /> 엑셀 다운로드
            </Button>
            <span className="text-sm text-muted-foreground">
              {nothingChosen
                ? "전체 직원을 출력합니다."
                : emptyDepartments
                  ? "출력할 사용자가 없습니다. 사용자를 체크해 주세요."
                  : `사용자 ${employeeIds.size}명을 출력합니다.`}
            </span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
