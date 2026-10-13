import { loginUrlReturningHere, type ApiEnvelope } from "@/api/client";
import { useMaintenanceStore } from "@/store/maintenance";
import { useUnsavedChangesStore } from "@/store/unsavedChanges";

/**
 * 파일 내려받기(엑셀·백업). 서비스 워커가 /api 로 가는 페이지 이동을 가로채지 않도록 링크 대신 fetch 로 받아 저장한다.
 * axios 를 거치지 않으므로 api/client 와 같게 처리한다:
 * - 오류 응답이면 서버 문구로 Error 를 던진다(오류 JSON 을 .xlsx 로 저장하지 않게).
 * - 세션이 끊겼으면(401) 로그인 화면으로, 점검 중(503 MAINTENANCE)이면 점검 안내를 띄운다.
 *
 * @throws Error 사용자에게 보여 줄 문구
 */
export async function downloadFile(url: string, fileName: string, failMessage: string): Promise<void> {
  const res = await fetch(url, { credentials: "same-origin" });
  if (!res.ok) {
    let body: ApiEnvelope<unknown> | undefined;
    try {
      body = (await res.json()) as ApiEnvelope<unknown>;
    } catch {
      // JSON 이 아닌 오류(프록시 등)
    }
    const code = body?.error?.code;
    if (res.status === 503 && code === "MAINTENANCE") useMaintenanceStore.getState().setActive(true);
    if (res.status === 401 && (!code || code === "UNAUTHORIZED")) {
      useUnsavedChangesStore.getState().discardAll();
      window.location.href = loginUrlReturningHere();
    }
    throw new Error(body?.error?.message ?? failMessage);
  }
  const href = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = href;
  a.download = fileName;
  // 일부 브라우저는 문서에 붙인 링크만 내려받고, 바로 해제하면 받기 전에 사라진다
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(href), 10_000);
}
