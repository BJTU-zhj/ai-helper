import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      "/aihelper": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
