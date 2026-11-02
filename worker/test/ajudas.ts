import { createExecutionContext, createScheduledController, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import worker from "../src/index";

const servidor: Required<Pick<ExportedHandler<Env>, "fetch" | "scheduled">> = worker;

interface Opcoes {
  /** POST por omissão; um objeto vai como JSON, um texto vai como está (para testar JSON estragado). */
  metodo?: string;
  corpo?: unknown;
  token?: string;
  /** Sobrepõe os segredos do ambiente de teste. */
  segredos?: Partial<Env>;
}

/** Chama o Worker como a Cloudflare chama. Sem RESEND_API_KEY, para os testes nunca falarem com a internet. */
export async function pedir(caminho: string, opcoes: Opcoes = {}): Promise<Response> {
  const cabecalhos = new Headers();
  if (opcoes.token) cabecalhos.set("authorization", `Bearer ${opcoes.token}`);
  const corpo = typeof opcoes.corpo === "string" ? opcoes.corpo : JSON.stringify(opcoes.corpo);
  const ctx = createExecutionContext();
  const resposta = await servidor.fetch(
    new Request(`https://exemplo.test${caminho}`, {
      method: opcoes.metodo ?? "POST",
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : corpo,
    }),
    { ...env, RESEND_API_KEY: undefined, ...opcoes.segredos } as Env,
    ctx,
  );
  await waitOnExecutionContext(ctx);
  return resposta;
}

/** Corre o Cron Trigger diário. */
export async function correrCron(): Promise<void> {
  const ctx = createExecutionContext();
  await servidor.scheduled(createScheduledController({ cron: "0 3 * * *" }), env, ctx);
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
