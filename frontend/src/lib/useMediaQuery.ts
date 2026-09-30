import { useEffect, useState } from "react";

/** CSS 미디어쿼리 일치 여부(창 크기가 바뀌면 다시 계산). 예: useMediaQuery("(max-width: 639px)") */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);

  useEffect(() => {
    const mql = window.matchMedia(query);
    const onChange = () => setMatches(mql.matches);
    onChange();
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}
