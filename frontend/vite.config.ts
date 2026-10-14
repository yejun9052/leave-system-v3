import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";
import path from "path";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg"],
      manifest: {
        name: "연차관리 시스템",
        short_name: "연차관리",
        description: "회사 연차/반차 관리 시스템",
        theme_color: "#4f46e5",
        background_color: "#ffffff",
        display: "standalone",
        start_url: "/",
        icons: [
          {
            src: "favicon.svg",
            sizes: "any",
            type: "image/svg+xml",
            purpose: "any maskable",
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  server: {
    port: 5173,
    // 같은 네트워크의 휴대폰에서도 PC 의 IP 주소(예: http://192.168.0.10:5173)로 들어와 테스트할 수 있게
    host: true,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        // 백엔드(로컬)는 http://localhost:5173 출처만 허용한다. IP 주소로 들어온 요청도 막히지 않게 출처를 그 값으로 바꿔 보낸다
        headers: { origin: "http://localhost:5173" },
      },
    },
  },
});
