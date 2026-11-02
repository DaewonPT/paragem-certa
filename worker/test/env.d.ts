// O resto do Env (DB e os segredos) vem do worker-configuration.d.ts, gerado a partir do wrangler.jsonc.
declare namespace Cloudflare {
  interface Env {
    /** Só nos testes: as migrações lidas por vitest.config.ts. */
    TEST_MIGRATIONS: import("cloudflare:test").D1Migration[];
  }
}
