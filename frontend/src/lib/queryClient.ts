import { QueryClient } from "@tanstack/react-query";

/** 앱 전체가 함께 쓰는 서버 데이터 캐시. 로그인한 사람이 바뀌면 store/auth 가 비운다. */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
    },
  },
});
