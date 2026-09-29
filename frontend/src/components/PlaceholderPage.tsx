import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold">{title}</h1>
      <Card>
        <CardHeader>
          <CardTitle className="text-base text-muted-foreground">준비 중</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          이 화면은 곧 제공됩니다.
        </CardContent>
      </Card>
    </div>
  );
}
