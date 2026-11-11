import config from "../public/dados/config.json";
import { erro } from "./respostas";
import { contaDaSessao, sha256Hex } from "./sessoes";

declare global {
  interface Env {
    /** Só nos testes: JSON com os interruptores que sobrepõem os do config.json. Só vale com AMBIENTE "teste" e sem RESEND_API_KEY. */
    TESTE_INTERRUPTORES?: string;
  }
}

export type Funcao = "comunidade" | "correcoes";
export type Escritor = { contaId: string; email: string; emailHash: string };

/**
 * Os interruptores do config.json. Os de teste só valem com AMBIENTE "teste" e sem RESEND_API_KEY: o servidor
 * publicado tem sempre a chave do Resend (o wrangler.jsonc exige-a), por isso não há como ligar nada por aí.
 */
export function interruptores(env: Env): {
  conta: boolean;
  comunidade: boolean;
  correcoes: boolean;
  carrisAoVivo: boolean;
  metro: boolean;
} {
  if (env.AMBIENTE === "teste" && !env.RESEND_API_KEY && env.TESTE_INTERRUPTORES) {
    return { ...config.interruptores, ...JSON.parse(env.TESTE_INTERRUPTORES) };
  }
  return config.interruptores;
}

/**
 * O endereço sem o que não muda a caixa de correio: minúsculas, sem o "+etiqueta" e, no Gmail, sem pontos
 * (`A.Na+2@googlemail.com` e `ana@gmail.com` são a mesma pessoa). Fora do Gmail os pontos ficam.
 */
export function normalizarEmail(email: string): string {
  const limpo = email.trim().toLowerCase();
  const arroba = limpo.lastIndexOf("@");
  if (arroba < 0) return limpo;
  let local = limpo.slice(0, arroba).split("+")[0];
  let dominio = limpo.slice(arroba + 1);
  if (dominio === "gmail.com" || dominio === "googlemail.com") {
    local = local.replaceAll(".", "");
    dominio = "gmail.com";
  }
  return `${local}@${dominio}`;
}

/** O `email_hash` dos bloqueios, das denúncias e das decisões: só se faz por aqui, para todos darem a mesma marca. */
export async function marcaDoEmail(email: string): Promise<string> {
  return sha256Hex(normalizarEmail(email));
}

/**
 * Quem faz o pedido: a sessão (401 sem_sessao) e o limite de 10 escritas por minuto e por conta (429 devagar).
 * Não olha para os interruptores, o bloqueio nem os termos: quem está bloqueado ainda pode denunciar.
 */
export async function quemEscreve(pedido: Request, env: Env): Promise<Escritor | Response> {
  const sessao = await contaDaSessao(pedido, env);
  if (!sessao) return erro("sem_sessao", 401);
  const { success } = await env.LIMITADOR.limit({ key: sessao.contaId });
  if (!success) return erro("devagar", 429);
  const conta = await env.DB.prepare("SELECT email FROM contas WHERE id = ?").bind(sessao.contaId).first<{ email: string }>();
  if (!conta) return erro("sem_sessao", 401);
  return { contaId: sessao.contaId, email: conta.email, emailHash: await marcaDoEmail(conta.email) };
}

/**
 * Por aqui passam todas as escritas da comunidade: o interruptor (403 desligado), a sessão e o limite por minuto,
 * o bloqueio (403 conta_bloqueada) e os termos em vigor (403 termos_por_aceitar), por esta ordem.
 */
export async function portaoDeEscrita(pedido: Request, env: Env, funcao: Funcao): Promise<Escritor | Response> {
  if (!interruptores(env)[funcao]) return erro("desligado", 403);
  const quem = await quemEscreve(pedido, env);
  if (quem instanceof Response) return quem;

  const [bloqueio, termos] = await env.DB.batch([
    env.DB.prepare("SELECT 1 FROM bloqueios WHERE email_hash = ? LIMIT 1").bind(quem.emailHash),
    env.DB.prepare("SELECT 1 FROM aceites_termos WHERE conta_id = ? AND versao_termos = ? LIMIT 1").bind(quem.contaId, config.versaoTermos),
  ]);
  if (bloqueio.results.length > 0) return erro("conta_bloqueada", 403);
  if (termos.results.length === 0) return erro("termos_por_aceitar", 403);
  return quem;
}
