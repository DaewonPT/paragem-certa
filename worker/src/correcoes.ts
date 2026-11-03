import config from "../public/dados/config.json";
import { corpoJson } from "./codigos";
import { portaoDeEscrita } from "./portao";
import { FORMATO_PARAGEM } from "./relatos";
import { erro, json } from "./respostas";

const REGRAS = config.comunidade;
const DIA = 24 * 60 * 60_000;
const MAX_CORRECOES_LIDAS = 100;
const MAX_NOME = 80;

// Continente, Açores e Madeira.
const LAT = { min: 32, max: 42.2 };
const LON = { min: -31.5, max: -6.1 };

export type CorrecaoPublica = { paragem: string; tipo: string; lat?: number; lon?: number; nome?: string };

function dentro(valor: unknown, { min, max }: { min: number; max: number }): valor is number {
  return typeof valor === "number" && valor >= min && valor <= max;
}

/**
 * POST /api/correcoes { paragem, tipo: "posicao", lat, lon } | { paragem, tipo: "nome", nome } | { paragem, tipo: "ja_nao_existe" }.
 * Fica pendente até o autor decidir; só então a leitura a mostra. Os campos que o tipo não usa são ignorados.
 */
export async function criarCorrecao(pedido: Request, env: Env): Promise<Response> {
  const quem = await portaoDeEscrita(pedido, env, "correcoes");
  if (quem instanceof Response) return quem;

  const { paragem, tipo, lat, lon, nome } = await corpoJson(pedido);
  if (typeof paragem !== "string" || !FORMATO_PARAGEM.test(paragem)) return erro("pedido_invalido", 400);

  let valores: [number | null, number | null, string | null];
  if (tipo === "posicao") {
    if (!dentro(lat, LAT) || !dentro(lon, LON)) return erro("pedido_invalido", 400);
    valores = [lat, lon, null];
  } else if (tipo === "nome") {
    // O comprimento em UTF-16 limita o trabalho antes de contar os caracteres de verdade (um emoji ocupa dois).
    const cortado = typeof nome === "string" ? nome.trim() : "";
    if (cortado.length === 0 || cortado.length > 2 * MAX_NOME || [...cortado].length > MAX_NOME) return erro("pedido_invalido", 400);
    valores = [null, null, cortado];
  } else if (tipo === "ja_nao_existe") {
    valores = [null, null, null];
  } else {
    return erro("pedido_invalido", 400);
  }

  const agora = Date.now();
  const id = crypto.randomUUID();
  // A contagem dentro do INSERT: pedidos em paralelo não passam do limite.
  const inserida = await env.DB.prepare(
    `INSERT INTO correcoes (id, paragem, conta_id, tipo, lat, lon, nome, criada_em)
     SELECT ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8
     WHERE (SELECT COUNT(*) FROM (SELECT 1 FROM correcoes WHERE conta_id = ?3 AND criada_em > ?9 LIMIT ?10)) < ?10`,
  )
    .bind(id, paragem, quem.contaId, tipo, ...valores, agora, agora - DIA, REGRAS.porContaPorDia.correcoes)
    .run();
  if (inserida.meta.changes === 0) return erro("muitos_pedidos", 429);
  return json({ id }, 201);
}

/**
 * As correções aprovadas das `paragens`: a mais recente de cada tipo por paragem (a decisão mais tardia; no mesmo
 * instante, a de id maior). Nada da conta sai.
 * shortcut: lê todas as aprovadas dessas paragens, também as já substituídas; só o autor as junta, por isso são poucas; apagar as substituídas se crescerem.
 */
export async function correcoesDaComunidade(env: Env, paragens: string[]): Promise<CorrecaoPublica[]> {
  const { results } = await env.DB.prepare(
    `SELECT c.paragem, c.tipo, c.lat, c.lon, c.nome FROM correcoes c
     WHERE c.paragem IN (SELECT value FROM json_each(?1)) AND c.estado = 'aprovada'
       AND NOT EXISTS (
         SELECT 1 FROM correcoes o WHERE o.paragem = c.paragem AND o.estado = 'aprovada' AND o.tipo = c.tipo
           AND (o.decidida_em > c.decidida_em OR (o.decidida_em = c.decidida_em AND o.id > c.id)))
     LIMIT ?2`,
  )
    .bind(JSON.stringify(paragens), MAX_CORRECOES_LIDAS)
    .all<{ paragem: string; tipo: string; lat: number | null; lon: number | null; nome: string | null }>();
  return results.map((c) => ({
    paragem: c.paragem,
    tipo: c.tipo,
    ...(c.tipo === "posicao" && c.lat !== null && c.lon !== null ? { lat: c.lat, lon: c.lon } : {}),
    ...(c.tipo === "nome" && c.nome !== null ? { nome: c.nome } : {}),
  }));
}
