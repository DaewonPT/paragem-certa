import { correcoesDaComunidade } from "./correcoes";
import { fotosDaComunidade } from "./fotos";
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

/** O estado de uma linha do Metro que não está normal; o `id` é "metro:VD:<8 hex de sha256(texto)>", o mesmo enquanto o texto não mudar. */
export interface PerturbacaoPublica {
  id: string;
  linha: string;
  texto: string;
}

/**
 * GET /api/comunidade?paragens=K1,K2 (1–100): o ecrã da paragem e o mapa. Sem sessão, sem cache no servidor e sem
 * limite por IP. Cada parte desligada vem vazia; com as três desligadas, 403.
 * Com `linhas=L1,L2` (1–20) em vez de `paragens`: os relatos ativos dessas linhas, confirmados e por confirmar (o ecrã da
 * linha); só a `comunidade` o liga, 403 sem ela.
 */
export async function lerComunidade(pedido: Request, env: Env): Promise<Response> {
  const ligado = interruptores(env);
  const procura = new URL(pedido.url).searchParams;
  if (procura.has("linhas")) {
    if (!ligado.comunidade) return erro("desligado", 403);
    const linhas = lista(procura.get("linhas")?.toUpperCase() ?? null, MAX_LINHAS, FORMATO_LINHA);
    if (!linhas) return erro("pedido_invalido", 400);
    return json({ relatos: await relatosPublicos(env, "linha", linhas, false) });
  }
  if (!ligado.comunidade && !ligado.fotos && !ligado.correcoes) return erro("desligado", 403);
  const paragens = lista(procura.get("paragens"), MAX_PARAGENS, FORMATO_PARAGEM);
  if (!paragens) return erro("pedido_invalido", 400);

  const relatos = ligado.comunidade ? await relatosPublicos(env, "paragem", paragens, false) : [];
  const { fotos, emRevisao } = ligado.fotos ? await fotosDaComunidade(env, paragens) : { fotos: [], emRevisao: [] };
  const correcoes = ligado.correcoes ? await correcoesDaComunidade(env, paragens) : [];
  return json({ relatos, fotos, emRevisao, correcoes });
}

/**
 * GET /api/avisos?linhas=L1,L2 (1–20), sempre 200 `{ relatos, perturbacoes }` (cartões das rotas, viagem, "Avisa-me"): os relatos
 * confirmados e ativos dessas linhas (`[]` sem `comunidade`) e as perturbações oficiais do Metro.
 */
export async function lerAvisos(pedido: Request, env: Env): Promise<Response> {
  const linhas = lista(new URL(pedido.url).searchParams.get("linhas")?.toUpperCase() ?? null, MAX_LINHAS, FORMATO_LINHA);
  if (!linhas) return erro("pedido_invalido", 400);
  const relatos = interruptores(env).comunidade ? await relatosPublicos(env, "linha", linhas, true) : [];
  // Limite: sem perturbações do Metro, o Metro está fora da 0.4 (a porta 8243 dá 526); voltam quando o Metro mandar a cadeia completa.
  const perturbacoes: PerturbacaoPublica[] = [];
  return json({ relatos, perturbacoes });
}
