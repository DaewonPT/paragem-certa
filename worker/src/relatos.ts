import config from "../public/dados/config.json";
import { portaoDeEscrita } from "./portao";
import { corpoJson, erro, json } from "./respostas";

const REGRAS = config.comunidade;
const DIA = 24 * 60 * 60_000;
const MAX_RELATOS = 100;

export type Tipo = keyof typeof REGRAS.duracaoMinutos;
export type RelatoPublico = { id: string; tipo: Tipo; paragem: string; linha: string; criadoEm: number; expiraEm: number; confirmacoes: number; confirmado: boolean };

const TIPOS_DE_LINHA = new Set<string>(["acidente", "desvio", "evento", "nao_passou", "vai_cheio"]);
const VOTOS = new Set<string>(["ainda_esta", "ja_nao_esta"]);

/** "CM:060419" ou "TRANSITOUS:<id>", sem caracteres de HTML; os ids do Transitous têm "(", ")", "-" e "_". */
export const FORMATO_PARAGEM = /^(CM|TRANSITOUS):[^\s,<>"'&]{1,120}$/;
/** O nome visível da linha ("736", "15E"), já em maiúsculas. */
export const FORMATO_LINHA = /^[\p{L}\p{N}][\p{L}\p{N} .\-]{0,15}$/u;
/** Só os votos de contas com pelo menos esta idade contam (e o de quem relatou, sempre): trava o "crio duas contas e confirmo já". */
export const IDADE_PARA_CONTAR = 24 * 60 * 60_000;

function duracao(tipo: Tipo): number {
  return REGRAS.duracaoMinutos[tipo] * 60_000;
}

/**
 * Os votos `voto` no relato `r` que contam: o de quem relatou e os das contas com pelo menos `IDADE_PARA_CONTAR`
 * (`limite` é o parâmetro com agora − essa idade). Uma linha por conta e relato, por isso ninguém conta duas vezes.
 */
function votosQueContam(voto: "ainda_esta" | "ja_nao_esta", limite: string): string {
  return `(SELECT COUNT(*) FROM confirmacoes c JOIN contas k ON k.id = c.conta_id
    WHERE c.relato_id = r.id AND c.voto = '${voto}' AND (c.conta_id = r.conta_id OR k.criada_em <= ${limite}))`;
}

const ATIVO_IGUAL = "paragem = ?1 AND tipo = ?2 AND linha = ?3 AND estado = 'ativo'";

/**
 * POST /api/relatos { tipo, paragem, linha? }. Relatar o que já está ativo conta como "Ainda está" dessa conta, se ela ainda
 * não votou nele, e não gasta os 20 por dia. A posição e o IP nunca se leem: os campos que não se conhecem (lat, lon) ficam de fora.
 */
export async function criarRelato(pedido: Request, env: Env): Promise<Response> {
  const quem = await portaoDeEscrita(pedido, env, "comunidade");
  if (quem instanceof Response) return quem;

  const { tipo, paragem, linha } = await corpoJson(pedido);
  if (typeof tipo !== "string" || !Object.hasOwn(REGRAS.duracaoMinutos, tipo)) return erro("pedido_invalido", 400);
  if (typeof paragem !== "string" || !FORMATO_PARAGEM.test(paragem)) return erro("pedido_invalido", 400);
  let chaveLinha = "";
  if (TIPOS_DE_LINHA.has(tipo)) {
    chaveLinha = typeof linha === "string" ? linha.toUpperCase() : "";
    if (!FORMATO_LINHA.test(chaveLinha)) return erro("pedido_invalido", 400);
  } else if (linha != null) {
    return erro("pedido_invalido", 400);
  }

  const agora = Date.now();
  const id = crypto.randomUUID();
  const chave = [paragem, tipo, chaveLinha];
  // Uma só transação: o índice único (um ativo igual de cada vez) e a contagem dentro do INSERT seguram os pedidos em paralelo.
  const resultados = await env.DB.batch([
    // O ativo igual que já expirou dá lugar ao novo.
    env.DB.prepare(`UPDATE relatos SET estado = 'terminado' WHERE ${ATIVO_IGUAL} AND expira_em <= ?4`).bind(...chave, agora),
    env.DB.prepare(
      `INSERT INTO relatos (id, tipo, paragem, linha, conta_id, criado_em, expira_em)
       SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7
       WHERE (SELECT COUNT(*) FROM (SELECT 1 FROM relatos WHERE conta_id = ?5 AND criado_em > ?8 LIMIT ?9)) < ?9
       ON CONFLICT DO NOTHING`,
    ).bind(id, tipo, paragem, chaveLinha, quem.contaId, agora, agora + duracao(tipo as Tipo), agora - DIA, REGRAS.porContaPorDia.relatos),
    // O "Ainda está" desta conta no ativo igual: no relato novo é o de quem relatou; num que já existia, é um voto, se a conta ainda não tinha votado nele.
    env.DB.prepare(
      `INSERT INTO confirmacoes (relato_id, conta_id, voto, em)
       SELECT id, ?4, 'ainda_esta', ?5 FROM relatos WHERE ${ATIVO_IGUAL} LIMIT 1
       ON CONFLICT (relato_id, conta_id) DO NOTHING`,
    ).bind(...chave, quem.contaId, agora),
    // Renova só com o primeiro voto (changes() é o do INSERT acima) de outra conta que já conta.
    env.DB.prepare(
      `UPDATE relatos SET expira_em = ?6 WHERE ${ATIVO_IGUAL} AND conta_id <> ?4 AND changes() > 0
       AND EXISTS (SELECT 1 FROM contas WHERE id = ?4 AND criada_em <= ?5)`,
    ).bind(...chave, quem.contaId, agora - IDADE_PARA_CONTAR, agora + duracao(tipo as Tipo)),
    env.DB.prepare(`SELECT id FROM relatos WHERE ${ATIVO_IGUAL} LIMIT 1`).bind(...chave),
  ]);

  if (resultados[1].meta.changes === 1) return json({ id, novo: true }, 201);
  const existente = resultados[4].results[0] as { id: string } | undefined;
  // Sem linha nova e sem ativo igual: foi a contagem dos 20 por dia que travou.
  if (!existente) return erro("muitos_pedidos", 429);
  return json({ id: existente.id, novo: false });
}

/** POST /api/votos { relato, voto }: uma linha por conta e relato; só muda de "Ainda está" para "Já não está", e repetir não escreve nada. */
// Limite: cada conta ainda pode votar uma vez em cada relato ativo (umas 8 linhas escritas por relato); juntar um contador "votos:<conta>" em contadores, como em favoritos.ts, se os relatos ativos chegarem às centenas.
export async function votar(pedido: Request, env: Env): Promise<Response> {
  const quem = await portaoDeEscrita(pedido, env, "comunidade");
  if (quem instanceof Response) return quem;

  const { relato, voto } = await corpoJson(pedido);
  if (typeof relato !== "string" || relato.length === 0 || relato.length > 64) return erro("pedido_invalido", 400);
  if (typeof voto !== "string" || !VOTOS.has(voto)) return erro("pedido_invalido", 400);

  const agora = Date.now();
  const ativo = await env.DB.prepare("SELECT tipo FROM relatos WHERE id = ? AND estado = 'ativo' AND expira_em > ? LIMIT 1")
    .bind(relato, agora)
    .first<{ tipo: Tipo }>();
  if (!ativo) return erro("nao_existe", 404);

  // Cada passo volta a exigir o relato ativo: pode ter acabado entre a leitura acima e o lote.
  const ATIVO = "r.id = ?1 AND r.estado = 'ativo' AND r.expira_em > ?2";
  const idade = agora - IDADE_PARA_CONTAR;
  const resultados = await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO confirmacoes (relato_id, conta_id, voto, em)
       SELECT r.id, ?3, ?4, ?2 FROM relatos r WHERE ${ATIVO}
       ON CONFLICT (relato_id, conta_id) DO UPDATE SET voto = excluded.voto, em = excluded.em
       WHERE confirmacoes.voto = 'ainda_esta' AND excluded.voto = 'ja_nao_esta'`,
    ).bind(relato, agora, quem.contaId, voto),
    voto === "ainda_esta"
      ? // changes() é o do voto acima, como em admin/acoes.ts: cada conta renova o relato uma vez.
        env.DB.prepare(
          `UPDATE relatos AS r SET expira_em = ?5 WHERE ${ATIVO} AND r.conta_id <> ?3 AND changes() > 0
           AND EXISTS (SELECT 1 FROM contas WHERE id = ?3 AND criada_em <= ?4)`,
        ).bind(relato, agora, quem.contaId, idade, agora + duracao(ativo.tipo))
      : env.DB.prepare(
          `UPDATE relatos AS r SET estado = 'terminado', expira_em = ?2 WHERE ${ATIVO} AND ${votosQueContam("ja_nao_esta", "?3")} >= ?4`,
        ).bind(relato, agora, idade, REGRAS.desapareceCom),
    env.DB.prepare(`SELECT r.estado, r.expira_em, ${votosQueContam("ainda_esta", "?2")} AS confirmacoes FROM relatos r WHERE r.id = ?1`).bind(relato, idade),
  ]);

  const depois = resultados[2].results[0] as { estado: string; expira_em: number; confirmacoes: number } | undefined;
  // A conta de quem relatou foi apagada entre a leitura e o lote: o relato saiu com ela.
  if (!depois) return erro("nao_existe", 404);
  const aindaAtivo = depois.estado === "ativo" && depois.expira_em > agora;
  // Nada escrito num relato que já acabou: acabou entre a leitura e o lote. Um voto repetido num relato ativo responde 200.
  if (resultados[0].meta.changes + resultados[1].meta.changes === 0 && !aindaAtivo) return erro("nao_existe", 404);
  return json({ confirmacoes: depois.confirmacoes, confirmado: depois.confirmacoes >= REGRAS.confirmadoCom, ativo: aindaAtivo });
}

/**
 * Os relatos ativos das `chaves` (paragens ou linhas), os mais recentes primeiro, no máximo 100. Nada da conta sai.
 * Limite: as contagens dos votos não têm LIMIT (uma linha por conta e relato); limitar se um relato puder juntar milhares de votos.
 */
export async function relatosPublicos(env: Env, por: "paragem" | "linha", chaves: string[], soConfirmados: boolean): Promise<RelatoPublico[]> {
  const agora = Date.now();
  const { results } = await env.DB.prepare(
    `SELECT * FROM (
       SELECT r.id, r.tipo, r.paragem, r.linha, r.criado_em, r.expira_em, ${votosQueContam("ainda_esta", "?3")} AS confirmacoes
       FROM relatos r WHERE r.${por} IN (SELECT value FROM json_each(?1)) AND r.expira_em > ?2 AND r.estado = 'ativo'
     ) WHERE confirmacoes >= ?4 ORDER BY criado_em DESC LIMIT ?5`,
  )
    .bind(JSON.stringify(chaves), agora, agora - IDADE_PARA_CONTAR, soConfirmados ? REGRAS.confirmadoCom : 0, MAX_RELATOS)
    .all<{ id: string; tipo: Tipo; paragem: string; linha: string; criado_em: number; expira_em: number; confirmacoes: number }>();
  return results.map((r) => ({
    id: r.id,
    tipo: r.tipo,
    paragem: r.paragem,
    linha: r.linha,
    criadoEm: r.criado_em,
    expiraEm: r.expira_em,
    confirmacoes: r.confirmacoes,
    confirmado: r.confirmacoes >= REGRAS.confirmadoCom,
  }));
}
