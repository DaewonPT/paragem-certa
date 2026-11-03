// O resto do Env (DB e os segredos) vem do worker-configuration.d.ts, gerado a partir do wrangler.jsonc.
declare namespace Cloudflare {
  interface Env {
    /** Só nos testes: as migrações lidas por vitest.config.ts. */
    TEST_MIGRATIONS: import("cloudflare:test").D1Migration[];
    /** Só nos testes: o texto do wrangler.jsonc, do wrangler.admin.jsonc, do src/index.ts e de docs/servidor.md, lidos por vitest.config.ts. */
    TEST_CONFIG: string;
    TEST_ADMIN_CONFIG: string;
    TEST_INDEX_SRC: string;
    TEST_GUIA: string;
  }
}
