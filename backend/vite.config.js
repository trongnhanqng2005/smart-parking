import { resolve } from "node:path";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  appType: "custom",
  plugins: [tailwindcss()],
  input: {
    app: resolve(import.meta.dirname, "src/main/resources/web/js/shared/app.js"),
    "theme-bootstrap": resolve(
      import.meta.dirname,
      "src/main/resources/web/js/shared/theme-bootstrap.js",
    ),
    login: resolve(import.meta.dirname, "src/main/resources/web/js/pages/login.js"),
    "account-security": resolve(import.meta.dirname, "src/main/resources/web/js/pages/account-security.js"),
  },
  build: {
    outDir: "target/generated-resources/static",
    emptyOutDir: true,
    rolldownOptions: {
      output: {
        entryFileNames: "assets/[name].js",
        chunkFileNames: "assets/chunks/[name].js",
        assetFileNames: "assets/[name][extname]",
      },
    },
  },
});
