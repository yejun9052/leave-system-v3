import { useState } from "react";
import { Download, FileSpreadsheet } from "lucide-react";
import { reportApi } from "@/api/dashboard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useToast } from "@/components/ui/toast";

export default function ReportsPage() {
  const { toast } = useToast();
  const [year, setYear] = useState(new Date().getFullYear());
  const [loading, setLoading] = useState(false);

  const download = async () => {
    setLoading(true);
    try {
      const res = await fetch(reportApi.usageExportUrl(year), { credentials: "same-origin" });
      if (!res.ok) throw new Error("다운로드 실패");
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `leave-usage-${year}.xlsx`;
      a.click();
      URL.revokeObjectURL(url);
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

      <Card className="max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <FileSpreadsheet className="h-5 w-5 text-primary" /> 연차 사용 현황
          </CardTitle>
          <CardDescription>
            선택한 연도의 연차현황표(부서별 직원, 부여·사용·남은 연차, 사용한 날짜)를 엑셀로 출력합니다.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label>연도</Label>
            <Input
              type="number"
              value={year}
              onChange={(e) => setYear(Number(e.target.value))}
              className="max-w-[140px]"
            />
          </div>
          <Button onClick={download} disabled={loading}>
            <Download className="h-4 w-4" /> 엑셀 다운로드
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
