import { createExecutionContext, waitOnExecutionContext } from "cloudflare:test";
import { env } from "cloudflare:workers";
import { exportJWK, generateKeyPair, SignJWT, type CryptoKey } from "jose";
import admin from "../src/admin/index";
import { CHAVE_MARCA_TESTE } from "./ajudas";

const paginaDeRevisao: Required<Pick<ExportedHandler<Env>, "fetch">> = admin;

/** Valores falsos, só dos testes. */
export const AUTOR = "autor@exemplo.pt";
export const EQUIPA = "https://equipa-de-teste.cloudflareaccess.com";
export const AUDIENCIA = "audiencia-de-teste";
/** A origem dos pedidos ao pc-admin nos testes. */
export const ORIGEM_ADMIN = "https://admin.test";

let chaves: Promise<{ privada: CryptoKey; jwks: string }> | undefined;

/** O par RS256 com que os testes assinam, como o Access assina os seus; o JWKS público vai em TESTE_ACCESS_JWKS. */
function chavesDoAccess(): Promise<{ privada: CryptoKey; jwks: string }> {
  chaves ??= (async () => {
    const par = await generateKeyPair("RS256");
    const { kty, n, e } = await exportJWK(par.publicKey);
    return { privada: par.privateKey, jwks: JSON.stringify({ keys: [{ kty, n, e, kid: "access", use: "sig" }] }) };
  })();
  return chaves;
}

/** Os segredos de um pc-admin bem configurado, com as chaves de teste no sítio das do Access. */
export async function segredosAdmin(): Promise<Partial<Env>> {
  return {
    AMBIENTE: "teste",
    TESTE_ACCESS_JWKS: (await chavesDoAccess()).jwks,
    ACCESS_EQUIPA: EQUIPA,
    ACCESS_AUD: AUDIENCIA,
    EMAIL_AUTOR: AUTOR,
    CHAVE_MARCA_EMAIL: CHAVE_MARCA_TESTE,
  };
}

/** Um token como os do Access para o autor; em `campos`, `undefined` tira o campo; `chave` assina com outra chave. */
export async function tokenAccess(campos: Record<string, unknown> = {}, chave?: CryptoKey): Promise<string> {
  const agora = Math.floor(Date.now() / 1000);
  const todos: Record<string, unknown> = {
    iss: EQUIPA,
    aud: [AUDIENCIA],
    sub: "id-do-autor-no-access",
    email: AUTOR,
    type: "app",
    iat: agora,
    nbf: agora,
    exp: agora + 3600,
    ...campos,
  };
  const corpo = Object.fromEntries(Object.entries(todos).filter(([, valor]) => valor !== undefined));
  return new SignJWT(corpo).setProtectedHeader({ alg: "RS256", kid: "access", typ: "JWT" }).sign(chave ?? (await chavesDoAccess()).privada);
}

interface OpcoesAdmin {
  /** O Cf-Access-Jwt-Assertion; por omissão um token válido do autor, `null` para não mandar nenhum. */
  jwt?: string | null;
  /** GET por omissão. */
  metodo?: string;
  /** Vai como formulário (application/x-www-form-urlencoded). */
  corpo?: Record<string, string>;
  /** O cabeçalho Origin; por omissão a origem do pc-admin nos POST, `null` para não mandar nenhum. */
  origem?: string | null;
  /** Sobrepõe os segredos de `segredosAdmin`. */
  segredos?: Partial<Env>;
  cabecalhos?: Record<string, string>;
}

/** Chama o pc-admin como o Access o chamaria. Sem RESEND_API_KEY, para os testes nunca falarem com a internet. */
export async function pedirAdmin(caminho: string, opcoes: OpcoesAdmin = {}): Promise<Response> {
  const metodo = opcoes.metodo ?? "GET";
  const cabecalhos = new Headers(opcoes.cabecalhos);
  const jwt = opcoes.jwt === undefined ? await tokenAccess() : opcoes.jwt;
  if (jwt !== null) cabecalhos.set("cf-access-jwt-assertion", jwt);
  const origem = opcoes.origem === undefined ? (metodo === "GET" ? null : ORIGEM_ADMIN) : opcoes.origem;
  if (origem !== null) cabecalhos.set("origin", origem);
  const ctx = createExecutionContext();
  const resposta = await paginaDeRevisao.fetch(
    new Request(`${ORIGEM_ADMIN}${caminho}`, {
      method: metodo,
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : new URLSearchParams(opcoes.corpo),
    }),
    { ...env, RESEND_API_KEY: undefined, ...(await segredosAdmin()), ...opcoes.segredos } as Env,
    ctx,
  );
  await waitOnExecutionContext(ctx);
  return resposta;
}
