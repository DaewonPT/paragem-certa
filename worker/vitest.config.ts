import path from "node:path";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  const migracoes = await readD1Migrations(path.join(import.meta.dirname, "migrations"));
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        // O D1 de teste é local e vazio; apply-migrations.ts aplica-lhe estas migrações.
        miniflare: { bindings: { TEST_MIGRATIONS: migracoes } },
      }),
    ],
    test: { setupFiles: ["./test/apply-migrations.ts"] },
  };
});
