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
 * Fotos por rever há mais de 30 dias: primeiro os ficheiros, nos dois buckets (uma aprovação a meio já pode ter copiado
 * para o público; apagar uma chave que não existe não faz nada), e só depois as linhas. Se o R2 falhar, as linhas ficam
 * e o Cron do dia seguinte repete. A linha sai pelo id, seja qual for o estado: uma aprovação que acabou entre os dois
 * passos ficava a apontar para um ficheiro que já não existe.
 */
async function apagarFotosPorRever(env: Env, antesDe: number): Promise<number> {
  const { results } = await env.DB.prepare("SELECT id, chave FROM fotos WHERE estado = 'pendente' AND criada_em < ?1 LIMIT ?2")
    .bind(antesDe, LOTE)
    .all<{ id: string; chave: string | null }>();
  const chaves = results.flatMap((foto) => (foto.chave ? [foto.chave] : []));
  if (chaves.length > 0) await Promise.all([env.FOTOS_PENDENTES.delete(chaves), env.FOTOS.delete(chaves)]);
  if (results.length > 0) {
    await env.DB.prepare("DELETE FROM fotos WHERE id IN (SELECT value FROM json_each(?1))")
      .bind(JSON.stringify(results.map((foto) => foto.id)))
      .run();
  }
  return results.length;
}

/**
 * O Cron diário: apaga o que passou os prazos da spec (secção 4.7). `agora` vem do controlador do Cron.
 * Cada prazo é independente: se um falhar (o R2 em baixo, por exemplo), os outros fazem-se na mesma e o erro sobe no fim.
 */
export async function aplicarPrazos(env: Env, agora = Date.now()): Promise<void> {
  const anteontem = new Date(agora - 2 * DIA).toISOString().slice(0, 10);
  // Tabela, condição (com ?1) e o limite. Todas as condições têm índice; as confirmações saem com o relato (ON DELETE CASCADE).
  const prazos: [string, string, number | string][] = [
    ["relatos", "expira_em < ?1", agora - 30 * DIA],
    // A rejeitada já não tem ficheiro; fica 1 dia só para contar no limite de 1 foto por conta e dia.
    ["fotos", "estado = 'rejeitada' AND criada_em < ?1", agora - DIA],
    ["emails_por_enviar", "criado_em < ?1", agora - 30 * DIA],
    ["denuncias", "criada_em < ?1", agora - 365 * DIA],
    ["decisoes", "criada_em < ?1", agora - 365 * DIA],
    ["bloqueios", "criado_em < ?1", agora - 365 * DIA],
    ["contadores", "dia < ?1", anteontem],
    ["envios_email", "dia < ?1", anteontem],
  ];
  const passos = prazos.map(([tabela, condicao, limite]) =>
    emLotes(async () => {
      const apagadas = await env.DB.prepare(`DELETE FROM ${tabela} WHERE rowid IN (SELECT rowid FROM ${tabela} WHERE ${condicao} LIMIT ?2)`)
        .bind(limite, LOTE)
        .run();
      return apagadas.meta.changes;
    }),
  );
  passos.push(emLotes(() => apagarFotosPorRever(env, agora - 30 * DIA)));

  const falha = (await Promise.allSettled(passos)).find((resultado): resultado is PromiseRejectedResult => resultado.status === "rejected");
  if (falha) throw falha.reason;
}
