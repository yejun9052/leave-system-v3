import { useCallback, useEffect, useRef, useState } from "react";

/**
 * 서버 값을 불러와 고친 뒤 저장하는 화면의 입력 상태(정책, 자동 백업, 자동화 설정 등).
 * 실시간 갱신으로 서버 값이 바뀌었을 때
 * - 고치는 중이 아니면 새 값으로 바꾼다.
 * - 고치는 중이면 입력을 지우지 않고 changedElsewhere = true 로 알린다. reload() 로 새 값을 불러온다.
 * 저장에 성공하면 reset(저장된 값)으로 맞춘다(서버가 값을 다듬어 돌려줘도 남이 바꾼 것으로 보지 않게).
 *
 * @param server 서버 값을 화면 입력 모양으로 바꾼 것. 아직 없으면 undefined
 */
export function useServerForm<T>(server: T | undefined) {
  const [form, setForm] = useState<T | null>(server ?? null);
  const [changedElsewhere, setChangedElsewhere] = useState(false);
  /** 지금 입력의 바탕이 된 서버 값(JSON) */
  const base = useRef<string | null>(server === undefined ? null : JSON.stringify(server));
  const serverJson = server === undefined ? null : JSON.stringify(server);
  const latest = useRef(server);
  latest.current = server;

  useEffect(() => {
    if (serverJson === null || serverJson === base.current) return;
    const editing = form !== null && base.current !== null && JSON.stringify(form) !== base.current;
    if (!editing || JSON.stringify(form) === serverJson) {
      base.current = serverJson;
      setForm(latest.current ?? null);
      setChangedElsewhere(false);
    } else {
      setChangedElsewhere(true);
    }
    // form 은 일부러 빼 둔다: 서버 값이 바뀔 때만 비교한다
  }, [serverJson]);

  /** 남이 바꾼 새 값으로 다시 불러오기(내 입력은 버림) */
  const reload = useCallback(() => {
    const value = latest.current;
    if (value === undefined) return;
    base.current = JSON.stringify(value);
    setForm(value);
    setChangedElsewhere(false);
  }, []);

  /** 저장 성공: 저장된 값을 새 바탕으로 */
  const reset = useCallback((value: T) => {
    base.current = JSON.stringify(value);
    setForm(value);
    setChangedElsewhere(false);
  }, []);

  return { form, setForm, changedElsewhere, reload, reset };
}
