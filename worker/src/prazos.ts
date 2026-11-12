const DIA = 24 * 60 * 60_000;
/** Linhas apagadas de cada vez, e quantas vezes se repete num Cron (o resto fica para o dia seguinte). */
const LOTE = 500;
const MAX_VOLTAS = 10;

/** Repete `passo` (que apaga até LOTE linhas e diz quantas apagou) até sobrarem menos de LOTE, no máximo MAX_VOLTAS vezes. */
async function emLotes(passo: () => Promise<number>): Promise<void> {
  for (let volta = 0; volta < MAX_VOLTAS; volta++) {
    if ((await passo()) < LOTE) return;
  }
}

/**
 * O Cron diário: apaga o que passou do prazo (os prazos estão nos termos e na política de privacidade). `agora` vem do controlador do Cron.
 * Cada prazo é independente: se um falhar, os outros fazem-se na mesma e o erro sobe no fim.
 */
export async function aplicarPrazos(env: Env, agora = Date.now()): Promise<void> {
  const anteontem = new Date(agora - 2 * DIA).toISOString().slice(0, 10);
  // Tabela, condição (com ?1) e o limite. Todas as condições têm índice; as confirmações saem com o relato (ON DELETE CASCADE).
  const prazos: [string, string, number | string][] = [
    ["relatos", "expira_em < ?1", agora - 30 * DIA],
    ["emails_por_enviar", "criado_em < ?1", agora - 30 * DIA],
    ["denuncias", "criada_em < ?1", agora - 365 * DIA],
    ["decisoes", "criada_em < ?1", agora - 365 * DIA],
    ["bloqueios", "criado_em < ?1", agora - 365 * DIA],
    ["contadores", "dia < ?1", anteontem],
  ];
  const passos = prazos.map(([tabela, condicao, limite]) =>
    emLotes(async () => {
      const apagadas = await env.DB.prepare(`DELETE FROM ${tabela} WHERE rowid IN (SELECT rowid FROM ${tabela} WHERE ${condicao} LIMIT ?2)`)
        .bind(limite, LOTE)
        .run();
      return apagadas.meta.changes;
    }),
  );

  const falha = (await Promise.allSettled(passos)).find((resultado): resultado is PromiseRejectedResult => resultado.status === "rejected");
  if (falha) throw falha.reason;
}
