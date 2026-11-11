import { createExecutionContext, createScheduledController, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import worker from "../src/index";
import { criarSessao } from "../src/sessoes";

const servidor: Required<Pick<ExportedHandler<Env>, "fetch" | "scheduled">> = worker;

interface Opcoes {
  /** POST por omissão; um objeto vai como JSON, um texto vai como está (para testar JSON estragado), um Uint8Array vai em bruto. */
  metodo?: string;
  corpo?: unknown;
  token?: string;
  /** Sobrepõe os segredos do ambiente de teste. */
  segredos?: Partial<Env>;
  /** Cabeçalhos a mais. */
  cabecalhos?: Record<string, string>;
}

/** Chama o Worker como a Cloudflare chama. Sem RESEND_API_KEY, para os testes nunca falarem com a internet. */
export async function pedir(caminho: string, opcoes: Opcoes = {}): Promise<Response> {
  const cabecalhos = new Headers(opcoes.cabecalhos);
  if (opcoes.token) cabecalhos.set("authorization", `Bearer ${opcoes.token}`);
  const corpo = typeof opcoes.corpo === "string" || opcoes.corpo instanceof Uint8Array ? opcoes.corpo : JSON.stringify(opcoes.corpo);
  const ctx = createExecutionContext();
  const resposta = await servidor.fetch(
    new Request(`https://exemplo.test${caminho}`, {
      method: opcoes.metodo ?? "POST",
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : corpo,
    }),
    { ...env, RESEND_API_KEY: undefined, LIMITE_AUTH: limitador(Infinity), ...opcoes.segredos } as Env,
    ctx,
  );
  await waitOnExecutionContext(ctx);
  return resposta;
}

/** Corre o Cron Trigger diário; `agora` é a hora a que o Cloudflare o teria disparado. */
export async function correrCron(opcoes: { segredos?: Partial<Env>; agora?: number } = {}): Promise<void> {
  const ctx = createExecutionContext();
  await servidor.scheduled(
    createScheduledController({ cron: "0 3 * * *", scheduledTime: opcoes.agora ?? Date.now() }),
    { ...env, RESEND_API_KEY: undefined, LIMITE_AUTH: limitador(Infinity), ...opcoes.segredos } as Env,
    ctx,
  );
  await waitOnExecutionContext(ctx);
}

/** SHA-256 em hexadecimal, escrito à parte do código do servidor para os testes o poderem contrariar. */
export async function sha256(texto: string): Promise<string> {
  const resumo = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(resumo)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Um pedido com o token de sessão, para chamar `contaDaSessao` diretamente. */
export function pedidoComToken(token?: string): Request {
  return new Request("https://exemplo.test/api/x", { headers: token ? { authorization: `Bearer ${token}` } : {} });
}

/** Um limitador que deixa passar `deixa` pedidos e recusa os seguintes; `chaves` regista a chave de todos. */
export function limitador(deixa: number): RateLimit & { chaves: string[] } {
  const chaves: string[] = [];
  return {
    chaves,
    async limit({ key }) {
      chaves.push(key);
      return { success: chaves.length <= deixa };
    },
  };
}

/** Segredos que ligam as funções da comunidade (só valem com AMBIENTE "teste" e sem RESEND_API_KEY) e um limitador que deixa tudo passar. */
export const LIGADO: Partial<Env> = {
  AMBIENTE: "teste",
  TESTE_INTERRUPTORES: JSON.stringify({ comunidade: true, correcoes: true }),
  LIMITADOR: limitador(Infinity),
};

const DIA = 24 * 60 * 60_000;

/**
 * Cria uma conta com os termos aceites e uma sessão, e devolve o token. Por omissão os termos são os "1" e a conta
 * tem 2 dias (já conta nos votos); `email` fica como vem, para os testes poderem trazer maiúsculas e "+etiquetas".
 */
export async function criarConta(id: string, email: string, opcoes: { termos?: string; criadaEm?: number } = {}): Promise<string> {
  const agora = Date.now();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO contas (id, email, criada_em) VALUES (?, ?, ?)").bind(id, email, opcoes.criadaEm ?? agora - 2 * DIA),
    env.DB.prepare("INSERT INTO aceites_termos (conta_id, versao_termos, aceite_em) VALUES (?, ?, ?)").bind(id, opcoes.termos ?? "1", agora),
  ]);
  return criarSessao(env, id);
}
