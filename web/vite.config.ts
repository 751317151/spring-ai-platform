import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    port: 4000,
    proxy: {
      "/api/v1/auth": {
        target: "http://localhost:8083",
        changeOrigin: true,
      },
      "/api/v1/agent": {
        target: "http://localhost:8082",
        changeOrigin: true,
      },
      "/api/v1/rag": {
        target: "http://localhost:8081",
        changeOrigin: true,
      },
      "/api/v1/monitor": {
        target: "http://localhost:8084",
        changeOrigin: true,
      },
      "/api/v1/chat": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
