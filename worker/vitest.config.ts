import { readFile } from "node:fs/promises";
import path from "node:path";
import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-plugin";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  const migracoes = await readD1Migrations(path.join(import.meta.dirname, "migrations"));
  // O workerd dos testes não lê ficheiros: as configurações e o index.ts do Worker público vão como texto, para o
  // admin.test.ts confirmar que o pc-admin não tem pré-visualizações e que o Worker público não o importa.
  // O guia do autor também, para o base.test.ts confirmar que nomeia os segredos que as configurações exigem.
  const texto = (ficheiro: string) => readFile(path.join(import.meta.dirname, ficheiro), "utf8");
  return {
    plugins: [
      cloudflareTest({
        wrangler: { configPath: "./wrangler.jsonc" },
        // O D1 de teste é local e vazio; apply-migrations.ts aplica-lhe estas migrações.
        miniflare: {
          bindings: {
            TEST_MIGRATIONS: migracoes,
            TEST_CONFIG: await texto("wrangler.jsonc"),
            TEST_ADMIN_CONFIG: await texto("wrangler.admin.jsonc"),
            TEST_INDEX_SRC: await texto("src/index.ts"),
            TEST_GUIA: await texto("../docs/servidor.md"),
          },
        },
      }),
    ],
    // Só os testes do Worker: os de ferramentas/ são do Node (`npm run ferramentas`) e não correm no workerd.
    test: { include: ["test/**/*.test.ts"], setupFiles: ["./test/apply-migrations.ts"] },
  };
});
