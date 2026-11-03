import { interruptores } from "./portao";
import { FORMATO_LINHA, FORMATO_PARAGEM, relatosPublicos } from "./relatos";
import { erro, json } from "./respostas";

const MAX_PARAGENS = 100;
const MAX_LINHAS = 20;

/** "K1,K2" → ["K1", "K2"], ou null se vier vazio, com mais de `maximo` chaves ou com alguma fora do `formato`. */
function lista(texto: string | null, maximo: number, formato: RegExp): string[] | null {
  if (!texto) return null;
  const chaves = texto.split(",");
  return chaves.length <= maximo && chaves.every((chave) => formato.test(chave)) ? chaves : null;
}

/**
 * GET /api/comunidade?paragens=K1,K2 (1–100): o ecrã da paragem e o mapa. Sem sessão, sem cache no servidor e sem
 * limite por IP (spec 5.3). Cada parte desligada vem vazia; com as três desligadas, 403.
 */
export async function lerComunidade(pedido: Request, env: Env): Promise<Response> {
  const ligado = interruptores(env);
  if (!ligado.comunidade && !ligado.fotos && !ligado.correcoes) return erro("desligado", 403);
  const paragens = lista(new URL(pedido.url).searchParams.get("paragens"), MAX_PARAGENS, FORMATO_PARAGEM);
  if (!paragens) return erro("pedido_invalido", 400);

  const relatos = ligado.comunidade ? await relatosPublicos(env, "paragem", paragens, false) : [];
  return json({ relatos, fotos: [], emRevisao: [], correcoes: [] });
}

/** GET /api/avisos?linhas=L1,L2 (1–20): só os relatos confirmados e ativos dessas linhas (cartões das rotas, viagem, "Avisa-me"). */
export async function lerAvisos(pedido: Request, env: Env): Promise<Response> {
  if (!interruptores(env).comunidade) return erro("desligado", 403);
  const linhas = lista(new URL(pedido.url).searchParams.get("linhas")?.toUpperCase() ?? null, MAX_LINHAS, FORMATO_LINHA);
  if (!linhas) return erro("pedido_invalido", 400);
  return json({ relatos: await relatosPublicos(env, "linha", linhas, true) });
}
