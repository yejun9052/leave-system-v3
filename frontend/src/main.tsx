import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClientProvider } from "@tanstack/react-query";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import App from "./App";
import { ToastProvider } from "@/components/ui/toast";
import { ConfirmProvider } from "@/components/ui/confirm";
import { queryClient } from "@/lib/queryClient";
import "./index.css";

// 화면 경로는 App 의 <Routes> 가 정한다. 데이터 라우터로 감싸는 것은 저장 안 한 변경이 있을 때 이동을 멈추기 위해서다(useBlocker)
const router = createBrowserRouter([
  {
    path: "*",
    element: (
      <ToastProvider>
        <ConfirmProvider>
          <App />
        </ConfirmProvider>
      </ToastProvider>
    ),
  },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </React.StrictMode>,
);
