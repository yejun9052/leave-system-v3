import type { LucideIcon } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";

/**
 * 숫자 카드(대시보드·리포트 회사 현황): 아이콘, 이름, 큰 숫자, 아래 설명.
 * to 가 있으면 카드 전체가 그 화면으로 가는 링크가 된다.
 */
export default function Kpi({ icon: Icon, label, value, sub, accent, valueClassName, to }: {
  icon: LucideIcon;
  label: string;
  value: string;
  sub: React.ReactNode;
  accent?: boolean;
  valueClassName?: string;
  to?: string;
}) {
  const card = (
    <Card className={cn(accent && "border-primary/40 bg-primary/5",
      to && "h-full transition-colors hover:border-primary/60",
      to && (accent ? "hover:bg-primary/10" : "hover:bg-accent/50"))}>
      <CardContent className="flex items-center gap-4 p-5">
        <div className={cn("flex h-12 w-12 shrink-0 items-center justify-center rounded-xl",
          accent ? "bg-primary/10 text-primary" : "bg-muted text-foreground/70")}>
          <Icon className="h-6 w-6" />
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className={cn("text-2xl font-bold", accent && "text-primary", valueClassName)}>{value}</p>
          <p className="flex items-center truncate text-sm text-muted-foreground">{sub}</p>
        </div>
      </CardContent>
    </Card>
  );
  if (!to) return card;
  return (
    <Link to={to} className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
      {card}
    </Link>
  );
}
